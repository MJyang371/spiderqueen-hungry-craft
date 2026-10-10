# ROUND 7E — 阶段 A + 阶段 B（契约安全 / 生存 / 飞行与命令仲裁）

> 承接 `ROUND7D_SOULFIRE_RENDER_FIX.md`。按本轮指令，**只做阶段 A 与阶段 B**，完成后停下等待玩家验证；
> 模型（C）、多段命中箱（D）、吐息视觉（E）**尚未开始**，骑乘仍未实现。

## 0. 本轮记录的开始状态

| 项目 | 值 |
|---|---|
| 仓库 HEAD | `34a97714bef7a5a130461d737e73f5a3629dc978`（2026-09-13） |
| 未提交改动 | 338 项（未做任何破坏性 git 操作，未新建 stash） |
| 构建 | Minecraft 1.20.1 / Forge **47.4.10**（开发）· 实例 **47.4.23** / mod `0.0.1-dev` |
| 环境依赖 | Ice and Fire **2.1.13-1.20.1-beta-5**、Citadel **2.6.3-1.20.1**（本地 jar 为 API 真值） |
| 备份 | `D:\MinecraftModding\_backups\round7e_before\`（11 个文件：soulfire 全包 + 模型/渲染器/探针） |

## 1. 阶段 A：生存与契约安全

### A1 摔落免疫 — `SoulfireDragonEntity#causeFallDamage`
覆盖 `causeFallDamage(...)` 返回 `false` 并 `resetFallDistance()`。
**没有**关闭碰撞、**没有**瞬移、重力照旧作用于未飞行的单位、普通落地效果（方块 `stepOn` 的声音与粒子）全部保留。
另外在 `setFlying/setHovering` 的状态切换与 `finishLanding()` 里清 `fallDistance`，避免"带着高度落地"的账。

### A2 火焰与熔岩免疫 — `HungryCraftEntities.SOULFIRE_DRAGON`
注册时加 `.fireImmune()`：只免疫火焰类伤害（火、燃烧、熔岩），**非火焰魔法/物理/虚空照旧命中**（测试里有对照组）。

### A3 原始龙骨绝不丢失 — 新增**持久化契约台账**
新增三个文件：

| 文件 | 作用 |
|---|---|
| `soulfire/SoulfireContracts.java` | `SavedData`（存于主世界）：每条契约携带 origin/stage/**完整 NBT**/原始 UUID/维度/坐标/朝向/状态。状态 `ACTIVE → PENDING → DONE` |
| `soulfire/SoulfireFaults.java` | **故障注入缝**：`failNextSpawns(n)` / `failNextRestores(n)`，生产环境恒为 0，仅供测试 |
| `soulfire/SoulfireContractEvents.java` | 服务端 tick 钩子：每 600 tick 重试所有 PENDING 契约 |

`SoulfireRising` 被重写为**先记账、再交换**：
1. `raise()`：先把契约写入台账（**此时遗骸同时存在于新单位与磁盘**）→ 再 `handOverCorpse(...)` **确认**新单位真被世界接受 → **才** `corpse.discard()`；被拒绝则撤销台账记录、遗骸**原封不动**。
2. `release(dragon, removeUnit, why)`：先写 PENDING（承诺落盘）→ 再移除单位 → 再尝试恢复；恢复失败**不丢记录**，由 600 tick 一次的 sweep 重试（磁盘上永久保留）。
3. **重复生成防护**：恢复前先查"原始 UUID 是否已站在世界里"，已存在则直接判定 DONE。
4. `onDeath`：死亡也走同一条释放路径（玩家不会因为打输一场而丢掉骨龙）；`die()` 中先归还再让基类死亡。
5. **高空安全落点**：`safeSpot()` 向下最多 48 格找带地板的空位，契约在空中结束时不会把骨龙从天上丢下去。
6. 无 I&F 时：**不静默丢弃**——记录留在台账，等 I&F 回来再落地。

## 2. 阶段 B：飞行状态机与命令仲裁

### B1 消除无限爬升
原来 `errand()` 每 tick 用 `max(目的Y, 当前Y)+2` 重算目标高度 —— 这是**梯子**，长距离 MOVE 会一路爬升。
现在 `cruiseHeight()` **每条命令只决定一次**（起飞或首次飞行时），记在 goal 的 `cruiseY`，随 `forget()` 在换命令时重置；
途中遇到障碍由飞行执行器唯一的临时绕行点处理，绕完回到同一条直线；抵达水平范围后才受控下降。

### B2 所有命令统一仲裁
- `acceptCommand()` 现在**同时 `stopFlight()` 并清 `landing`**：旧航点、旧绕行点、旧飞行版本全部作废。
- `cycleOrder()` 之前**不递增版本**（只改模式）—— 这正是"FOLLOW 生效但旧 MOVE 仍在驱动飞行"的成因，已修。
- 新增实体级入口 `followOrder()` / `huntOrder()`；`SoulfireOrders.followHere/freeAttack/cycleOrder(混合组)` 全部改走它们，不再直接 `setOrderMode`。
- 指令清单全部经过闸门：moveTo / setForcedTarget / breatheAt / holdAt / followHere / freeAttack / cycleOrder / rally。

### B3 空中绝不写地面寻路
新增 `MoveState { GROUNDED, WALKING, FLYING, LANDING, HOVERING }` 与 **唯一** 的落地桥 `landTo()`；
地面导航只能经 `requestWalkTo()` 写入，**在空中返回 false 且什么都不写**；
goal 的 `walkTo(target, version)` 在空中改为 `landTo(landingSpot)`，落地后（`onGround()`）才交回导航。
`stand()` 也统一走同一套下降。

### B4 重载后状态一致
存档新增 `Flying / Hovering / Landing / NoGravity`；读档时按标志重建重力（不恢复任何旧航点），并 `resetFallDistance()`。
再加**自愈**：`!isFlying && !isHovering && isNoGravity()` → 当 tick 关掉无重力，杜绝"悬在空中不飞不落"。

## 3. 验证（本轮实际执行过的东西）

### 3.1 GameTest：`soulfire_dragon` 批量 **14/14 通过**（168 项总量中的本批量）
新增 9 条，覆盖你列出的最小场景：

| 场景 | 测试 |
|---|---|
| 1 高空落地不掉血 | `aunitthatfallsoutoftheskykeepsallitshearts`（直接问规则 + 5×5 竖井 40 格真实坠落） |
| 2 火焰/熔岩免疫 | `fireandlavacannotburnaunitofsoulfire`（含"普通伤害仍生效"对照组） |
| 6 生成失败不丢龙骨 | `arefusedspawnleavestheremainsuntouched`（注入失败 + 成功对照） |
| 7 恢复失败不丢身份 | `arefusedrestorekeepstheidentityoftheremains`（原始 UUID/种类/阶段/整份 NBT）+ 重试仍不丢 |
| 8 死亡归还流程 | `thedeathofaunithandstheremainsback` |
| 15 无 I&F 环境 | `withouttheothermodnothingisraisedandnothingislost` |
| 3 远距离 MOVE 不无限升高 | `alongflyoverdoesnotclimbforever`（140 tick 上限 25 格） |
| 4 飞行中换命令取消旧目标 | `anewordercancelstheflightoftheoldone`（FOLLOW 与驻守键两条） |
| 5 空中存读不卡无重力 | `aunitsavedintheaircomesbackwithsensiblegravity` |

> 同一轮里**与本轮无关**的旧失败有 5 条（`identityandpopulation`、`meleeswingsofeveryhumanoidbodyreallyremovehealth`、
> `territorycore_stopsthenaturalsurfacespawner`、`packaggrobothwaysandnofriendlyfire`、
> `aswingistheswingofthegameanditmovestheweaponarm`），属于人类/Sweatlord 家族，在本机是已知的时序类不稳定。
> **168 项全量未跑到结束**（确认 soulfire 批量通过后即终止），这一点如实标注。

### 3.2 真实客户端 + 真实集成服务端（`-Dspiderqueen.soulfire.hazards=true`）
在玩家实例（含 I&F + Citadel）直接进 `新的世界 (1)`，探针在**集成服务端**上放置并测量：

```
[round7E] hazard probe: a unit was dropped from 30 blocks with 100.0 of 100.0 health
[round7E] hazard probe: the dropped unit landed at y=63 with 100.0 of 100.0 health (it started with 100.0) - noGravity=false
[round7E] hazard probe: fireImmune=true health 100.0 -> fire 100.0 -> lava 100.0 -> ordinary blow 94.0
[round7E] hazard probe: a unit was sent on a MOVE order 90 blocks away from y=69
[round7E] hazard probe: after 140 ticks of the errand the unit is at y=74 (started 69, highest 74) - climbed 5 blocks
[round7E] hazard probe: the errand was steering=true ; after a new order: version 1->2 , errand gone=true , flight still steering=false , order=FOLLOW
```

- **30 格坠落 → 100/100 血**（A1，真实服务端物理）
- **火 100 → 熔岩 100 → 普通伤害 94**（A2，且免疫不是无敌）
- **90 格 MOVE、140 tick 仅升高 5 格**（B1）
- **换命令：版本 1→2、旧 errand 消失、旧飞行不再转向**（B2）

## 4. 未完成 / 未验证（如实标注）

- **阶段 C、D、E 未开始**：I&F 原版骨龙模型与动画、阶段缩放与命中范围、多段命中箱、吐息喷射视觉，全部未动。
- **骑乘仍未实现**（右键 `PASS`、`canAddPassenger` false），是本轮范围之外。
- 阶段 B3 的"落地→再走"只在 GameTest 与探针中验证了状态与规则；**长距离遇障绕行、复杂地形下的手感需要你在游戏里确认**。
- 168 项全量 GameTest 未跑到结束；同轮 5 条旧失败与本轮无关但也未修。
- `SoulfireHazardProbe` / `SoulfireRenderProbe` 是开发探针（默认关闭，`-D` 开关），如需剔除可整类删除。
