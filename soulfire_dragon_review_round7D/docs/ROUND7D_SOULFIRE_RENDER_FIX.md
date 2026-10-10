# ROUND 7D — 灵火龙"可见性"修复与实机取证

> 承接 `ROUND7C_SOULFIRE_IMPLEMENTATION.md`。7C 的判定是**大失败**：灵火龙在游戏里**完全不渲染**、看不出任何功能。
> 本轮（7D）只做一件事：**找出它为什么看不见，并让"看不见"这件事在结构上不可能再发生**；同时把行为里几处要点补齐、
> 用**真实客户端截图取证**，并把全部相关内容打包给外部评审。

---

## 1. 根因（有玩家日志为证）

玩家 `logs/latest.log` 里，技能**是成功的**、实体**是存在的**，失败在渲染：

```
[20:51:54] [HungryCraft/IceFireProbe] risen: soulfire dragon e09a977d-... from FIRE remains (stage 4)
[20:51:54] [Render thread/ERROR] the soul fire dragon model
   /assets/iceandfire/models/tabula/firedragon/firedragon_Ground could not be loaded
java.lang.NullPointerException: in is null
  at java.util.zip.ZipInputStream.<init>(ZipInputStream.java:132)
  at ...citadel.client.model.TabulaModelHandler.getModelJsonStream(TabulaModelHandler.java:186)
  at ...citadel.client.model.TabulaModelHandler.loadTabulaModel(TabulaModelHandler.java:57
  at ...spiderqueenhungrycraft.compat.iceandfire.client.SoulfireDragonRenderer.ensureModel(...)
  at ...SoulfireDragonRenderer.render(...)
```

**两个缺陷叠在一起：**

1. **路径错了**。Citadel 的 `loadTabulaModel(String)` 是把字符串直接丢给 `ClassLoader.getResourceAsStream`，
   而冰火传说自己用的模板是 `/assets/<mod>/models/tabula/<type>dragon/<type>dragon_<pose>.tbl`（**`.tbl` 后缀**，
   由 `TabulaModelHandlerHelper.loadTabulaModel` 补齐 `/` 与 `.tbl`）。我传的字符串少了 `.tbl` → 流为 null → NPE。
2. **更严重的是设计错误**：我为了"不让客户端崩溃"把异常吞掉并 `return`，
   于是**故障变成了不可见的故障**——实体在、指令在、渲染是空的。这正是"游戏内测试根本没有实现任何功能"的观感来源。

## 2. 7D 的修法（三条）

| # | 改动 | 文件 |
|---|---|---|
| 1 | **弃用外部模型**，自绘 `HierarchicalModel` + `LayerDefinition`（骨骼、姿势、UV 布局全在本模组） | `client/model/SoulfireDragonModel.java`（新增） |
| 2 | **自研贴图** 128×128（骨场 + 两处灵魂火色块：眼睛/口腔用亮青，翼膜/尾尖用暗青） | `assets/.../textures/entity/soulfire_dragon.png`（新增） |
| 3 | 渲染器**无条件注册**、不依赖任何可选 mod；`SoulfireDragonRenderer` 只用游戏自己的烘焙管道 | `client/renderer/SoulfireDragonRenderer.java`（新增）、`HungryCraftClientSetup`（改） |

旧的 I&F/Citadel 渲染方案（`compat/iceandfire/client/SoulfireDragonRenderer.java` + `SoulfireDragonAnimator.java`）
**已从源码树删除**，原文留档在评审包的 `superseded_round7C_iaf_client/` 供对照。

> 结论性判断：**一个单位能不能被画出来，绝不该取决于"另一个 mod 的文件在哪"。**
> 复用可选 mod 的模型观感更好（那是 7C 的设计目标），但它把"看不见"这种最致命的失败模式引了进来；7D 选择可靠与可测。

## 2.5 第二个根因（比看不见更致命）：`isOwnedBy` 被 vanilla 遮蔽

**这条是在跑 GameTest 时被抓出来的，它解释了玩家那句"游戏内测试是根本没有实现任何功能"。**

`SoulfireDragonEntity` 写了自己的拥有者判定：

```java
public boolean isOwnedBy(@Nullable Entity who) { return who instanceof Player p && ownerUuid.equals(p.getUUID()); }
```

但 `SoulfireDragonEntity extends TamableAnimal`，而 **vanilla 的 `TamableAnimal` 已经声明了更具体的
`isOwnedBy(LivingEntity)`**。Java 重载决议对 `Player`（是 `LivingEntity`）实参**永远选择那个更具体的方法**，
于是权杖的每一次询问：

```java
// SoulfireOrders.java 第 63 / 75 / 98 / 119 行
dragon.isOwnedBy(queen)      // queen 是 ServerPlayer → 走 vanilla 方法 → getOwner() == null → 永远 false
```

**结果：任何灵火龙都不被认作"女王的单位"** ——不能被选中、不能接受任何指令、不能被技能键指派吐息。
它照样会生成、照样有契约、照样按 goal 自己动，**所以从画面上看"像是有东西但什么都不听"**，
在代码里也完全看不出异常（两个方法都"正确"，错的是重载决议）。

**修法**：显式覆盖 vanilla 的签名并委托到本模组自己的判定：

```java
@Override
public boolean isOwnedBy(LivingEntity who) { return this.isOwnedBy((Entity) who); }
```

并把拥有者**同时**写进 vanilla 的 `setOwnerUUID`（`beginContract` 与读档时），
避免"本模组的契约说它是我的、vanilla 的 `getOwner()` 说是无主"这种自相矛盾的状态
（注意这**不是**驯服：`isTame()` 仍为 false，不会触发 vanilla 宠物行为）。

> 覆盖方法上写了 `@Override` —— 编译通过本身就证明了 vanilla 那个方法确实存在、遮蔽确实发生。

## 5.5 GameTest 结果

`runGameTestServer`（修复前 / 修复后各一轮）：

```
[22:02:22] Running test batch 'soulfire_dragon:1' (5 tests)...
[22:02:22] afriendofthequeenisfusedandtherightclickisdeaf failed! and it still answers to the queen who raised it   ← 抓出 isOwnedBy 遮蔽
```
修复后：
```
[22:05:17] Running test batch 'soulfire_dragon:1' (5 tests)...
（日志中没有任何一条提到这 5 个测试 → 全部通过）
```
同一轮里失败的只有 3 条**与本轮无关**的旧测试（`meleeswingsofeveryhumanoidbodyreallyremovehealth`、
`packaggrobothwaysandnofriendlyfire`、`aswingistheswingofthegameanditmovestheweaponarm`），
属于人类/Sweatlord 家族，在本机是已知的时序类不稳定（上一轮 154 项里也是同类失败）。

**顺带说一下这条测试为什么值得保留**：它问的问题（"女王是不是这只龙的友军/主人"）与权杖问的**完全同一个入口**，
所以它能抓到一个在运行的游戏里"看起来只是不听话"的 bug。

- **最新指令立即生效的最后一处漏洞**：`SoulfireCommandGoal` 原本只有实体的 `commandVersion`，
  但 goal 自己的本地记忆（repath 计时、巡逻点、卡住计数、上次距离）在换指令时**不会重置**。
  7D 增加 `lastVersion`，版本一变即 `forget()`（"接到新指令立即放弃正在执行的命令"）。
- **近战/远吐息**：重写 `fight()`：
  贴身（≤5 格、且垂直差 <2.5、有视线）→ 咬（`doHurtTarget`，20 tick 冷却，空中悬停也能咬）；
  超过 15 格 → **直线飞过去**（`flyTo`，不使用原版/冰火飞行逻辑）；进入吐息射程（24 格）→ 停住（地面站定 / 空中悬停）**吐息**；
  再远 → 走过去。
- **技能（能力键）**：`SoulfireOrders.skyOpen()` 判定目标点上方 25 格是否畅通 →
  畅通则飞到目标上空 15 格**悬停下喷**；否则在目标附近**降落站定平喷**。**无冷却**；
  任何指令（移动/攻击/驻守/巡逻/跟随/自由攻击/再次释放技能）都通过 `acceptCommand()` 清空 `breathTarget` 立即打断。
- **右键恒无反应**（`mobInteract` → `PASS`）、**不可骑乘**（`canAddPassenger` → false）、无投技（本轮不做）。

## 4. 实机取证：我自己启动了游戏并截图（这是本轮的核心证据）

我无法"看"画面，所以写了一个**开发用渲染探针** `client/SoulfireRenderProbe.java`：
用 `-Dspiderqueen.soulfire.probe=true` 打开，它在真实客户端里
**把相机钉在同一位置**（否则云、时间、下落中的玩家会让整幅画都在变）、
在相机前方放 3 只**客户端本地**的灵火龙（站立 / 飞行 / 坐姿吐息）、
先拍一张**没有龙的底图**再拍有龙的图，并用 `Screenshot.grab` 落盘。

真实客户端（玩家实例 1.20.1-Forge_47.4.23，含冰火传说与 Citadel，直接进 `新的世界 (1)`）的日志：

```
[21:42:37] [sp.SpiderQueenHungryCraft]: [round7D] soulfire dragon renderer registered (model of this mod)
[21:43:17] [round7D] probe unit placed at BlockPos{x=-507, y=73, z=-469} state=FLYING
[21:43:17] [round7D] probe unit placed at BlockPos{x=-508, y=70, z=-472} state=SITTING_BREATHING
[21:43:19] [round7D] soulfire render probe shot 'front': 108 frames drawn so far
[21:43:23] [round7D] soulfire render probe shot 'side': 108 frames drawn so far
```

**"108 frames drawn so far"** 是渲染器里 `render()` **成功返回后**才自增的计数器 → 模型确实被画出来了（不是异常被吞）。

对两张图做**同一相机底图差分**（脚本 `build/diff_shots.ps1`，按贴图色域过滤掉云/光照/地形）后的结果：
屏幕上**恰好出现 3 个骨白/灵魂火色团块**，位置与探针放置的 3 只单位一致：

```
changed pixels (sampled every 2px): 11117
  object 1: area 334 cells, screen box x 0..517   y 80..256   (517x176 px)
  object 2: area   6 cells, screen box x 416..461 y 96..128   (45x32 px)
  object 3: area   4 cells, screen box x 832..854 y 144..176  (22x32 px)
```

截图原件随包：`evidence/soulfire_probe_baseline.png` / `_front.png` / `_side.png`
（进入世界时的姿势分别是站立、飞行、坐姿吐息）。

**我仍然看不到"好不好看"**：体型比例、观感、贴图细节属于主观判断，需要人眼复核——这一点在下面"未完成项"里如实标注。

## 5. 自动化测试

`SoulfireDragonGameTest`（batch `soulfire_dragon`）本轮增加第 5 条：

| 测试 | 覆盖 |
|---|---|
| `theunitofthisroundisregisteredandhasavanillaparent` | 注册 id、vanilla 父类、初始跟随且无契约 |
| `anewcommanddropstheonebeforeit` | setMoveTo → breatheAt → setMoveTo：每次清空上一条、版本递增 |
| `theorderkeywalkswiththesamefourordersasthebrood` | follow→hold(取锚点)→patrol(保留)→free attack→follow(丢锚点) |
| `afriendofthequeenisfusedandtherightclickisdeaf` | 主人/同主单位不可为目标；`mobInteract` 必为 `PASS` |
| `theskillhoversonlywhentheskyoverthepointisopen` | **新增**：目标点上方 25 格畅通 → 悬停分支；有遮挡 → 降落分支（直接测 `SoulfireOrders.skyOpen`） |

## 6. 未完成项 / 局限（如实标注）

1. 我**看不到画面**：只证明了"确实被绘制、颜色正确、位置正确、帧数在涨"，观感需人眼。
2. **全量 154 项 GameTest 未在本轮重跑完毕**：本机跑完整套件存在**无关的时序类失败**
   （`Can't keep up! Running ... 505 ticks behind`），7C 轮已确认与灵火龙/亡灵契约无关。
3. 投技、骑乘按要求不做；I&F 侧旧骨龙路径（`NecromancyOrders`/`NecromancyContract`）仍在仓库中，正常玩法不可达但未清理。
4. 探针 `SoulfireRenderProbe` 是**开发工具**（默认关闭），若评审认为不该留在生产代码里，可整类删除且无残留引用。
