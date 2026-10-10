# ROUND 7I — Stage 1：真实空中转向、身体朝向同步、I&F 飞行/转弯/降落姿态与吐息头部稳定性

> 承接 ROUND 7H。本轮按玩家要求**只安装 Stage 1**：以 GPT-6 `SoulfireDragon_GPT6_Round7I_ReferencePatch.zip` 为参考源码，逐文件哈希校验后落地，然后由我（DS）在本地真实环境里编译、跑 GameTest、启动客户端做开阔地飞行实测。
> **不重新设计灵火龙、不重建骨龙模型、不启用 I&F 原生 AI、不删独立飞行逻辑、不碰蓝眼/骑乘/复活身份。**

---

## 1. 结论速览（Stage 1）

| # | 事项 | 状态 | 证据 |
|---|---|---|---|
| 1 | 空中真实转向（每 tick ≤ 8°，先转后飞） | 见 §5 | 实机探针：90° 与 180° 腿的 yaw 变化率、运动方向一致性、反向腿不得后退 |
| 2 | 身体朝向同步 `DATA_BODY_YAW` | 见 §5 | 服务端/同步值/客户端三者对齐读数（7H §8.3 的 ~58.6° 端间偏差在飞行时修复） |
| 3 | I&F 转弯姿态（bank） | 见 §5 | `DATA_AIR_BANK` → `BodyUpper.rotateAngleZ` + `armL1/armR1`（原骨架，非假翼） |
| 4 | 下降/落地姿态混合 | 见 §5 | `LANDING` / `LANDING_BLEND` 同步值 + 实机落地帧 |
| 5 | 吐息时头颈稳定 | 见 §5 | 逐 tick 采样 `syncedHeadYaw` 的步长/方向反转统计 |
| 6 | 回归（MOVE/HOLD/FOLLOW/降落/契约/命中箱/咬击） | 见 §6 | 全量 GameTest（179 项） |

---

## 2. 版本与安全

| 项目 | 值 |
|---|---|
| 补丁包 | `D:\MinecraftModding\_references\SoulfireDragon_GPT6_Round7I_ReferencePatch.zip` |
| 包内 SHA-256 | `B1E015C0A8F2089BB042B366B67E8FA04A7AB93CA625FB44EF8CED17025C4A17`（54.3 KB，54 个条目层级） |
| 补丁内文件校验 | Stage 1 三个文件全部匹配 `manifest.json` 的 `new_sha256` ✔（详见 §4） |
| 应用前工程校验 | 本地 `SoulfireDragonEntity.java` / `SoulfireDragonAnimator.java` / `SoulfireIafDragonRenderer.java` 全部匹配 manifest 的 `old_sha256` ✔ ⇒ **干净应用，无需定向合并** |
| 我的外部备份 | `D:\MinecraftModding\_backups\round7i_before\`（40 个文件：`soulfire`、`compat\iceandfire`） |
| 安装器外部备份 | `D:\MinecraftModding\_soulfire7i_external_backups\stage1_20261010_181718_206525\`（含 `backup.json`） |
| Git | HEAD 仍为 `34a97714bef7a5a130461d737e73f5a3629dc978`；**未执行** `reset/checkout/clean/commit/stash` 等任何操作 |
| 回滚 | `py -3 rollback_patch.py <工程根> <备份目录>`（§9） |

---

## 3. Stage 1 装了什么（逐项）

### 3.1 `SoulfireDragonEntity.java`（+124 / −12）

| 改动 | 作用 |
|---|---|
| 新增同步字段 `DATA_BODY_YAW`、`DATA_AIR_BANK`、`DATA_LANDING`、`DATA_LANDING_BLEND` + 访问器 `syncedBodyYaw()/syncedAirBank()/landingAnimationProgress()` | 把"真实朝向 / 转弯倾斜 / 正在降落 / 降落混合进度"交给客户端动画 |
| `MAX_AIR_TURN_DEGREES = 8.0F`，飞行时按目标方位逐 tick 转向：`turn = clamp(wrapDegrees(desiredYaw - yaw), ±8)`，随后 `setYRot/yBodyRot/DATA_BODY_YAW` 一起更新，`DATA_AIR_BANK = clamp(turn * 1.75, ±14)` | **先转身再前进**：`turnSpeed` 在剩余 >60° 时为 0（原地转），>25° 时 0.55，否则 1.0 ⇒ 不会倒着飞、不会横着飞 |
| `isLanding()` 改为客户端读同步值；新增 `setLandingFlag()` 统一写入（并清零 blend） | 动画第一次真正"知道"龙在降落 |
| 服务端飞行时 `yBodyRot = getYRot()` 并同步 `DATA_BODY_YAW`（阈值 0.25°）；客户端在飞行/悬停/降落时把 `yBodyRot` 平滑追向同步值并 `setYRot(yBodyRot)` | 修复 7H 实测到的"客户端整身随头转、与服务端差 ~58.6°"根因 |
| 降落最后约 7 格按高度差产生 `LANDING_BLEND`（上限 0.96） | 给 I&F 原生地面姿态一个平滑入口 |
| 所有 `this.landing = ...` 改为 `setLandingFlag(...)` | 同步与内部状态永不脱钩 |

### 3.2 `SoulfireDragonAnimator.java`（+62 / −16）

| 改动 | 作用 |
|---|---|
| `shownYaw/shownPitch`（共享字段）→ `WeakHashMap<SoulfireDragonEntity, float[]>` | 一个渲染器服务多条龙，平滑状态不再互相串味 |
| 新增 `isLanding()` 分支：I&F 六个 `FLIGHT` 姿态 + `HOVERING_POSE`(0.35) + 按 `landingAnimationProgress()` 混入 `GROUND_POSE` | 降落过程用**原版姿态**过渡 |
| 悬停时补 `HOVERING_POSE`(0.6)；快速下坠（`dy < −0.20`）补 `DIVING_POSE`(0.18) | 更贴近 I&F 飞行动作 |
| 新增 `applyAirTurn`：`syncedAirBank()` → `BodyUpper.rotateAngleZ`(×0.75) 与 `armL1/armR1.rotateAngleX`(∓0.20) | 转弯倾斜施加在**原骨架**上，不是假翼 |
| 头颈：改用 `wrapDegrees` 差值 + 逐实体状态；**吐息时响应系数 0.10**（平时 0.22） | 缓解吐息时头部抖动 |
| `applyPose` 插值 0.75 → 0.50 | 姿态过渡更柔 |

### 3.3 新增 `Soulfire7IFlightGameTest.java`

`flightmotionfollowsdragonsfacing`：飞行中每个采样点要求 **实际水平运动方向与 `getYRot()` 的前向余弦 ≥ 0.94**，并要求至少 5 个空中样本 —— 检查的是**真实速度方向**，不是命令状态。

> 未触碰：`SoulfireRising`、契约台账、RTS 命令执行器、权杖选择、骑乘、吐息伤害、蓝眼。**未引入任何 I&F 原生实体或 AI。**

---

## 4. 应用过程（可复核的原始输出）

1. **包完整性**：ZIP SHA-256 = `B1E015C0…4A17`；解包到 `D:\MinecraftModding\_patches\Soulfire7I\`。
2. **补丁文件哈希**（对照 manifest `new_sha256`，全部 MATCH）：

| 文件 | 前缀 | 结果 |
|---|---|---|
| `stage1/.../SoulfireDragonAnimator.java` | `2d5a5812da8dc9d0` | MATCH |
| `stage1/.../Soulfire7IFlightGameTest.java` | `f8109780cba8e9c2` | MATCH |
| `stage1/.../SoulfireDragonEntity.java` | `dd12d4375a2d9ba8` | MATCH |
| `stage2/.../SoulfireIafDragonRenderer.java` | `40d00212312745e5` | MATCH（Stage 2，未安装） |
| `stage2/.../Soulfire7IMultipartGameTest.java` | `6397962c16096e4e` | MATCH（Stage 2，未安装） |
| `stage2/.../SoulfireDragonEntity.java` | `ed7febe0b06be1b0` | MATCH（Stage 2，未安装） |

3. **本地基线哈希**（对照 manifest `old_sha256`，全部 MATCH ⇒ 补丁正是针对当前 7H 工程）：

| 文件 | 前缀 | 结果 |
|---|---|---|
| `soulfire/SoulfireDragonEntity.java` | `8d02fb0b2aba3e44` | MATCH |
| `compat/iceandfire/client/SoulfireDragonAnimator.java` | `560f7d7f6607b7ff` | MATCH |
| `compat/iceandfire/client/SoulfireIafDragonRenderer.java` | `c9279385f027b170` | MATCH |

4. **dry-run**（安装器默认只读）：`READY REPLACE …Animator.java / READY ADD …FlightGameTest.java / READY REPLACE …Entity.java / DRY-RUN PASS | 3 pending`，**0 REFUSE**。
5. **apply**：三个文件 `APPLIED`，安装器自带备份目录已打印（§2）。
6. **落盘复核**：应用后三个文件的 SHA-256 与 manifest `new_sha256` 完全一致 ✔。
7. **编译**：`gradlew compileJava` → **BUILD SUCCESSFUL**（11s），**没有出现 Forge/Citadel API 签名错误**，因此未做任何最小修正（保留的原始日志：`build\c7i1.log`）。
8. **改动规模**（`git diff --no-index`，完整 diff 存 `build\diff7i_entity.diff`、`build\diff7i_animator.diff`）：

```
SoulfireDragonEntity.java   | 136 +++++++++--   1 file changed, 124 insertions(+), 12 deletions(-)
SoulfireDragonAnimator.java |  78 ++++++----    1 file changed,  62 insertions(+), 16 deletions(-)
```

---

## 5. Stage 1 实机验证

### 5.1 探针

新增 `client/Soulfire7IFlightProbe.java`（`-Dspiderqueen.soulfire.flight=true` 才生效）：把一条成年灵火龙放到女王上方 40 格的空中，依次执行

1. **起飞**（`setFlying(true)`），
2. **90° 腿**：从朝向 0° 下令飞向 +X 60 格（目标方位 −90°），
3. **180° 反向腿**：下令飞向 −X 60 格（目标方位 +90°），
4. **落地腿**：下令降到 30 格外地表（读 `LANDING` / `LANDING_BLEND`），
5. **吐息腿**：`breatheAt(...)` 后逐 tick 采样头部朝向。

每 tick 采样的判据（与 §3 的实现一一对应）：

* 每 tick 转向量 ≤ **8.05°**（`MAX_AIR_TURN_DEGREES`）；
* 移动样本（水平速度 > 0.08）中，`cos(运动方向, 朝向)` ≥ **0.94**；
* **不得后退**：`cos` ≥ −0.05；
* **不得边大角度转身边前进**：剩余方位 > 60° 时水平速度 ≤ 0.25；
* 服务端 `yBodyRot` ↔ 同步值 ↔ **客户端副本 `yBodyRot`** 三者的差值（这正是 7H §8.3 记录的 ~58.6° 偏差的修复点）。


---

### 5.2 实机结果（`build\probe_7i5.log`，干净测试世界）

| 腿 | 结论 | 原始读数 |
|---|---|---|
| 90° | **物理全部正确** | 实际转过 **93°**；51 个移动样本中：与其朝向不一致 **0**、后退 **0**、在大角度转弯中前进 **0** |
| 180° 反向 | **物理全部正确** | 同上三项全 **0**（50 个移动样本） |
| 落地 | **PASS** | `landing` 被观测到 ✔；`LANDING_BLEND` 峰值 **0.96** ✔；落地后 blend 归零 ✔；结束时 y=68 站在地面 ✔ |
| 帧序列 | 13 张 | `soulfire_7i_90_t40/80/120`、`7i_180_t40/80/120`、`7i_landing_blend_60/400`、`7i_landing_done`、`7i_breath_30/60/90/120`（存 `build\probe_shots\round7i\`） |

**两项探针测不准的地方（如实说明，结论以 §6 的 GameTest 为准）**：

* **"每 tick ≤ 8°"**：探针按**客户端 tick**采样；客户端掉帧时一个样本会跨多个**服务端 tick**，于是读出 16°（= 2×8°）甚至 90° 的单步 ✗。这是采样问题，不是转向问题 —— 同一份实现里"0 misaligned / 0 backwards / 0 travelled-while-turning"三项是由**位移**判定的，不受帧率影响 ✔。
* **吐息头部稳定性**：同一原因（单步 30°、窗口内总变动 68°），加上该阶段单位仍在飞向被指向点，读到的是"飞行中的转向"而不是"抖动" ✗。

### 5.3 实机环境问题（与补丁无关，亦已记录）

`probe_world` 累积了历次探针留下的**数百条**骨龙（每条 6 个命中箱）⇒ 客户端低到 <1 tps；线程转储显示同步/碰撞查询正卡在 `Level.getEntities` 的**部件表遍历**上：

```
"Server thread" RUNNABLE
  at it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap$MapIterator.nextEntry
  at net.minecraft.world.level.Level.getEntities(Level.java:656)     <- Forge 的 getPartEntities() 循环
  at net.minecraft.world.entity.Entity.collide(Entity.java:890)
  at spiderqueenhungrycraft.soulfire.SoulfireDragonEntity.travel(SoulfireDragonEntity.java:1366)
```

这正是 Stage 2 要清理的 7H 遗留（幽灵部件被 `ensurePartsInWorld()` 反复重新加入世界）✔。处理办法：复制世界并清空其实体数据得到 `saves\probe_clean`（保留地形）✔；探针开场也会 `/kill @e[type=…soulfire_dragon]` 清场 ✔；`build\probe_paths.txt` 已恢复为原值（`probe_world`）✔。


## 6. GameTest 结果（权威，逐服务端 tick）

运行：`build\gametest7i2.log`（干净 GameTest 世界，**183 项** = 7H 的 178 + 本轮的 5 项）。

### 6.1 本轮新增的 `soulfire7i_flight` 批次（5 项）

| 测试 | 来源 | 结果 | 原始输出 |
|---|---|---|---|
| `flightmotionfollowsdragonsfacing` | **补丁自带** | **FAILED** | `air motion disagrees with yaw: 0.50079793403370` ✗ |
| `aninetydegreeorderturnsbeforeitflies` | DS | **FAILED** | `no turn over 8.05 degrees in one tick: 48.2` ✗ |
| `afullreversalturnsinsteadofflyingbackwards` | DS | **FAILED** | `no turn over 8.05 degrees in one tick` ✗ |
| `adsecdescpassesmeasuredlandingposes` | DS | **通过**（无失败行） | — |
| `theheadholdsthelinethroughoutthebreath` | DS | **通过**（无失败行） | — |

### 6.2 结论：Stage 1 的"真实空中转向"没有成立

* 补丁把转向写进 `travel()`（每 tick ≤8°）✔，但**实体的 `getYRot()` 并不只由这段代码决定**：同一 tick 内还有别的写入者（原版的注视/朝向控制），于是被测到 **48.2°/tick 的整帧跳变** ✗。
---

## 7. 修改清单

| 文件 | 来源 | 变更 |
|---|---|---|
| `src/main/java/spiderqueenhungrycraft/soulfire/SoulfireDragonEntity.java` | 补丁 Stage 1 | 替换（+124 / −12）：同步 `BODY_YAW/AIR_BANK/LANDING/LANDING_BLEND`、空中 ≤8°/tick 真实转向、服务端/客户端身体朝向统一、降落混合进度 |
| `src/main/java/spiderqueenhungrycraft/compat/iceandfire/client/SoulfireDragonAnimator.java` | 补丁 Stage 1 | 替换（+62 / −16）：逐实体平滑状态、I&F 悬停/俯冲/地面姿态混合、转弯倾斜、吐息头部稳定 |
| `src/main/java/spiderqueenhungrycraft/soulfire/Soulfire7IFlightGameTest.java` | 补丁 Stage 1 | 新增：`flightmotionfollowsdragonsfacing`（真实速度方向 vs 朝向） |
| `src/main/java/spiderqueenhungrycraft/client/Soulfire7IFlightProbe.java` | DS（本轮自建，验证用） | 新增：实机 90°/180° 转向、落地、吐息头部稳定性的逐 tick 探针与帧序列 |
| `D:\MinecraftModding\NecromancyIV_Astra_Handoff\ROUND7I_STAGE1_FLIGHT_TURNING.md` | DS | 新增：本文件 |

---

## 8. 未验证 / 已知限制（如实注明）

1. **I&F 姿态的方向与强度未经肉眼标定**：`AIR_BANK`（±14°）、`BodyUpper.rotateAngleZ × 0.75`、`armL1/armR1 ∓0.20`、悬停 0.6 / 俯冲 0.18 都是参考值，需要玩家在实机上看"飞得像不像骨龙"。参考包 README 也这样声明。
2. **没有录像**（环境不产出视频）：我提供的是关键时刻的**帧序列**截图（每条腿每 40 tick 一帧 + 落地/吐息帧），不是运动录像。
3. **Stage 2 未安装**：其 8 条验收标准（F3+B 六框、`getEntityOrPart`、箭矢命中可见头/翼/尾并掉血、端间位置一致、点击识别主体与契约、翼骨在画面内时不误剔、无重复 ID/幽灵部件、MOVE/FOLLOW/HOLD/降落/契约不回归）本轮**未开始**。
4. **未测多人/专用服务器**：只在集成服务端 + 本地客户端验证。
5. **7H 遗留（Stage 2 才修）**：
   * `ensurePartsInWorld()` 会对 `PartEntity` 调 `addFreshEntity(part)` ⇒ 日志噪音 `Tried to add entity soulfire_dragon but it was marked as removed already`（7H 三次运行分别 35 / 1014 / 2895 次 ⇒ **本轮 Stage 1 未引入**）；
   * 站立（非飞行）时客户端/服务端身体朝向仍可能不一致（Stage 1 只统一了飞行/悬停/降落态）；
   * 命中箱仍是 6 段近似骨架。
6. **全量 GameTest 中人类/史莱姆王模块的既有失败**与 7I 无关（三次 7H 运行 + 本轮集合各不相同 ⇒ 属于不稳定项），本轮未修（不在授权范围）。

---

## 9. 回滚方法

1. 安装器回滚（校验当前文件确与本补丁哈希一致后才覆盖）：
   ```powershell
   py -3 D:\MinecraftModding\_patches\Soulfire7I\rollback_patch.py "D:\MinecraftModding\SpiderQueen-HungryCraft-Forge-1.20.1" "D:\MinecraftModding\_soulfire7i_external_backups\stage1_20261010_181718_206525"
   ```
2. 或用我自己的整目录备份直接覆盖：
   ```powershell
   Copy-Item D:\MinecraftModding\_backups\round7i_before\soulfire\* <工程>\src\main\java\spiderqueenhungrycraft\soulfire\ -Recurse -Force
   Copy-Item D:\MinecraftModding\_backups\round7i_before\compat\iceandfire\* <工程>\src\main\java\spiderqueenhungrycraft\compat\iceandfire\ -Recurse -Force
   ```
3. 回滚后需删除新增文件 `Soulfire7IFlightGameTest.java`（补丁新增项，回滚脚本会自动删除其记录的 ADD 项）。

---

## 10. 下一步：只有玩家验收通过后才装 Stage 2

Stage 2 必须**同时**满足（任务书原文）：

1. F3+B 能看见六个合理位置的部件框；
2. `ServerLevel.getEntityOrPart` 能查询到真实部件；
3. 箭矢能命中实际可见的头部、翅膀和尾部并让主体掉血；
4. 客户端和服务端部件位置基本一致；
5. 玩家点击部件后，仍能正确识别主体及契约归属；
6. 主体中心离开视野但翼骨还在画面内时，不会错误消失；
7. 不出现重复实体 ID、幽灵部件或死亡后遗留部件；
8. 原有 MOVE、FOLLOW、HOLD、降落、契约恢复均不回归。

**特别声明的纪律**：F3+B 只是调试显示，**不能**把"看见六个方框"当成"真实攻击判定成功"——第 3、5 条必须用真实箭矢与真实左键点击分别证明（玩家近战需在真实攻击距离内）。

## 11. 交付物清单（本轮全部可复核材料）

| 类别 | 路径 | 说明 |
|---|---|---|
| 构建日志 | `build\c7i1.log` | `compileJava` BUILD SUCCESSFUL（11s，无 API 错误） |
| 构建日志 | `build\deploy7i5.log` | 含探针与 4 项课程测试的最终部署（BUILD SUCCESSFUL） |
| 补丁审阅 | `build\diff7i_entity.diff`、`build\diff7i_animator.diff` | Stage 1 全部改动逐行 diff |
| GameTest | `build\gametest7i2.log` | **权威运行**：183 项，含 `soulfire7i_flight` 5 项 |
| GameTest | `build\gametest7i1.log` | 第一次运行（世界被历史遗留污染，慢） |
| 实机日志 | `build\probe_7i5.log` | 干净世界里的完整实机课程（90°/180°/落地/吐息） |
| 实机日志 | `build\probe_7i1.log`、`build\probe_7i3.log` | 早期两次（环境问题，见 §5.3） |
| 线程转储 | `build\jstack7i1.txt` | 证明部件表遍历成为热点（Stage 2 修） |
| 帧序列 | `build\probe_shots\round7i\`（13 张） | 90°/180° 各 3 帧、落地 2 帧、落地完成 1 帧、吐息 4 帧 |
| 部署 jar | 源 `build\libs\spiderqueen_hungrycraft-0.0.1-dev.jar`（6806 KB，19:30:27）<br>目标 `…\1.20.1-Forge_47.4.23\mods\` | SHA-256 `E48443A95659C6AD35C2931F6EA40F755AA6863E788A11909B860299D160710F` |
| 备份 | `D:\MinecraftModding\_backups\round7i_before\`（40 个文件）<br>`D:\MinecraftModding\_soulfire7i_external_backups\stage1_20261010_181718_206525\` | 应用前源码；安装器自带备份（含 `backup.json`） |
| 补丁包 | `D:\MinecraftModding\_references\SoulfireDragon_GPT6_Round7I_ReferencePatch.zip`（SHA-256 `B1E015C0…4A17`）<br>解包 `D:\MinecraftModding\_patches\Soulfire7I\` | 含 `apply_patch.py` / `rollback_patch.py` / `manifest.json` |
| 本文件 | `D:\MinecraftModding\NecromancyIV_Astra_Handoff\ROUND7I_STAGE1_FLIGHT_TURNING.md` | 含全部原始读数与未验证项 |

---

## 12. 本轮结论与下一步（停止等待验收）

**Stage 1 的判定：部分通过**。

* **通过**：编译（无 API 修正）、地图姿态混合（落地 blend 峰值 0.96 并在落地后归零，GameTest + 实机双证）、吐息头部稳定性（GameTest 通过）、7H 全部既有 soulfire 测试无回归 ✔。
* **未通过**：**"真实空中转向"** —— 单 tick 偏航跳变 48.2°（超出补丁自己的 8° 钳制），补丁自带测试给出的运动/朝向余弦只有 0.50 ⇒ "先转身再沿正确方向飞"在服务端/实机都没有成立 ✗。
* **因此不安装 Stage 2** ✔（符合任务书与参考包 README 的前置条件）。

**我（DS）对根因的初判（未擅自修改产品行为）**：补丁只在 `travel()` 里写 `setYRot/yBodyRot`，但同一 tick 中实体的朝向还会被原版的注视/朝向控制写入；于是"每 tick ≤8°"只约束了其中一位写入者，另一位造成的整帧跳变直接推翻了"朝向 = 飞行方向"这一前提。**建议的下一步（等你决定）**：

1. 在**空中状态**下把该单位对朝向的写入收归一处（例如飞行时让注视控制不参与 yaw，或把飞行朝向作为唯一的权威值），再让运动方向**从朝向导出**；
2. 修好后再重跑本文件 §6 的同一组测试（`build\gametest7i2.log` 的三条失败项就是验收标准）；
3. 只有这三条转绿，才讨论 Stage 2 的八条验收。

**蓝眼睛、复活身份、骑乘、I&F 原生 AI、骨龙模型与独立飞行逻辑：本轮一律未触碰** ✔。


* 结果就是补丁自己那条测试给出的数字：**运动方向与朝向的余弦只有 0.50**（约 60° 不符）✗ —— 也就是说"先转身、再沿正确方向飞"这条**在实机/服务端上并不成立**。
* 反过来说，**落地下半段（姿态混合）与吐息头部稳定性两项通过** ✔，说明参考包在动画侧的两项改动是有效的。

### 6.3 与 7H 的回归对比

本轮完整运行（`build\gametest7i2.log`，183 项）**共 6 项失败**，逐项如下：

| 失败项 | 归属 |
|---|---|
| `flightmotionfollowsdragonsfacing` | **补丁自带**的 7I 转向测试 ✗ |
| `aninetydegreeorderturnsbeforeitflies` | 本轮 DS 的 90° 课程测试 ✗ |
| `afullreversalturnsinsteadofflyingbackwards` | 本轮 DS 的 180° 课程测试 ✗ |
| `identityandpopulation` | 7H 已知的、与人形刷新有关的不稳定项（与本事无关）✗ |
| `territorycore_stopsthenaturalsurfacespawner` | 同上（人类生成器）✗ |
| `guidancehelpsagainstadodgeandleavesanoncourseshotalone` | 史莱姆王引导项（历轮不稳定集合中的一员）✗ |

**没有回归**：7H 的 `soulfire_dragon:1`（19 项）与 `soulfire_multipart:1`（5 项）在本轮日志中**没有任何失败行** ✔；本轮新增的落地与吐息两项也通过 ✔。也就是说，失败全部集中在"空中真实转向"这一件事上 ✔。

> 按参考包 README 与任务书的要求：**Stage 1 未通过 ⇒ 停止，不安装 Stage 2** ✔。


