# ROUND 7C — 统一亡灵单位「灵火龙（Soulfire Dragon）」下一阶段设计

> **本文件只是设计记录。** 本轮（7B 原型）的代码**一行未改**：原型仍按 `contracted_bone_dragon` 继续开发与验证。
> 本文件把玩家 2026-10-09 的决定固定为方案 B 的**最终产品方向**，并给出迁移时机与风险评估。
> 事实依据来自运行中的 `libs\iceandfire-2.1.13-1.20.1-beta-5.jar`（`javap` + jar 条目）。

## 0. 决定（最终产品方向）

**所有通过蛛后亡灵契约复活的龙，无论原始遗骸是火龙、冰龙还是电龙，统一成为同一种 HungryCraft 独立单位：灵火龙。**

建议注册名 `hungrycraft:soulfire_dragon`（改名时机见 §7）。不再分别实现亡灵火龙 / 亡灵冰龙 / 亡灵电龙；原始龙的差异**只**影响体型、成长阶段与等级，不影响元素属性或战斗技能。

## 1. 事实核查：电龙在当前 I&F 版本是否真的存在

| 检查项 | 结果 | 证据 |
|---|---|---|
| 电龙实体类 | **存在** | `com/github/alexthe666/iceandfire/entity/EntityLightningDragon.class` 在 jar 内 |
| 注册表项 | **存在** | `IafEntityRegistry.LIGHTNING_DRAGON : RegistryObject<EntityType<EntityLightningDragon>>` |
| Tabula 模型 | **存在** | `assets/iceandfire/models/tabula/lightningdragon/lightningdragon_ground.tbl`（火龙/冰龙/电龙三者都有 `*_ground.tbl`） |
| 骨骼贴图 | **存在** | `assets/iceandfire/textures/models/lightningdragon/lightning_skeleton_1..5.png` |
| 模型类型枚举 | **存在** | `EnumDragonModelTypes.LIGHTNING_DRAGON_MODEL` |

**结论**：当前 I&F 2.1.13 **确实支持电龙**，不存在"凭空调用不存在的类"的风险。但：
- 所有涉及 `EntityLightningDragon` 的调用仍必须留在 `compat.iceandfire` 门内（无 I&F 时该类不会被加载）；
- 统一后的灵火龙**不再分支元素**，`LIGHTNING` 只出现在**原始龙骨记录**里（用于恢复），不参与模型/吐息/抗性决策。

## 2. 统一视觉

* 骨龙身体、翅膀、行走、飞行、攻击动画**保留**：直接复用 I&F 的 Tabula 模型几何 + 姿态库（`DragonAnimationsLibrary`），沿用 7B 已实现的适配器方案。
* **统一模型种类**：所有灵火龙使用**同一套**几何与姿态（建议取 `EnumDragonModelTypes.ICE_DRAGON_MODEL` 的几何，因为它与"蓝色发光"的视觉基调和冰龙眼睛层同源；具体取火/冰由原型验证后定，一旦定下不再按元素切换）。
* **统一眼睛**：所有灵火龙使用冰龙风格的**蓝色发光**眼睛层（`ice_eyes` 系列贴图或自绘发光层），不再使用火/电眼睛。
* **统一吐息视觉**：青蓝色灵魂火，粒子优先使用**原版** `net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME`（本模组已有 `level.sendParticles(ParticleTypes.…)` 的用法，见 `beast/QueenBeastBite.java`、`beast/QueenBeastController.java`）。
* **移除**：火吐息/冰吐息/电吐息的全部元素分支。

## 3. 统一战斗能力

灵火龙只有两种主动攻击：**近战** 与 **灵魂火吐息**。

* **彻底取消**：抓取投技、甩投、SHAKE_PREY、冰冻吐息、闪电吐息、原生龙的自主索敌与自主攻击（7B 原型已从架构上消除：新实体没有任何 I&F Goal，也没有 target goal）。
* **灵魂火吐息**要独立实现，**不得把粒子生成等同于伤害**：
  * 服务端：命中检测（视锥/射线 + 碰撞箱）、伤害结算（按等级缩放，量级参照火龙吐息）、点燃效果（`setSecondsOnFire`/`igniteForSeconds`）、冷却与持续时间；
  * 客户端：`SOUL_FIRE_FLAME` 粒子 + 音效（可复用 I&F 的呼吸音或原版音效）；
  * 友军保护：沿用 `isFriendlyToContract`/`QueenOwnerProtection` 的既有规则（不得烧到主人与其蛛群）；
  * 由 HungryCraft 决定何时开始、打谁、朝哪、何时停、是否切近战。
* 近战：由独立战斗控制器决定距离/时机/冷却，触发模型动作并按动画窗口结算伤害（7B 的 `BoneDragonCommandGoal` 目前只做接近，攻击部分是本阶段的下一步）。

## 4. 统一元素抗性

* 所有灵火龙获得**火焰免疫**：常规火焰伤害、着火（on fire）伤害、熔岩伤害。
  * 实现要点：实体 `EntityType` 侧 `fireImmune()`，加上伤害类型判定（`DamageTypes.IN_FIRE` / `ON_FIRE` / `LAVA` / `HOT_FLOOR`）。
* **明确不做什么**：不因火焰免疫而免疫其它魔法/一般伤害——免疫范围严格限定在"火焰系"，其它 `DamageSource` 一律照常结算。
* 需要一并明确：灵魂火自身造成的伤害对灵火龙**不生效**（自伤免疫），但仍受其它非火焰伤害影响。

## 5. 统一命令与骑乘（已在 7B 原型中成型，保持一致）

* 灵火龙是蛛后的**特殊子嗣**：参与全部现有选择（单击、框选、可见单位、批量、编组、集合）与混合单位命令；服务端统一校验归属。
* 命令优先级：**契约结束 > 玩家骑乘 > 最新有效 RTS 指令 > 空闲**；新指令替换旧任务；`commandVersion` 丢弃旧路径结果。
* 骑乘完全由 HungryCraft 自实现（`getControllingPassenger` 只认契约主人、坐背定位、无猎物分支）；**骑乘时允许玩家主动释放灵魂火吐息**。
* BOMBARD 保持接口预留，等前面稳定后再做 25 格空域检查 + 定点悬停 + 向下吐息。

## 6. 原始龙骨恢复（不可妥协的一条）

统一的是"作战单位"，**不是**"遗骸"：

```
原始冰龙骨 --契约--> 灵火龙(统一实体, 冰龙骨身份记录) --契约到期--> 原始冰龙骨
```

* 必须保存并原样恢复：**原始类型（火/冰/电）、UUID、成长阶段（size/age）、恢复所需数据**。
* **禁止**把所有原始龙骨永久转换成火龙骨（或任何统一遗骸）。
* 7B 原型已经预留了 `originalBoneUuid` 字段与 NBT 存档；正式迁移时把它扩展为完整的
  `BoneRecord{ originalUuid, originKind, stage, ageDays, savedCommand, savedHealth, owner, home, dimension }`，并以
  `originalUuid` 作为转换事务的**幂等键**（同一原始龙骨不得同时存在两个灵火龙）。

## 7. 注册名：`contracted_bone_dragon` → `soulfire_dragon` 的时机与风险评估

**当前状态**：`hungrycraft:contracted_bone_dragon` 已经注册并**已部署到测试实例**（18:50 的 jar），但它是**开发原型**：
只有开发者命令 `/hungrycraft debug iaf spawn_contracted_bone_dragon` 能生成它，它**没有**物品、没有语言键、没有进入正式契约流程、也**从未在任何发布版本里出现过**。

**风险面（只有一处）**：如果玩家在测试世界里用该命令生成过它并保存了世界，那么世界里会存在 `spiderqueen_hungrycraft:contracted_bone_dragon` 这个 id；改名后旧存档里那种实体会**无法解析**（Forge 会记录一条 missing mapping）。

**推荐方案（合并改名 + 安全别名）**，在**统一实体的那一个阶段**执行：

1. 在正式迁移轮里把注册 id 与常量改为 `SOULFIRE_DRAGON` / `"soulfire_dragon"`；
2. **同时**注册一个 Forge 别名，让旧存档里的老 id 平滑迁移：

   ```java
   @SubscribeEvent
   public static void onMissingMappings(RegistryEvent.MissingMappings<EntityType<?>> event)
   {
       for (var mapping : event.getAllMappings())
       {
           if (mapping.key.equals(new ResourceLocation(MODID, "contracted_bone_dragon")))
           {
               mapping.remap(HungryCraftEntities.SOULFIRE_DRAGON.get());   // 老存档里的实体就地变成灵火龙
           }
       }
   }
   ```
3. 改名的**全部影响面**（已核查）：`HungryCraftEntities` 的常量与 id、开发者命令里的那一处引用。**没有**语言键、没有物品、没有旧发布存档依赖它 → 改名成本极低。

**备选方案（零迁移风险）**：保留 id `contracted_bone_dragon`，只把**显示名**改为"灵火龙 / Soulfire Dragon"。实体 id 是内部标识，玩家看不到；只有在"必须让 id 与产品名一致"的洁癖需求下才值得改名。若玩家更在意零风险，采用此方案并在文档中记录 id↔名称的对应关系。

**明确不做的事**：不在 7B 原型验证完成之前改名——中途改名会同时打断"原型可验证性"与"已部署 jar 与源码一致性"。

## 8. 当前原型（7B）与最终方向的关系

**已经符合最终方向、不需要返工的部分**：

| 已实现（7B） | 为什么它已经是对的 |
|---|---|
| `ContractedBoneDragonEntity extends TamableAnimal`，类内零 I&F import | 统一后只是改 id 与"原始类型语义"，实体骨架不动 |
| 骑乘完全自实现（`getControllingPassenger` 只认契约主人、坐背、无猎物分支） | §5 的要求已满足 |
| 直线优先飞行 + 限升降 + 障碍临时航点 + 卡死报告 | §四/§5 的移动要求已满足 |
| `commandVersion` 丢弃旧命令结果；无自主索敌 | §5 的命令优先级与"无命令不索敌"已满足 |
| `originalBoneUuid` + NBT 存档 | §6 的原始身份恢复已预留 |
| 渲染/动画适配器（复用 I&F 模型几何 + 姿态库 + 骨骼贴图） | §2 的统一视觉只需换"统一模型种类 + 眼睛层 + 吐息粒子" |

**统一阶段需要新增/修改的部分**：注册改名（含 `MissingMappings` 别名）、`Variant` 语义改为"原始类型（火/冰/电）"、统一模型种类与蓝色眼睛层、灵魂火吐息（服务端伤害 + 客户端粒子）、火焰免疫、正式契约转换事务、ATTACK 近战与 BOMBARD。

## 9. 开发顺序（以"不牺牲当前原型可验证性"为前提）

1. **先完成并验证 7B 原型**（玩家实机验收）：生成、骨龙模型与动画、独立 MOVE/HOLD、独立飞行（直线优先）、正常龙背骑乘、无抓取。
2. 再进入**统一阶段**：改名（+别名）→ 统一模型/眼睛 → 灵魂火吐息 → 火焰免疫 → 近战与 BOMBARD。
3. 最后做**正式契约转换**（原始龙骨 ⇄ 灵火龙），并在转换事务验证可靠之前**不切换正式复活技能**。

> 本文档只记录方向。7B 原型的代码、部署与验收标准**保持不变**。
