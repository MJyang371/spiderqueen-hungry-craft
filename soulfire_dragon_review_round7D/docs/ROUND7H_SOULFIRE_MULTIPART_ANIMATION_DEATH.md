# ROUND 7H — 骨龙颈部转向、待机、撕咬动画、多段命中箱、死亡→残留链路复核与蓝眼最终裁决

> 承接 ROUND 7G。本轮按授权做五件事：**1) 颈部/头部转向 2) 待机动画 3) 真正的撕咬动画 4) 多段命中箱（参考包 stage 2 落地） 5) 死亡→残留回归链路复核**；并对**蓝眼**给出最终裁决。
> 所有验证均以"游戏真的跑过"为准：GameTest 批（`soulfire_dragon` 19 项 + 新增 `soulfire_multipart` 6 项）与实机探针（帧 + 日志）双份证据。

---

## 1. 结论速览

| # | 事项 | 状态 | 客观证据 |
|---|---|---|---|
| 1 | 颈部/头部转向 | **已实现** | 服务端 `DATA_HEAD_YAW/PITCH` 同步头转（`getYHeadRot - yBodyRot`）；实机探针记录同步 yaw **48°**，动画 `applyLook` 用该值弯 Neck1/2/3+Head（上限 45°/25°，`FOLLOW=0.22` 平滑） |
| 2 | 待机动画 | **已实现** | `applyIdle` 让颈/胸/尾缓慢摆动（瞥视周期 140 tick、持续 50 tick，范围 32 格）；探针在"真的站定"后测两帧骨架旋转量和 82.19→91.92（**PASS**） |
| 3 | 撕咬动画 | **已实现** | `BITE_TICKS=18`、`BITE_HIT_TICK=9` 的咬击时钟；I&F `BITE1–3` 姿态按 `biteProgress()` 混合，另有程序化兜底开口；实机探针：**一次咬击一次结算**（木偶 20→0，随后自行结束） |
| 4 | 多段命中箱 | **已实现并端到端实测** | 6 个部位（head/neck/body/left_wing/right_wing/tail），id=父+1..6；实机探针五项全 PASS：服务端认识 6 部位、客户端同一批、**准星命中部位**、**真实玩家点击经网络让主体扣血**（100→99）、stage 缩放两侧一致（0.34→2.27） |
| 5 | 死亡→残留链路 | **复核通过** | 实机探针 3 条死亡路线（冰/雷/火 × 箭/爆炸/玩家攻击）全部 PASS：原始 I&F 残留**实体**重新出现在世界 |
| 6 | 蓝眼 | **最终裁决：移除注册** | 7G 重建后仍然浮空（7H 缩略/侧面截图证明眼贴片脱离头骨）；按 7H §7 授权撤掉图层注册，源码保留待头部几何重做 |

---

## 2. 版本与安全

| 项目 | 值 |
|---|---|
| Git HEAD | `34a97714bef7a5a130461d737e73f5a3629dc978`（分支 `main`，**本轮未动**） |
| 备份 | `D:\MinecraftModding\_backups\round7h_before\`（118 个文件：soulfire、compat\iceandfire、brood） |
| 破坏性操作 | 0（无 `reset`、无 `checkout --`、无删除） |
| 本轮改动范围 | 仅 `soulfire`、`compat\iceandfire\client`、`client` 探针、`soulfire` GameTest |

---

## 3. 多段命中箱（参考包 stage 2）如何落地

### 3.1 六个部位与命名

`SoulfireDragonEntity.initSoulfireParts()` 建立固定顺序的数组，**顺序即语义**（下标 0..5）：

| 下标 | 名称 | 基准尺寸（stage 4） |
|---|---|---|
| 0 | `head` | 1.7 × 1.2 |
| 1 | `neck` | 1.3 × 1.1 |
| 2 | `body` | 2.8 × 2.3 |
| 3 | `left_wing` | 2.8 × 0.75 |
| 4 | `right_wing` | 2.8 × 0.75 |
| 5 | `tail` | 1.7 × 0.9 |

### 3.2 身份方案（与末影龙同构）

`setId(int)` 被覆写：父体拿到 id 后，立即把 6 个部位设为 `id+1 .. id+6`。这样**任何部位都不可能被误认成别的龙**的部位，而且客户端/服务端都能用同一个算式定位。

### 3.3 Forge 已经做好的两半（本轮只负责"放进世界"）

读 Forge 1.20.1 源码确认了机制边界，这正是多段能"玩家可点、箭可中"的原因：

| 位置 | 行为 |
|---|---|
| `ServerLevel.EntityCallbacks#onTrackingStart` (1521–1525) | 父体被追踪时，自动把 `getParts()` 全部登记进 `ServerLevel.dragonParts` |
| `ServerLevel#getEntityOrPart(int)` (1111–1113) | 先查普通实体，再查 `dragonParts` |
| `ClientLevel.EntityCallbacks#onTrackingStart` (960–964) | **客户端同样自动登记**部位到 `ClientLevel.partEntities` |
| `Level#getEntities(Entity, AABB, Predicate)` (656–660) | 遍历完普通实体后，**额外遍历 `getPartEntities()`** —— 准星射线用的就是它 |
| `ServerboundInteractPacket#getTarget(ServerLevel)` (66–68) | `return level.getEntityOrPart(this.entityId)` —— 玩家的攻击包按 **部位 id** 也能解析到部位 |

因此本 mod 只需要：服务端每 20 tick 用 `ensurePartsInWorld()` 把部位**加入世界**（`addFreshEntity`，只做一次，防止幽灵实体），其余由 Forge 自动完成。客户端的部位位置由客户端自己的 `updateSoulfireParts()` 每 tick 计算（客户端也要看到"移动中的命中箱"），与服务端近似一致。

### 3.4 命中箱随 stage 缩放

`getSoulScale()`：stage 1→0.20、2→0.40、3→0.6667、4→1.0、5→1.3333；`setStage()` 与 **客户端** `onSyncedDataUpdated(DATA_STAGE)` 都会调用 `resizeSoulfireParts()`，所以服务端命中箱和客户端准星框一起长大。

> 注意：这里缩放的是**命中箱**，模型绘制仍用原有的 `stageScale()`；二者故意区分——画面可以任意大，命中箱必须维持在"玩家真的打得中"的尺寸。

### 3.5 部位不会成为幽灵

`remove(RemovalReason)` 覆写：只要不是客户端，就把 6 个部位 `discard()`。契约到期、被杀、区块卸载，都不会留下可被射击的残留命中箱（GameTest 有专项断言）。

---

## 4. 颈部转向 / 待机 / 撕咬的实现

### 4.1 数据同步（服务端→客户端）

| 同步字段 | 类型 | 含义 |
|---|---|---|
| `DATA_HEAD_YAW` | Float | `Mth.wrapDegrees(getYHeadRot() - yBodyRot)`，相对身体的头转 |
| `DATA_HEAD_PITCH` | Float | `Mth.wrapDegrees(getXRot())` |
| `DATA_BITE_TICKS` | Int | 咬击时钟，`-1` = 没在咬 |

`syncHeadRotation()` 只在变化 > 0.5° 时写入，静止的头不花带宽。

### 4.2 动画调用链（`SoulfireDragonAnimator`）

| 方法 | 作用 | 关键参数 |
|---|---|---|
| `applyLook` | 颈/头转向 | Neck1 18%、Neck2 24%、Neck3 26%，Head 拿剩下的 32%；上限 `MAX_YAW=45°`、`MAX_PITCH=25°`；`FOLLOW=0.22` 逐帧追随（是"转过去"而不是"跳过去"） |
| `applyIdle` | 待机呼吸/摆动 | Neck2/胸/尾缓慢正弦摆动（站立时） |
| `applyBite` | 撕咬 | 按 `biteProgress()` 在 I&F `BITE1–3` 三张姿态间混合；没有姿态库时走程序化兜底（`openJaws`） |

`applyLook` 在每帧渲染时调用，源数据就是上面同步的 yaw/pitch —— **画面里的头和服务器认为的头是同一个数**，这也是命中箱能对准"看得见的头"的原因（`updateSoulfireParts()` 用 `getYHeadRot()` 摆头部位）。

### 4.3 待机瞥视（服务端行为）

`GLANCE_PERIOD=140`、`GLANCE_LENGTH=50`、`GLANCE_RANGE=32`：空闲时每隔约 7 秒，单位会把头转向**女王**最多 50 tick（`tickIdleGlance()`）。这与 `applyIdle` 的微小摆动叠加，让站立单位"像活着"。

### 4.4 撕咬时序（伤害只结算一次）

| 常量/方法 | 值/职责 |
|---|---|
| `BITE_TICKS` | 18（一次咬击的总时长） |
| `BITE_HIT_TICK` | 9（**唯一**结算伤害的 tick） |
| `startBite(victim)` | 拒绝：客户端调用、已在咬、目标为 null/已死、`SoulfireTargeting.isHuntable` 不通过 |
| `tickBite()` | 服务端计时；第 9 tick 调 `closeJaws()`，第 18 tick `endBite()` |
| `closeJaws()` | 唯一扣血点：重新取 UUID → 目标仍存活、仍可猎杀、距离 ≤ `MELEE_REACH+1.5`（5+1.5）→ `doHurtTarget`；`biteDealt` 保证一次咬击最多一次 |
| `endBite()` | `clearTemporaryCommand()` 与 `stopBreathOrder()` 都会调用——女王的新命令会取消正在进行的咬击（不会"边转身边咬"） |

`SoulfireCommandGoal.bite()` 现在只负责 **发起** 咬击（`dragon.startBite(victim)`）并把冷却设为 `BITE_COOLDOWN * 2`：伤害由动画时钟结算，所以**"一次咬击 = 一次伤害"在代码结构上成立**，而不只是靠巧合。

---

## 5. 蓝眼的最终裁决：移除注册

7G 把眼睛图层的常量改成了"图层真正绘制使用的单位"，但 7H 的实机截图（`build\probe_shots\round7h\zoom_soulfire_show_2_side.png` 等）显示：眼贴片仍然是一对**平片四边形**，它们**飘在头骨旁边**而不是嵌在眼窝里。

按 7H §7 的授权，本轮**撤掉 `SoulfireEyesLayer` 的注册**：

* `SoulfireIafDragonRenderer` 中不再 `addLayer(new SoulfireEyesLayer(...))`，并留下说明性注释（记录"为什么移除"和"什么时候可以拿回来"）；
* `SoulfireEyesLayer.java` 源码**保留未注册**——它并不坏，只是需要重做的头部几何；
* 结果：单位回到 7F 签收过的样子（普通骨龙、无发光眼），而不是带着一个明知会飘的补丁出场。

---

## 6. 死亡 → 残留回归链路复核

上一轮"看不到残留回来"的疑问，本轮定性为**调试单位 vs 真实单位的混淆**，并按两条腿复核：

1. **真实链条**：探针用真实的 I&F 技能从**真实骨龙尸体**升起单位（冰/雷/火三种），再用真实伤害源击杀（箭、爆炸、玩家攻击三条路线），然后**在世界里找原始残留实体本身**（不是查账本"应该回来了"）。三条路线 **全部 PASS**：原始 I&F 残留实体重新出现在世界中（`build\probe_7h3.log`、`build\probe_7h5.log`）。
2. **调试单位**：没有原始残留的调试单位现在会明确打出 `DEBUG_UNIT_NO_ORIGINAL_REMAINS` 日志——"没回来"与"本来就没有"从此不会再看错。

---

## 7. 修改清单（哪些文件、为什么）

| 文件 | 改动 | 为什么 |
|---|---|---|
| `soulfire/SoulfireDragonEntity.java` | 同步字段 `DATA_HEAD_YAW/PITCH/BITE_TICKS`；`BITE_TICKS=18`/`BITE_HIT_TICK=9`；`startBite/endBite/tickBite/closeJaws/biteProgress`；`syncHeadRotation`；待机瞥视 `tickIdleGlance`（`GLANCE_*`）；多段命中箱：`initSoulfireParts`/`resizeSoulfireParts`/`getSoulScale`/`updateSoulfireParts`/`positionPart`/`ensurePartsInWorld`/`getHeadHitboxCentre`/`isMultipartEntity`/`getParts`/`setId`/`setPartIds`/`remove` | 本轮功能主体：动画的数据源、咬击的时钟、6 个命中箱与它们的生命周期 |
| `soulfire/SoulfireDragonPart.java`（新） | 单个命中箱：`hurt` 转发父体、`is(Entity)` 认父体、按 stage `resize` | 命中箱共享一个血池：打在任何部位都只算一次伤害，也不会产生第二条残留记录 |
| `soulfire/SoulfireOrders.java` | 解析时 `candidate instanceof PartEntity part ? part.getParent() : candidate`（第 76、101 行） | 让选择/指令把"部位"解析成它的主体——玩家点到翅膀，命令仍发给那条龙 |
| `soulfire/goal/SoulfireCommandGoal.java` | `bite()`：`dragon.startBite(victim)` + `biteCooldown = BITE_COOLDOWN * 2` | 伤害交给动画时钟结算，goal 只负责发起与冷却，杜绝"goal 每 tick 各扣一次" |
| `soulfire/SoulfireRising.java` | 没有原始残留时输出 `DEBUG_UNIT_NO_ORIGINAL_REMAINS` | 把"调试单位"与"真实单位"在日志层面分开，上一轮的误判不会再发生 |
| `compat/iceandfire/client/SoulfireDragonAnimator.java` | `applyLook` / `applyIdle` / `applyBite` + `turn()` / `isIdle()` | 颈部逐节转向、站立微动、真实撕咬姿态 |
| `compat/iceandfire/client/SoulfireIafDragonRenderer.java` | 撤掉 `SoulfireEyesLayer` 注册（保留注释与源码） | 蓝眼的最终裁决：宁可不要，也不带一个浮空补丁出场 |
| `client/SoulfireShowProbe.java`（新） | 7H 实机探针：5 张定点截图 + 夜晚、颈部转向、撕咬、待机、**多段三问**、死亡三路线 | 只有实机能回答"看得见的东西对不对" |
| `soulfire/SoulfireMultipartGameTest.java`（新） | 6 项 GameTest（多段 5 项 + 咬击 1 项） | 把"一次命中=一次伤害""部位随主体""部位随主体消亡"变成回归网 |

> 备份：`D:\MinecraftModding\_backups\round7h_before\`；无删除、无 Git 操作。

---

## 8. 客观验证（本轮实际跑过的）

### 8.1 实机探针（真实客户端 + 集成服务端 + 真实 Ice and Fire）

最终一次运行：`build\probe_7h9.log`（**全项 PASS**）。

| 探针项 | 断言内容 | 结果 |
|---|---|---|
| 颈部转向 | 女王站到单位侧方，服务端转头；探针读**同步后**的 `syncedHeadYaw()` | **PASS**（同步 yaw 48°；动画用的就是这个数） |
| 待机动画 | 单位**真的站定**（服务端无位移且**客户端行走动画速度 0.0**）后，隔 25 tick 比较全身骨架旋转量之和 | **PASS**（82.19 → 91.92，变动 9.74） |
| 撕咬 | 面前放木偶，发起咬击，结算后比对血量 | **PASS**（20 → 0，一次咬击一次结算，随后咬击自行结束） |
| 多段①服务端 | 6 个部位是否被世界认识（`getEntityOrPart`） | **PASS**（head#7686 neck#7687 body#7688 left_wing#7689 right_wing#7690 tail#7691 全部 known） |
| 多段②客户端 | 客户端副本是否有同一批部位，且都在客户端的部位表里 | **PASS**（6/6 都在 `ClientLevel.partEntities`） |
| 多段③准星 | 用**游戏自己那条射线**（`ProjectileUtil.getEntityHitResult`）从女王眼睛沿视线找目标 | **PASS**（命中"单位的一个部位"，即翅膀可被点到） |
| 多段④真实点击 | 用真实玩家攻击（`gameMode.attack`，即点击发出的那个包）打该部位 | **PASS**（单位血量 100 → 99：客户端→网络包→服务端 `getEntityOrPart`→`part.hurt`→`dragon.hurt` 全链路成立） |
| 多段⑤缩放 | stage 1 ↔ 5 时头部位宽度（服务端与客户端） | **PASS**（0.34 → 2.27，两侧一致） |
| 死亡→残留 | 冰/雷/火三种真实骨龙 → 真实技能升起 → 真实伤害源击杀 → 在世界里找**原始残留实体** | **3/3 PASS**（`build\probe_7h3.log`、`probe_7h5.log`、`probe_7h9.log`） |
| 蓝眼 | 定点截图（正面/侧面/45°/下方/夜晚） | 眼贴片仍飘 ⇒ 裁决为**移除注册**（见 §5） |

### 8.2 探针排查过程（值得记录的两件事）

1. **"一次命中"的帧序**：GameTest 的 `onEachTick` 与实体 `tick()` 不在同一帧序上，按"第 9 tick 必须掉血"断言会误报 ✗。改为**统计掉血次数**（`drops == 1`）后，测试与实现都不再依赖帧序 ✔。
2. **准星从眼睛出发，而不是从脚**：探针把女王放在"翅膀中心的高度"，`placeCamera` 的俯仰按**脚部位置**算 ⇒ 射线从眼球（比脚高 1.62 格）水平射出，**从翅膀上方掠过** ✗（诊断打印：box y 71.96–72.96，眼睛 y 74.08，`clip` = false）。改成"从眼睛看向翅膀"后立即 PASS ✔。这条对**将来任何准星/命中测试**都是通用教训 ✔。

### 8.3 已知未解的一致性问题（如实记录）

同一次运行里，探针诊断出：**客户端副本的身体朝向与服务端不同**（`yBodyRot`：服务端 ≈ 0°，客户端 ≈ 58.6° ⇒ 差值来自客户端把"头转向"带成了"整身转动"）。后果：

* 头、胸两处，客户端与服务端位置几乎重合（差 < 0.1 格 ✔）；
* **翅膀**两端差约 3.3 格 ✗ —— 因为翅膀按身体朝向摆放；
* 实际影响有限：玩家点到"看得见的翅膀"时，客户端发出的是**那个部位 id**，服务端用同一个 id 解析到**它自己的**同一部位并扣血 ⇒ **伤害一定落在主体上**（已实测 PASS ✔）；受影响的只是"服务端认为该点属于哪个部位"这一几何判断（箭矢/爆炸）✗。

修法（留给 7I，本轮**不动**，以免最后一刻引入新风险）：同步 `DATA_BODY_YAW`，并在客户端把 `yBodyRot/yBodyRotO` 固定为该值——这样画面、客户端命中箱、服务端命中箱三者同向；同时需要确认 I&F 模型自身是否已经把 `yHeadRot - yBodyRot` 应用到头骨上（若已应用，`applyLook` 会叠加成双倍，需要在实机上用肉眼确认）。

### 8.4 截图

`build\probe_shots\round7h\`（**最终验证运行 13:00–13:02 的那一套**）：`soulfire_show_1_front / 2_side / 3_angle / 4_below / 5_night / 6_look_left / 7_bite / 8_idle_a / 8_idle_b / 9_parts`，以及用于裁决蓝眼的放大图 `zoom_soulfire_show_1_front.png` / `zoom_soulfire_show_2_side.png`。

> 客户端实例的截图目录：`D:\我的世界测试版\2\versions\1.20.1-Forge_47.4.23\screenshots\soulfire_show_*`（探针每次运行都会覆盖同名文件，所以那里留下的就是最后一次运行的一套）。

---

## 9. GameTest 结果

| 运行 | 日志 | 总数 | 关注的批量 | 结论 |
|---|---|---|---|---|
| A | `build\gametest7h.log` | 178 | `soulfire_dragon:1` **19/19 通过**（无 `failed!`）；`soulfire_multipart:1` 5 项 4 过 1 败 | 新批的败项是**测试自身的错**：第一版拿另一条骨龙当咬击目标，而 `SoulfireTargeting.isHuntable` 明确规定"骨龙不打骨龙" |
| B | `build\gametest7h2.log` | 178 | 同 A | 咬击断言改为"第 9 tick 掉血"仍败（帧序问题，见 §8.2） |
| **C** | `build\gametest7h3.log` | 178 | `soulfire_dragon:1` **19/19 通过**；`soulfire_multipart:1` **5/5 通过**（含修复后的咬击测试） | **soulfire 两批 24/24 全绿** ✔ |

**与本轮无关的失败**（每轮 9 项，集合在三次运行之间发生变化）：

* 稳定出现：`identityandpopulation`、`territorycore_stopsthenaturalsurfacespawner`、`packaggrobothwaysandnofriendlyfire`、`aswingistheswingofthegameanditmovestheweaponarm`；
* 不稳定（每轮互换）：`webshotinstantguard` / `snowballinstantguard` / `egginstantguard`、`shieldreaction_israisedwhiletheshooterisstillaiming` / `shieldreaction_stopsanarrowonacollisioncourse`、`shieldreaction_holdsthroughavolley`、`shieldreaction_holdsvisiblythenreleases`、`meleeswingsofeveryhumanoidbodyreallyremovehealth`。

三点说明：

1. 这些全部属于**人类 / 史莱姆王**模块，与 soulfire 无交集；
2. 7G 那份日志是"确认 soulfire 批量通过后即终止全量运行"（约定），**从未跑完整轮**，所以这些项在 7G 从未被观察到；
3. 运行 A/B/C 三次比对（同一份代码连跑三次）给出了它们稳定/不稳定的直接证据 ▲；三份日志都在 `build\` 下。

---

## 10. 未完成 / 待玩家确认

| # | 事项 | 现状 |
|---|---|---|
| 1 | 蓝眼 | 已**移除注册**（源码保留）。是否接受"没有发光眼"，请玩家定夺；恢复需要重做头部几何 |
| 2 | 客户端/服务端身体朝向不一致（§8.3） | 未修（本轮刻意不动）。影响：客户端看到的翅膀位置与服务端命中箱差约 3.3 格；**点击仍会正确扣主体血**（已实测 ✔），受影响的只是服务端对"该点属于哪个部位"的几何判断 |
| 3 | 颈部动画是否与 I&F 模型自带头骨旋转叠加（是否会转两倍） | 需**肉眼**确认（图 `6_look_left` / 实机）；代码层无法自动判断 |
| 4 | 待机/瞥视/咬击的**幅度与节奏**（140/50 tick、45°/25° 上限、18 tick 咬击） | 数值已写在文档与代码里，请按手感提意见 |
| 5 | 命中箱是**近似骨架**（服务端按头/颈/胸/尾/双翼摆放） | 极端姿态（盘旋、俯冲）下与画面可能略有偏差；逐骨骼跟随留给后续轮次 |
| 6 | 全套 GameTest 中人类/史莱姆王模块的 8–9 项失败 | 与本轮无关（§9，三次运行集合还略有不同），未修：不属于 7H 的授权范围 |

---

## 11. 已部署 jar

| 项目 | 值 |
|---|---|
| 源 | `build\libs\spiderqueen_hungrycraft-0.0.1-dev.jar`（6785 KB，2026-10-10 12:59:13） |
| 目标 | `…\1.20.1-Forge_47.4.23\mods\spiderqueen_hungrycraft-0.0.1-dev.jar` |
| SHA-256 | `2A0C3ABE907BE88172D21F55CB2912DF07759325842C378C1F943648564D83C0` |
| 同时内容 | `iceandfire-2.1.13-1.20.1-beta-5.jar`、`citadel-2.6.3-1.20.1.jar` |
| 说明 | `deployIceAndFireTest` 只做构建与复制（会先删掉同 modid 的旧 jar），**从不自行启动游戏** |

---

## 12. 实机验收步骤（请你来看）

1. 启动测试实例（`D:\mcinst` 对应 `1.20.1-Forge_47.4.23`，内含 I&F 与本 mod 的最新 jar，SHA 见 §11）。
2. 用**亡灵法术权杖**升起一条骨龙（或用 `/summon spiderqueen_hungrycraft:soulfire_dragon`）。
3. **站立观察**：颈部应有轻微起伏（呼吸/摆动），每隔约 7 秒会把头**转向你**；你绕着它走，头会跟着转（上限 ±45°）。
4. **下令攻击**一个敌人（权杖攻击指令）：靠近时**张口 → 咬合**（约 1 秒），目标只掉**一次**血；命令打断时咬击会立刻结束。
5. **F3+B**：应看到 **6 个**命中箱（head / neck / body / left_wing / right_wing / tail），并随 stage（1→5）一起变大；用**箭**射翅膀或尾巴 → 主体掉血（一次一发，不会重复扣）。
6. **死亡**：用真实伤害击杀 → 原来的 I&F 残留（骨龙尸体）**实体**重新出现在原地。
7. **眼睛**：现在是普通骨龙（无发光眼）——如果希望它发光，请说明，7I 会重做头部几何后再挂图层。
