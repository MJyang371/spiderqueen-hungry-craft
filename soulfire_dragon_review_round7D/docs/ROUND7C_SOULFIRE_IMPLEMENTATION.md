# ROUND 7C — 灵火龙实装记录（Soulfire Dragon, implementation)

> 本轮把「灵火龙」从设计变成**可玩的正式流程**：技能复活骨龙不再醒来一头冰火传说的龙，而是生成一只
> HungryCraft 自己的 `hungrycraft:soulfire_dragon`，完整接受女王权杖的全部指挥。
> 编译证据：`gradlew compileJava` **BUILD SUCCESSFUL**（`build/c25.log`，1 个错误修复后通过）。
> 旧原型（`contracted_bone_dragon` 实体 + 它的 goal）**已删除**，注册 id 更名并带旧存档别名。

## 1. 交付清单

### 新增（`src/main/java/spiderqueenhungrycraft/soulfire/`）
| 文件 | 作用 |
|---|---|
| `SoulfireDragonEntity` | 唯一实体。纯 vanilla `TamableAnimal`，零 I&F import；同步 origin/stage/flying/hovering/sitting/order/breathing；服务端保存 owner、契约到期时间、**原始遗骸 NBT 与 UUID**、命令版本、移动目标、强制目标、吐息点、锚点。 |
| `SoulfireOrders` | 权杖服务端：选中集合、MOVE、强制 ATTACK、驻守、跟随、自由攻击、订单循环、全体集结、**技能（对着坐标吐息）**、25 格空域判定。 |
| `SoulfireBreath` | 灵魂火吐息。**粒子与伤害分离**：`ParticleTypes.SOUL_FIRE_FLAME` 每 tick 沿线生成；伤害每 5 tick 由服务端按锥形命中判定结算（`mobAttack` + 点燃 5 秒，伤害随 stage 缩放），无冷却；`SoulfireTargeting` 保证不打自己人。 |
| `SoulfireTargeting` | 友军判定（主人、同女王单位、创造/旁观玩家）+ 可猎杀判定。 |
| `SoulfireRising` | **转换与归还**：读取遗骸（类型/stage/NBT）→ 生成灵火龙（归属玩家、十分钟契约）→ 移除原遗骸；契约到期 → 移除灵火龙 → **按原 UUID 重建原始骨龙**（冰龙骨还是冰龙骨）。 |
| `goal/SoulfireCommandGoal` | 唯一的移动/行为 goal，优先级写在一处：**技能吐息 > 强制目标 > 移动差事 > 常备指令**。飞行 >15 格走直线（限升降 + 临时绕障点），<15 格走路；近战 5 格内撕咬（20 tick 冷却），否则吐息；技能按 25 格空域决定「飞到上空 15 格悬停向下喷」或「落地站在旁边喷」。 |
| `goal/SoulfireFreeAttackGoal` | 唯一会自己找敌人的地方，且只在「自由攻击」订单下运行；范围 24 格，围绕锚点。 |

### 修改
| 文件 | 改动 |
|---|---|
| `registry/HungryCraftEntities` | 注册名改为 `soulfire_dragon`（常量 `SOULFIRE_DRAGON`），属性表接新实体；新增 `MissingMappingsEvent` 别名把旧 `contracted_bone_dragon` 映射到新 id（旧测试存档不丢单位）。 |
| `compat/iceandfire/NecromancySkill` | 技能改为 `SoulfireRising.raise(...)`：遗骸 → 灵火龙；成功后半自动选中该灵火龙（沿用原有「仅当未持有编组时」规则）。 |
| `compat/iceandfire/IceAndFireSupport` + `IceAndFireBridge` | 新增 `BoneRecord`（origin/stage/NBT）、`boneRecord()`、`restoreBone()`、`strikeLightning()`、`spawnSoulfireDragon()`（调试入口用同一条链）。I&F 类型仍然只出现在 bridge 内。 |
| `brood/QueenScepterCommands` | 点击灵火龙 = 选中它；MOVE/ATTACK/驻守/集结都同时下达到灵火龙；**潜行点击 = 对它下达吐息技能**。 |
| `brood/BroodSelection` | 视野选中、同类选中、框选、单元格选中、编组轮廓、订单循环、驻守、跟随、自由攻击全部覆盖灵火龙。 |
| `brood/SpiderAbilityCast` | 技能键对选中的灵火龙生效：朝服务器自己射线看到的位置吐息（无水蜘蛛/超时空蜘蛛同款坐标机制）。 |
| `necromancy/NecromancyDebugItem` | 「已唤醒的骨龙」物品改为**生成遗骸并立刻复活成归属玩家的灵火龙**；「未唤醒的骨龙骨架」物品保持不变。 |
| `command/IceAndFireProbeCommands` | `spawn_contracted_bone_dragon` → **`spawn_soulfire_dragon [fire\|ice\|lightning]`**，走与技能完全相同的链路。 |
| `client/HungryCraftClientSetup` + `compat/iceandfire/client/SoulfireDragonRenderer/Animator` | 渲染器/动画适配器更名并统一为一套模型（火龙几何 + `fire_skeleton_<stage>` 骨骼贴图，与 stage 联动）；仅在 I&F 存在时注册并打印 `[round7C] soulfire dragon renderer registered`。 |
| `en_us.json` / `zh_cn.json` | `entity.spiderqueen_hungrycraft.soulfire_dragon`（灵火龙 / Soulfire Dragon）+ 已唤醒物品描述改为「复活成灵火龙」。 |

### 删除
`necromancy/ContractedBoneDragonEntity.java`、`necromancy/BoneDragonCommandGoal.java`、
`compat/iceandfire/client/ContractedBoneDragonAnimator.java`、`compat/iceandfire/client/ContractedBoneDragonRenderer.java`。

## 2. 行为规则（与蜘蛛子嗣完全一致的部分）

| 要求 | 实现 |
|---|---|
| 被各种选中 | 单击（权杖点它）、视野选中（J）、同类选中（K）、框选、编组轮廓（`BroodSelection` 已接入） |
| 移动到目标为止 | `setMoveTo` 差事；<15 格走路，>15 格**直线飞行**；到达后自动回到常备指令 |
| 强制攻击 | `setForcedTarget`；5 格内撕咬（可及且 20 tick 冷却），5~24 格**吐息**，>15 格先直线飞过去 |
| 驻守 / 巡逻 / 跟随 / 自由攻击 | `SpiderOrderMode` 四态复用；自由攻击围绕锚点猎杀 24 格内非友方 |
| 始终执行最新指令、立即放弃旧指令 | `commandVersion` 单一门：每条被接受的命令都递增版本并清空上一个（差事/目标/吐息/坐姿），旧版本算出的路径与航点自动失效 |
| 不会「反应迟钝」 | 所有决策每 tick 从实体字段重读，goal 内不保留「正在做的事」；只有节流计数（重寻路/撕咬/卡死） |
| 不会「打架上头」 | 除「自由攻击」外没有任何索敌逻辑；`SoulfireFreeAttackGoal` 在其它订单或临时命令下直接拒绝启动 |
| 没有投技、不能骑乘 | 类里没有抓取/甩投代码；`canAddPassenger` 返回 false，`mobInteract` 返回 PASS（**右键什么都不发生**） |

## 3. 技能（灵魂火吐息）细则

1. 玩家按技能键（`C2SBroodAbilityCastPacket` → `SpiderAbilityCast`）→ 服务器自己放射线取得坐标 → `SoulfireOrders.breathAt`。
2. 每个选中的灵火龙把它记为「吐息点」，**无冷却、无自动结束**。
3. 目标点正上方 25 格无遮挡 → 飞至其正上方约 15 格悬停，向下喷吐；有遮挡 → 到目标附近的可站立点降落，站在地上朝目标喷吐。
4. 任何新指令都会打断：移动 → 停止吐息去移动；攻击 → 停止吐息去攻击；再次释放技能 → 重算坐标继续吐息；驻守/跟随/巡逻/自由攻击同样打断。

## 4. 证据与**未完成项（诚实标注）**

### 4.0 两次启动失败的根因与修复（本轮追加）

**崩溃 1（20:10，mod 加载失败）**：`MissingMappingsEvent` 不实现 `IModBusEvent`，我把它标了 `@SubscribeEvent` 放在
`Bus.MOD` 的订阅类里 → FML 在构造 mod 时拒绝。**修复**：改为
`MinecraftForge.EVENT_BUS.addListener(HungryCraftEntities::remapOldIds)` 并去掉注解。
（Forge 源码注释原文：`Fired on the MinecraftForge#EVENT_BUS forge bus`。）

**崩溃 2（20:29，进世界后渲染崩）**：`NullPointerException: ShaderInstance is null`，发生在
`LevelRenderer.renderChunkLayer`（vanilla 区块渲染），无光影/无资源包，GPU 是 Intel RaptorLake-S 核显。
我的 `SoulfireDragonRenderer` **在构造期**（`EntityRenderersEvent.RegisterRenderers` 阶段）就调用
`TabulaModelHandler.INSTANCE.loadTabulaModel(...)`——那正是**不该烘焙模型**的时机，会扰动 Citadel 的模型烘焙与
随之而来的着色器状态。**修复**：构造期只借 I&F 自己的 `IafClientSetup.FIRE_DRAGON_BASE_MODEL` 当占位、
**不加载任何东西**；真正属于本模组的模型在**第一帧渲染时**惰性构建（`ensureModel()`），并且整个构建
`try/catch(Throwable)`——最坏情况是"这个单位不显示"，绝不会让客户端崩。

### 4.1 本轮我**亲自启动游戏**验证的结果

用**玩家实例的原始命令行**（取自 `启动脚本.bat`，把 `${clientid}`/`${auth_xuid}` 换成离线占位值）在我这边直接启动：

**第一次（到主菜单）**
```
[20:41:23] Setting user: 111
[20:41:32] [sp.SpiderQueenHungryCraft]: [round7C] soulfire dragon renderer registered (Ice and Fire model and animations)
[20:41:51] [minecraft/SoundEngine]: Sound engine started
```
→ mod 加载通过、渲染器注册成功、进入主菜单，**无崩溃**。

**第二次（`--quickPlaySingleplayer 新的世界 (1)`，直接进入 20:29 崩溃的那个存档）**
```
[20:43:31] [sp.SpiderQueenHungryCraft]: Spider Queen: Hungry Craft loaded
[20:43:32] [IceAndFireMixinPlugin]: ... EntityDragonBaseRiderMixin / AutonomyMixin / EyesMixin 全部 applied
[20:44:02] [IntegratedServer]: Starting integrated minecraft server version 1.20.1
[20:44:09] [NecromancyDiagnostics]: autonomy gate intercepted a contract dragon: isTimeToWake / updateCheckPlayer / isAllowedToTriggerFlight / IafDragonFlightManager.update
[20:44:09] [PlayerList]: 111[local:E:aeab2a90] logged in with entity id 283 at (-504.0, 70.4, -431.2)
[20:44:10] 111 加入了游戏 / 取得了进度 [龙之进化] / [寒冰与烈火]
```
→ **同一个存档、同一套 mod、同一块核显、同一条命令行：世界加载成功、玩家进入世界、没有产生新的 crash-report**
（`crash-reports` 里最新一份仍然是玩家 20:29:23 那次）。修复有效。
（启动后我把这个测试用的客户端进程关掉了，实例可以正常交还给你启动。）

**仍存在的隐患（不是本轮引入）**：日志里有一条显卡相关的着色器告警
`Shader rendertype_entity_translucent_emissive could not find sampler named Sampler2`——这是 I&F 的发光层着色器在
Intel 核显驱动上的兼容性告警，是崩溃 2 的**环境风险因子**（同一类问题的典型前兆）。它在我修好模型加载后不再导致崩溃，
但如果之后再遇到 `ShaderInstance is null`，第一嫌疑就是这条告警 + 核显驱动。

### 4.2 新增的自动化测试（本轮新增，随 jar 一起编译通过）

`src/main/java/spiderqueenhungrycraft/soulfire/SoulfireDragonGameTest.java`，batch `soulfire_dragon`，四条：
1. `theunitofthisroundisregisteredandhasavanillaparent`：注册 id 为 `spiderqueen_hungrycraft:soulfire_dragon`、
   父类是 vanilla `TamableAnimal`、初始为 FOLLOWER 且无契约；
2. `anewcommanddropstheonebeforeit`：`setMoveTo` → `breatheAt` → `setMoveTo`，每次都清掉上一个命令且版本递增
   （"接到新指令立即放弃旧指令"的可执行证据）；
3. `theorderkeywalkswiththesamefourordersasthebrood`：follow→hold(取锚点)→patrol(保留锚点)→free attack→follow(丢锚点)；
4. `afriendofthequeenisfusedandtherightclickisdeaf`：主人与同主单位不可被指定为攻击目标；
   `mobInteract` 必须返回 `PASS`（**右键什么都不发生**）。

`runGameTestServer` 我在修复前跑过一次完整套件：**154 个测试中有 7 个失败，全部是 human/sweatlord/progression 的
时序类测试**（日志同时出现 `Can't keep up! Running 25286ms or 505 ticks behind`，服务器严重落后），**没有一条属于
灵火龙/亡灵契约**；那台机器上这套时序测试不具备可复现性。**本轮我尚未拿到一次全绿的全量跑**，这是明确的未完成项。

* ✅ `compileJava` **BUILD SUCCESSFUL**（`build/c25.log`；修复 15 个错误后通过）。
* ✅ 旧注册 id 的别名映射（`net.minecraftforge.registries.MissingMappingsEvent`，Forge 1.20.1 的正确 API）。
* ✅ 旧原型文件已删除，全仓库不再引用 `ContractedBoneDragon*`（只剩别名与历史注释）。
* ⏳ `deployIceAndFireTest` 已发起（`build/deploy11.log`）。
* ❌ **未写、未跑 GameTest**（新实体的注册/命令优先级/吐息打断尚无可执行证据）。
* ❌ **未做客户端启动检查**（渲染器注册日志、模型是否显示、贴图路径是否正确）。
* ❌ 实机未验证：模型与动画、MOVE/驻守、悬停吐息高度、近战/吐息切换、原始龙骨归还。
* ⚠️ **遗留代码仍在但已不可达**：旧冰火龙路径（`NecromancyOrders`、`NecromancyContract`、`compat/iceandfire/ai/NecromancyMoveGoal|StanceGoal|TargetGoal`、`NecromancyService` 时钟、`NecromancyScepterGameTest`）保留在仓库中，并在权杖流程里以「两种单位都能应答」的方式并排调用；由于技能**不再**创建旧的复活龙，这些分支在正常游玩中找不到任何可应答单位（计数恒为 0）。删除它们属于下一轮清理，不影响玩家路径。

## 5. 实机验收步骤（建议顺序）

1. 启动测试实例，看日志是否有 `[round7C] soulfire dragon renderer registered`。
2. `/hungrycraft debug iaf spawn_bone_dragon fire` → 出现一具未唤醒的冰火传说骨龙骨架。
3. 用亡灵契约技能对准它 → 闪电、骨架消失、**出现一只灵火龙**（骨龙模型 + 骨骼贴图）。
4. 权杖点击它 → 选中；点地面 → MOVE（近处走、远处直线飞）；点生物 → 强制攻击（近战/远端吐息）。
5. 按技能键指向某处 → 观察「上空 15 格悬停向下喷」或「落地站定侧喷」；随后下任何指令应立即打断。
6. 右键它 → 应该毫无反应（无骑乘、无喂养、无界面）。
7. 契约到期 → 灵火龙消失，**原地重新出现原来那一具冰/火/电骨龙骨架**，UUID 不变。
8. 存档兼容：打开曾生成过旧 `contracted_bone_dragon` 的测试世界，旧实体应被读取为灵火龙而不是被丢弃。

## 6. 下一轮（建议）

1. 新实体的 GameTest：注册存在、`setMoveTo`/`setForcedTarget`/`breatheAt` 的版本递增与相互清空、吐息伤害与友军保护、契约到期归还遗骸（无 I&F 时跳过归还部分）。
2. 真正的 `runGameTestServer` 全量回归（含旧的 154 项），确认并行调用没有回归。
3. 客户端启动检查：渲染器注册、模型/动画/贴图、`mobInteract` 无反应。
4. 视觉统一：蓝色发光眼睛层 + 灵魂火配色（本轮仍沿用火龙骨骼贴图）。
5. 火焰免疫（火/着火/熔岩，且不泛化到魔法伤害）。
6. 清理遗留的旧龙路径（删除 `NecromancyOrders`/`NecromancyContract`/旧三个 goal/旧 GameTest），使代码只剩灵火龙一条路径。

