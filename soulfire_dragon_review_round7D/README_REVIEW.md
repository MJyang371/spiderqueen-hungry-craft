# 灵火龙（Soulfire Dragon）评审包 — Round 7C / 7D

> 面向外部评审：这是 HungryCraft（SpiderQueen-HungryCraft, Forge 1.20.1）中"亡灵契约 IV"技能的产物单位
> **灵火龙 `spiderqueen_hungrycraft:soulfire_dragon`** 的全部相关代码、文档与证据。
> 请重点给出**代码质量、架构合理性、性能与兼容性**方面的意见，以及"应该继续改造还是推倒重来"的判断。

---

## 1. 这个单位是什么

玩家（蜘蛛女王）用技能 **亡灵契约 IV** 对着冰火传说（Ice and Fire）的**骨龙残骸**施放：
残骸被**移除**、其完整 NBT 与身份被**存进**新单位，原地升起一只属于该玩家的**灵火龙**，契约 10 分钟。
契约到期（或单位死亡路径）时，灵火龙消失、**原样交还**那具骨龙残骸。

灵火龙是**本模组自己的实体**，父类是 vanilla `TamableAnimal`（`SoulfireDragonEntity` 内**零** I&F import），
它接受**女王权杖的全部指令**，与蜘蛛子嗣同一套词汇：选中、移动到坐标、强制攻击、驻守、巡逻、跟随、自由攻击、技能键吐息。
核心不变量：**永远执行最新指令**（单一 `commandVersion` 闸门，`acceptCommand()` 递增并清空所有临时状态）。

## 2. Round 7C → 7D 的关键变更（为什么有这一包）

**7C 在实战中判定失败**，两个可复现的缺陷：

| 缺陷 | 根因 | 7D 修法 |
|---|---|---|
| 客户端启动崩溃 1（FML 拒绝 mod） | `MissingMappingsEvent` 不是 `IModBusEvent`，被注册在 `Bus.MOD` | 改为 `MinecraftForge.EVENT_BUS.addListener(...)` |
| 客户端崩溃 2（进世界后 `ShaderInstance is null`） | 渲染器在**注册阶段**构造期就烘焙 Citadel Tabula 模型 | 构造期不加载任何资源 |
| **灵火龙完全不渲染（看不见）** | 模型路径 `/assets/iceandfire/models/tabula/firedragon/firedragon_Ground` **少了 `.tbl`**，`getResourceAsStream` 返回 null → NPE → 渲染器 `catch` 后静默 `return` | **彻底弃用外部模型**：自绘 `SoulfireDragonModel`（`HierarchicalModel` + `LayerDefinition`）+ 自研贴图，**无任何可选依赖**，随 vanilla 的 `RegisterLayerDefinitions` 一起烘焙 |

> 关键教训（也是请评审重点关注的设计问题）：**"为了不崩客户端而静默吞掉渲染异常"会让故障变成不可见故障**。
> 7D 的做法是让"画不出来"这件事**在结构上不可能发生**，而不是继续捕获异常。

## 3. 本包的代码地图

```
src/soulfire/                     单位本体与行为
  SoulfireDragonEntity.java       实体：状态、指令闸门、契约、直线飞行 executor
  SoulfireOrders.java             权杖指令入口（选中集合、移动、攻击、驻守/巡逻/跟随/自由攻击、吐息）
  SoulfireBreath.java             灵魂火吐息：粒子（可见）与伤害（服务端锥形判定）分离
  SoulfireTargeting.java          友方判定（同主永不互殴；主人/创造/旁观者不可成为目标）
  SoulfireRising.java             升起与归还（残骸 NBT 存入 / 到期原样交还）
  goal/SoulfireCommandGoal.java   唯一的行为目标：技能 > 强制目标 > 移动 > 站立命令
  goal/SoulfireFreeAttackGoal.java 唯一允许自主索敌的目标（仅"自由攻击"命令下）
  SoulfireDragonGameTest.java     5 条 GameTest
src/client/model/SoulfireDragonModel.java      自绘模型（骨骼、姿势、贴图 UV 布局）
src/client/renderer/SoulfireDragonRenderer.java 渲染器（仅此一处，无 if(某个mod存在)）
src/client/SoulfireRenderProbe.java            开发用渲染取证探针（-Dspiderqueen.soulfire.probe=true）
src/resources/texture/soulfire_dragon.png      128×128 自研贴图（骨骼 + 两处灵魂火色块）
superseded_round7C_iaf_client/                 7C 的 I&F/Citadel 渲染方案（已删除，留档对照）
integration/                                   接线点摘录：选中、权杖、技能键、注册表、语言文件
docs/                                          7C 设计/实现文档
```

## 5. 本轮（7D）实机取证

`evidence/` 里的三张图是**真实客户端**（玩家实例 1.20.1-Forge_47.4.23 + 冰火传说 + Citadel）用
`-Dspiderqueen.soulfire.probe=true` 打开渲染探针后拍的：相机被钉死在同一位置，先拍**无龙底图**，
再在相机前方放 3 只**客户端本地**的灵火龙（站立 / 飞行 / 坐姿吐息）后拍 `front`，最后转 40° 拍 `side`。

- 日志证据：`[round7D] soulfire render probe shot 'front': 108 frames drawn so far`
  ——该计数只在 `Renderer.render()` **成功返回之后**自增，即模型确实被画进了帧。
- `evidence/DIFF_REPORT.txt` 是同相机底图差分（按贴图色域过滤云/光照/地形）的结果：
  `object 1: 517x176 px`（左侧，含翼膜的大块）、`object 2: 45x32 px`（中上，飞行的那只）、
  `object 3: 22x32 px`（右侧）——**恰好 3 个团块，与放置的 3 只一致**。
- 复现工具：`evidence/tools/`（`diff_shots.ps1` 差分、`ascii_view.ps1` 像素分类、`make_soulfire_texture.ps1` 贴图生成）。

> **我（AI）看不到画面**：以上证明的是"确实被绘制、颜色与位置正确、帧数在涨"；
> 体型比例与观感需要人眼复核，这是本包希望外部评审补上的部分。

## 6. 请评审时特别留意的设计点

1. **`commandVersion` 单闸门**：所有指令共用一条版本号，`tick()` 中版本变化即 `forget()` 丢弃全部路径/节奏状态。
   请评估这是否足以消灭"反应迟钝/打架上头"，以及是否该引入真正的行为树/状态机。
2. **`SoulfireCommandGoal` 是"唯一行为目标"**（独占 MOVE/LOOK flag），而非多个 goal 竞争。
   这是为"最新指令必胜"服务的取舍，请评估其可读性与扩展性代价。
3. **直线飞行**：`travelAirborne()` 直接写 `deltaMovement` 并 `move(MoverType.SELF, ...)`，不使用 vanilla 寻路、
   不使用 I&F 飞行管理器；碰撞时临时横向绕行（`detourWaypoint`，60 tick 上限）。
   请评估其与 vanilla 碰撞/区块加载的健壮性，以及是否需要 `MoveControl` 化。
4. **技能的两分支（悬停/降落）**：`SoulfireOrders.skyOpen()` 检测目标点上方 25 格是否畅通 → 飞到上方 15 格悬停下喷；
   否则落到目标附近地面平喷。无冷却，任何指令立即打断（`acceptCommand()` 清 `breathTarget`）。
5. **粒子与伤害分离**：`SoulfireBreath` 的粒子是效果，伤害由服务端锥形判定独立决定（5 tick 一次），
   `SOUL_FIRE_FLAME` 粒子，无冷却。
6. **模型自制 vs 复用**：7C 走"复用 I&F Tabula 模型 + 骨骼贴图"（LGPL 合规、观感好但脆弱）；
   7D 改为自绘（可靠、可测、无外部依赖，观感朴素）。**请给出长期建议。**

## 5. 已知未完成项 / 局限（如实标注）

- 我（AI）**无法"看"游戏画面**，本轮通过**客户端内置取证探针**在真实客户端里放置 3 只不同姿势的灵火龙并截图，
  以截图 + 渲染帧计数作为"确实被绘制"的证据；**体型比例、观感是否好看属于主观判断，需要人眼复核**。
- 完整的 154 项 GameTest 全量跑在本机存在**无关的时序类失败**（服务器 `Can't keep up! ... 505 ticks behind`）。
- 投技、骑乘按本轮要求**不做**（右键恒为 `PASS`，`canAddPassenger` 恒 false）。
- I&F 侧仍保留 `NecromancyOrders` / `NecromancyContract` 等旧骨龙代码路径（正常玩法已不可达），未清理。
