# ROUND 7F — GPT-6 灵火龙补丁集成、验证与定向修复

> 本轮以 GPT-6 的 `SoulfireDragon_GPT6_Round7F_SourcePatch.zip` 为**基础与真值**，只做集成、编译修复、实机验证与最小范围修复。
> 未重写灵火龙实体、AI、命令系统或飞行系统；未抛弃补丁另起一套方案。
> 本轮只证明两件事：**1) 灵火龙真的使用 Ice and Fire 骨龙模型与动画；2) 飞行任务结束后真的降落、接触地面、收起翅膀。**

---

## 1. 原始代码版本（改动前记录）

| 项目 | 值 |
|---|---|
| Git HEAD | `34a97714bef7a5a130461d737e73f5a3629dc978` |
| 分支 | `main` |
| 脏文件数（开始时 / 结束时） | **339** / 340（仅新增 `SoulfireLandingProbe.java`） |
| stash 数 | 0（**未创建也从未使用**） |
| 破坏性 git 操作 | **无**：未执行 `reset`/`clean`/`checkout`/`commit`/`add`/`stash` |
| 本轮外部备份 | `D:\MinecraftModding\_backups\round7f_before\`（15 个文件：`soulfire` 全包 + 两个探针） |
| 补丁安装器自建备份 | `D:\MinecraftModding\_Soulfire7F_backup_20261010_073801`（3 份被替换文件的原始字节） |

## 2. GPT-6 补丁集成过程

### 2.1 只做校验，不写源码

解压到工程之外：`D:\MinecraftModding\_patches\Soulfire7F\`，先读 `README_CN.md`、`VERIFICATION.txt`、`manifest.json`、`apply_patch.py`、`rollback_patch.py` 与 `patch/` 下 6 个 Java 文件。

补丁自身完整性（6/6 `PATCH-OK`）与三个待替换文件的基线哈希（3/3 `MATCH`）：

```
PATCH-OK  replace soulfire/SoulfireDragonEntity.java        local MATCH bc59636941c29c5a9b09 == bc59636941c29c5a9b09
PATCH-OK  replace soulfire/goal/SoulfireCommandGoal.java    local MATCH 4083ee09211246317001 == 4083ee09211246317001
PATCH-OK  replace client/HungryCraftClientSetup.java        local MATCH d3099f95b8a936ddab63 == d3099f95b8a936ddab63
PATCH-OK  new     compat/iceandfire/client/SoulfireDragonAnimator.java
PATCH-OK  new     compat/iceandfire/client/SoulfireIafTabulaModel.java
PATCH-OK  new     compat/iceandfire/client/SoulfireIafDragonRenderer.java
```[c2.out.log](../SpiderQueen-HungryCraft-Forge-1.20.1/build/c2.out.log)

> 说明：安装器与上面的核对都使用补丁自己的规则（去 BOM、CRLF→LF 后取 SHA-256）。我第一次用 PowerShell 字符串往返比对时误报 DIFFER，**字节级复核后确认一致**，因此**三个替换文件是本轮真实的 7E 快照，不需要手工合并**。

### 2.2 冲突与结构检查

* 包名正确（`spiderqueenhungrycraft.compat.iceandfire.client`，与既有 `compat/iceandfire` 包同级，无重名类）；
* 无重复类、无签名冲突；
* 未引入 I&F 原生 `EntityDragonBase`、原生 Dragon AI、`IafDragonFlightManager`、`SHAKE_PREY`、乘客抓取（补丁只读 I&F 的模型/动画库）；
* 无 I&F 环境不会加载 Tabula 类：注册处由 `ModList.isLoaded("iceandfire") && ModList.isLoaded("citadel")` 分流到 7D 自研渲染器。

### 2.3 正式应用

用补丁自带的安装器写入（**没有**用任何强制覆盖手段）：

```
py -3 .\apply_patch.py "D:\MinecraftModding\SpiderQueen-HungryCraft-Forge-1.20.1" --apply
OK: 已校验 6 个补丁目标，源文件与 ROUND 7E 基线一致。
成功：已安装 6 个文件。
项目外备份: D:\MinecraftModding\_Soulfire7F_backup_20261010_073801
```

### 2.4 I&F / Citadel API 与资源核对（以本地 jar 为准）

`libs/iceandfire-2.1.13-1.20.1-beta-5.jar` + `libs/citadel-2.6.3-1.20.1.jar`，用 `javap` 逐条核对：

| 补丁用到 | 实际 jar 中的签名 | 结论 |
|---|---|---|
| `TabulaModelHandlerHelper.loadTabulaModel` | `static TabulaModelContainer loadTabulaModel(String) throws IOException` | ✔ 存在 |
| `DragonAnimationsLibrary.getModel` | `static TabulaModel getModel(IEnumDragonPoses, IEnumDragonModelTypes)` | ✔ 存在 |
| `EnumDragonPoses` | `GROUND_POSE / SITTING_POSE / WALK1..4 / FLIGHT1..6` 全部存在 | ✔ |
| `EnumDragonModelTypes` | `FIRE_DRAGON_MODEL` 存在（另有 ICE/LIGHTNING） | ✔ |
| `IceAndFireTabulaModelAnimator` | `(TabulaModel)` 构造 + `public void moveToPose(TabulaModel, TabulaModel)` | ✔ 存在 |
| Citadel `TabulaModel` | `(TabulaModelContainer, ITabulaModelAnimator)`、`public ModelAnimator llibAnimator`、`parts()`、`getAllParts()` | ✔ 存在 |
| Citadel `ITabulaModelAnimator` | `void setRotationAngles(TabulaModel, T, float×6)` | ✔ 存在 |
| Citadel `BasicEntityModel` | 抽象 `parts()` 与 **`m_6973_`**（SRG 名）、具体 `m_7695_` | ⚠ 见第 4 节：这是唯一的编译冲突来源 |

资源（I&F jar 内实际存在，逐个列出）：

```
assets/iceandfire/models/tabula/firedragon/firedragon_ground.tbl   18.0KB   ← 补丁使用的路径
同目录 walk1..4 / flight1..6 / sitting / diving / hovering / dead …（60+ 个 .tbl）
assets/iceandfire/textures/models/firedragon/fire_skeleton_1..5.png  6.4-6.5KB
```

**动画链路**（对 Citadel 字节码反汇编确认，不是猜测）：

```
public void m_6973_(Entity, float×5)  →  invokeinterface ITabulaModelAnimator.setRotationAngles(TabulaModel, Entity, FFFFFF)
```

即：Citadel 的模型回调**确实**会把动画交给 `SoulfireDragonAnimator`，姿态来自 I&F 的 `DragonAnimationsLibrary`。

## 3. 六个文件的实际集成结果

| 文件 | 操作 | 结果 |
|---|---|---|
| `soulfire/SoulfireDragonEntity.java` | 替换（补丁） | **原样安装**（与 7E 基线逐字节一致）。新增：`landingTicks` 超时、落地时阻尼速度、接近地面时给出受控负速度、读档把 LANDING 作废。**7E 的契约/版本/直线飞行/摔落免疫/火焰免疫全部保留** |
| `soulfire/goal/SoulfireCommandGoal.java` | 替换（补丁） | **原样安装**。新增：`LANDING_MAX_DROP=192` 的向下真实搜索（要求承载面 + 无流体 + 完整 AABB 空间 + 不加载区块）、找不到落点返回 `null` 而不是空中的假落点、`noLandingSearchUntil` 限流、空中任务改用 `beginLanding`、HOLD 落地后把空中锚点改写到真实地面 |
| `client/HungryCraftClientSetup.java` | 替换（补丁） | **原样安装**。I&F+Citadel 存在 → 注册 `SoulfireIafDragonRenderer`；否则 → 7D 渲染器 |
| `compat/iceandfire/client/SoulfireDragonAnimator.java` | 新增（补丁） | **原样安装**。扩展 I&F 的 `IceAndFireTabulaModelAnimator`，按实体状态选 GROUND/SITTING/WALK1-4/FLIGHT1-6 姿态 |
| `compat/iceandfire/client/SoulfireIafTabulaModel.java` | 新增（补丁） | **修改 1 处以修复编译错误**（见第 4 节），其余保留：惰性加载 `.tbl`、姿态库未就绪则抛错、失败时 `ERROR [Soulfire7F] FAILED` |
| `compat/iceandfire/client/SoulfireIafDragonRenderer.java` | 新增（补丁） | **增加 1 个方法 `boneModel()`**（仅供开发探针在真实客户端上询问"骨架真的加载了吗"），渲染/贴图/缩放逻辑原样保留 |

**是否修改了 GPT-6 补丁？** 是，两处，都在新增的客户端适配文件里，都在第 4 节说明；**没有**改动补丁的实体、Goal、注册三份逻辑。
**是否手工合并？** 不需要：三份被替换文件与补丁基线完全一致，安装器直接通过。

## 4. 编译错误与修复

### 4.1 唯一一处编译错误（首个也是最后一个）

```
src\main\java\spiderqueenhungrycraft\compat\iceandfire\client\SoulfireIafTabulaModel.java:23: 错误:
SoulfireIafTabulaModel不是抽象的, 并且未覆盖BasicEntityModel中的抽象方法m_6973_(SoulfireDragonEntity,float,float,float,float,float)
1 个错误  5 个警告（全部是既有的 `ResourceLocation(String,String)` 过时警告）
```

### 4.2 根因（不是"模型 API 不存在"，而是映射体制）

* `libs/` 里的 I&F/Citadel 是**未反混淆的发行 jar**，MC 继承成员仍是 SRG 名（`m_6973_` = `setupAnim`，`m_7695_` = `renderToBuffer`）；
* 本工程按 `build.gradle` 的注释是**故意**用 `compileOnly` 裸引用它们、**不做 remap**（第 206–221 行写明理由：避免引入重映射步骤与网络依赖）；
* 于是 `extends AdvancedEntityModel` 让同一个动画回调**以两个名字**同时成为抽象方法（Mojang 名与 SRG 名各一个）；
* 若把两个名字都实现，`reobf` 会把 Mojang 名重映射成与 SRG 名相同的 `m_6973_` → 产物里出现**两个完全同签名的重复方法**，是一份坏 class。

### 4.3 最小修复（只改适配器，不动实体继承结构、不动 I&F 的使用）

* `SoulfireIafTabulaModel` 改为继承**原版** `net.minecraft.client.model.EntityModel<SoulfireDragonEntity>`（纯 Mojang 名，映射干净），并把 Citadel 的 `TabulaModel` 作为**委托字段**：
  `renderToBuffer → delegate.renderToBuffer`、`setupAnim → delegate.setupAnim`、`parts/getAllParts → delegate`。
  几何、姿态、贴图**全部仍来自 I&F**，这个类不画任何一个自己的方块（类注释里写明了原因）。
* `SoulfireIafDragonRenderer` 增加 `public SoulfireIafTabulaModel boneModel()`（探针用）。

### 4.4 产物字节码验证（这是"真的接通"的关键证据）

对**已 reobf 的成品 jar** 反汇编：

```
public void m_7695_(PoseStack, VertexConsumer, int, int, float×4)
   24: invokevirtual TabulaModel.m_7695_   ← 画的是 Citadel 的几何
public void setupAnim(SoulfireDragonEntity, float×5)
   20: invokevirtual TabulaModel.m_6973_   ← 动画交给 Citadel，即 I&F 姿态
public void m_6973_(Entity, float×5)        ← 游戏真正调用的桥接方法
   13: invokevirtual setupAnim(SoulfireDragonEntity, float×5)   ← 转发到上面的实现
```

最终结果：`compileJava` ✅、`build` ✅、`deployIceAndFireTest` ✅（BUILD SUCCESSFUL）。

## 5. 原版模型与动画加载证据（真实客户端 + 真实集成服务端）

启动参数：`-Dspiderqueen.soulfire.landing=true`（新探针）+ `-Dspiderqueen.soulfire.hazards=true`（7E 探针回归）。
实例：`D:\我的世界测试版\2\versions\1.20.1-Forge_47.4.23`（I&F 2.1.13-beta-5 + Citadel 2.6.3）。
日志：`build\probe_7f3.log`（模型）、`build\probe_7f.log`（两侧同时）。

```
[Soulfire7F] I&F Tabula renderer selected
[round7F] the game draws the unit with
      spiderqueenhungrycraft.compat.iceandfire.client.SoulfireIafDragonRenderer
      - texture iceandfire:textures/models/firedragon/fire_skeleton_4.png
[Soulfire7F] REAL I&F bone-dragon Tabula model loaded:
      /assets/iceandfire/models/tabula/firedragon/firedragon_ground.tbl
[round7F] I&F skeleton loaded=true parts=104 pose library ready=true
```

* **渲染器**：游戏为这个单位选中的是 I&F 骨龙渲染器（不是 7D 自研渲染器）；
* **几何**：Citadel Tabula 骨架 **104 个部件**（自制方块模型不可能有这个数量级）；
* **贴图**：`iceandfire:textures/models/firedragon/fire_skeleton_4.png`（亡灵龙统一外观，按 stage 取 1..5）；
* **姿态库**：`DragonAnimationsLibrary.getModel(GROUND_POSE, FIRE_DRAGON_MODEL)` 非空 → `SoulfireDragonAnimator` 能取到 GROUND/SITTING/WALK/FLIGHT 姿态；
* **失败面**：整份日志**没有** `[Soulfire7F] FAILED`、**没有** fallback、**没有** "landing timed out"；唯一的 ERROR 是离线客户端账号校验与 Citadel 赞助者福利（与模型无关）。

> 按题面要求，以下**不算**成功：`Renderer registered` / `Mixin applied` / 渲染帧数 / 只是"能看见"。上表给出的是**渲染器类名 + 真骨架部件数 + 姿态库就绪 + 无 FAILED**，而"玩家眼睛看到的形状"仍由第 9 节交给玩家最终确认。

## 6. 降落状态机的测试结果

### 6.1 真实服务端（真实地形、真实玩家，`build\probe_7f3.log`）

判定口径一律是 `onGround() && !isFlying() && !isHovering() && !isLanding() && !isNoGravity()`：

| 场景（对应题面测试 A–E） | 结果 |
|---|---|
| **HOLD**：离地 14 格下达驻守 | `PASS - ended on the floor after 24 ticks at y=63`（起点 y=79→ 地面 63） |
| **MOVE**：60 格外的地面目标 | `PASS - ended on the floor after 135 ticks at y=63; 2 blocks from its goal` |
| **FOLLOW**：40 格外的真实玩家 | `PASS - ended on the floor after 73 ticks at y=63; 2 blocks from its goal`（真的飞到主人身边再降落） |
| **空中保存/重载**：降落途中存档再重建 | `it was landing=true when saved, and it came back landing=false with flying=true` → `PASS - ended on the floor after 52 ticks` |

降落过程没有瞬移：GameTest 2 逐 tick 断言 `|ΔY| < 4.0`（一次跳跃式下降即判失败）。

### 6.2 GameTest 中新增的 5 项（`soulfire_dragon` 批量，全部走权杖入口）

| 测试 | 覆盖 | 结果 |
|---|---|---|
| `aholdfromhighabovefinallylands` | 高空 HOLD 收翼落地（场景 1/2/6 中的"不再长期悬空"） | ✅ |
| `adistanterrandendsonafloor` | 远距 MOVE 抵达并落地（含"非瞬移"断言） | ✅ |
| `afollowercomestothenextfloorallthesame` | FOLLOW 取消旧任务后落地 | ✅ |
| `alandingthatcannotarrivegivesupandtheunitstillcomessdown` | **降落点不可用 → 超时放弃 → 仍能正常降落**（不死锁） | ✅（诊断日志实测在 tick 200 观测到 `gaveUp=true`，即 180 tick 超时生效，随后正常落地） |
| `aunitsavedwhilelandinglandsafterthereload` | 降落途中保存重载后能重新降落 | ✅ |

**无法在 GameTest 中模拟的部分（如实说明）**：
* GameTest 世界**没有真实玩家**，而 FOLLOW 的目标是"玩家列表里的女王"，因此测试 3 只能证明"旧 errand 被取消 + 最终落地"这一半；**"飞到女王身边再落下"由真实服务端探针完成**（上表第 3 行）。
* 平坦测试场地**总有地面**，无法制造"世界上完全没有安全落点"的极端地形；因此测试 4 用**墙后不可达落点**来触发同一条超时路径（这也正是补丁注释里点名的"roof, unloaded chunk, or bad target"这一类）。

## 7. GameTest 总结果

* 总数：**173**（上一轮 168 + 本轮新增 5）；
* 本批量 `soulfire_dragon:1`：**19 项全部通过**（无任何 `failed!` 行）；
* 本轮**无关的既有失败 4 项**（人类 / Sweatlord 家族，本轮一行未动）：
  `identityandpopulation`、`territorycore_stopsthenaturalsurfacespawner`、`packaggrobothwaysandnofriendlyfire`、`aswingistheswingofthegameanditmovestheweaponarm`；
* 与 7E 一样，确认 soulfire 批量通过后即终止全量运行，**全量 173 项未跑到结束**（如实标注）；
* 日志：`build\gametest7f2.log`。

## 8. 客户端启动结果

* I&F 2.1.13-beta-5 与 Citadel 2.6.3 正常加载（I&F 自带的世界生成/生物配置行、Citadel 行都在日志里）；
* 本 mod 正常加载：`[Soulfire7F] I&F Tabula renderer selected`；
* 无 `NoSuchMethodError` / `ClassNotFoundException` / Mixin 失败 / shader 崩溃（7C 的构造期加载问题**没有**复发——模型仍然是首次绘制时才加载）；
* 7E 探针同轮回归全部复现：30 格坠落 100/100 血、火/熔岩 100→100→100 而普通伤害 94、90 格 MOVE 仅升高 5 格、换命令版本 1→2 且旧飞行不再转向。

## 9. 实机验证步骤（交给玩家，必须由人来看）

1. 生出一只灵火龙：**外形应是 I&F 原生骨龙**（长颈、骨架、翼膜由骨架撑开），不是 7D 的方块龙；若是方块龙，日志里必有 `[Soulfire7F] FAILED` 的完整堆栈，请连同堆栈一起反馈。
2. 观察动画：站立时有 I&F 的待机姿态；走动时是 I&F 的行走循环；飞行/悬停时翅膀按 I&F 飞行姿态扇动；驻守时坐下。
3. 站在开阔地，命令它去 60–90 格外的地面目标：看它是否直线飞行、抵达后**下降并真正踩地、收翼**。
4. 让它先飞到 15–25 格高，再按驻守键：应当下降落地、坐好，而不是长期悬空扇翅。
5. 从远处按跟随键：应当先飞回你身边，再在符合距离处找地方落下。
6. 在树林/屋顶附近重复一次 3–5：有安全位置就落下；没有时应保持受控悬停并打印 `no loaded safe landing surface` 警告，**不应**声称已降落。
7. 在它下降途中保存退出、重进世界：不应出现"没有目标的 LANDING"死锁，能重新降落。
8. 顺便确认：契约到期 / 被击杀，龙骨仍然安全回到手里（7E 的关键回归）。

## 10. 未完成项目（明确不声称）

* **视觉比例待调**：`SoulfireIafDragonRenderer.scale` 的 stage 系数（1→0.9、2→1.8、3→3.0、4→4.5、5→6.0）是首版估计值，补丁自己也标注"待调"；**主碰撞盒仍是旧尺寸**，视觉与碰撞的一致性要等多段命中箱那一轮。
* **姿态手感**：姿态集合（GROUND/SITTING/WALK1-4/FLIGHT1-6）确实取自 I&F，但"扇翼节奏看起来对不对"属于主观项，交给玩家定。
* **fallback 路径**：只有在缺 I&F / 缺 `.tbl` 时才出现，本轮没有制造该故障来验证它（只验证了正常路径与"无 FAILED"）；
* **无 I&F 启动路径**：由 `ModList.isLoaded` 分流，本轮未在真实无 I&F 实例上跑（沿用 7D 的注册路径，未改动）；
* 本轮**未做**：多段碰撞箱、新战斗技能、吐息重构、BOMBARD 优化、正式骑乘、蓝色发光眼睛、其他视觉特效；
* 上述 4 项既有 GameTest 失败未修（与本轮无关，属人类/Sweatlord 家族）。

## 11. Git 与备份状态

* HEAD 未变：`34a97714bef7a5a130461d737e73f5a3629dc978`（分支 `main`），脏文件 339 → **340**（仅新增 `SoulfireLandingProbe.java`）；
* **未**执行 `git reset` / `clean` / `checkout` / `commit` / `add` / `stash`；stash 数仍为 0；
* 备份三层齐备：
  1. `D:\MinecraftModding\_backups\round7f_before\`（本轮改动前，15 个文件）；
  2. `D:\MinecraftModding\_Soulfire7F_backup_20261010_073801`（补丁安装器在写入前自动创建，含 3 份原始文件 + `BACKUP_MANIFEST.json`）；
  3. 上一轮 `round7e_before` 仍在原位未被覆盖。
* 回滚方式（补丁自带，且会先校验"当前文件仍是本补丁哈希"才允许覆盖）：
  `py -3 rollback_patch.py "D:\MinecraftModding\_Soulfire7F_backup_20261010_073801" --restore`
  注意：**它不会**还原我在适配器上的两处修复，回滚后需重新编译。

## 12. 已部署 jar 的哈希与构建时间

| 项目 | 值 |
|---|---|
| 文件 | `spiderqueen_hungrycraft-0.0.1-dev.jar` |
| 大小 | 6 744.5 KB |
| 构建时间 | 2026-10-10 **08:20:12**（实例内副本） |
| SHA-256 | `1AB301DE43BA874B0CB74076D8AEC4161C73F5D890B789CD62E2E74C2E7B3EED` |
| 部署位置 | `D:\我的世界测试版\2\versions\1.20.1-Forge_47.4.23\mods\` |
| 源码产物一致性 | 与 `build\libs\` 内同名 jar **哈希完全相同**（`1AB301DE…3EED`） |
| 构建链 | `gradlew build`（含 `reobfJar`）→ `gradlew deployIceAndFireTest`，均 BUILD SUCCESSFUL |

## 13. 下一轮建议

1. **多段碰撞箱 + 视觉尺寸校准**：先固定 5 个 stage 的渲染缩放，再把多个命中盒挂到骨龙骨骼上（头/身/尾/翼），否则"看着大、打不中"会持续存在。
2. **正式骑乘**：模型已经是 I&F 骨龙，骑乘座椅点应对齐骨架背部骨骼坐标（可在 `SoulfireIafDragonRenderer` 里读骨骼位置来调）。
3. **吐息与原版模型对齐**：`getMuzzlePosition()` 目前按 `bbHeight * 0.72` 估算，建议改为读取头骨的世界坐标。
4. **非正常路径的可见告警**：把 `[Soulfire7F] FAILED` 同时做成玩家可见消息（一次性），避免"看起来是方块龙但没人看日志"。
5. **I&F 缺失时的开发期守护**：考虑在 dev 环境下显式断言"不应加载 Tabula 类"，把本轮修好的映射问题锁死。
6. 若要长期维护，建议评估**给 `libs/` 的两个 jar 加 `fg.deobf`**：这是 ForgeGradle 的标准做法，可让 `extends AdvancedEntityModel` 这类写法在本工程直接成立（本轮为遵守"不改工程既有构建约定、只修适配代码"的要求，选择了委托方案，代价是多一层转发）。
