# ROUND 7G — 骨龙动画、发光眼睛、跟随距离与吐息口部（反馈修复）

> 承接 ROUND 7F。本轮只修玩家反馈的四件事：**1) 动作未实装 2) 蓝眼睛未实装 3) 吐息不在嘴部 4) 跟随太近把人顶进模型**。
> **多段碰撞箱（参考包 stage 2）本轮明确不做**，只做 stage 1。

---

## 1. 结论速览

| 问题 | 状态 | 客观证据 |
|---|---|---|
| 1 动作未实装 | **已修复** | 实机探针：飞行时 **44/104 根骨骼在动**（旋转量和在 81.33↔82.61 间摆动）；调用链每帧被调用且姿态库齐全 |
| 2 蓝眼睛 | **已实现并重新标定** | 眼层挂在真实 `Head/Jaw` 骨骼上；坐标按实测头骨盒体从"像素误当方块"改为正确方块单位、并移到头骨**表面之外**（原先会陷在头内部/浮在头前方） |
| 3 吐息位置 | **已实测标定**（附加题） | 沿真实骨链（BodyUpper→Neck1→Neck2→Neck3→Head）实测口部在模型空间 **(0.0, 0.79, -1.72)** 方块 ⇒ 服务端改为按 stage 缩放该实测值 |
| 4 跟随太近 | **已修复** | 实机探针：跟随者在 **18.05 / 21.78 格**处停下（环半径 `max(14, 宽×4+6)=18`），不再进入玩家模型 |

## 2. 版本与安全

| 项目 | 值 |
|---|---|
| Git HEAD | `34a97714bef7a5a130461d737e73f5a3629dc978`（分支 `main`，未变） |
| 脏文件 | 340 → **342**（新增 `SoulfireEyesLayer.java`、`SoulfireRigProbe.java` 等，均为新增，无删除） |
| 破坏性 git 操作 | 无（无 reset/clean/checkout/commit/add/stash；stash 仍为 0） |
| 本轮备份 | `D:\MinecraftModding\_backups\round7g_before\`（39 个文件：soulfire 包 + compat/iceandfire 全包 + 两个探针） |
| 补丁自建备份 | `D:\MinecraftModding\_Soulfire7G_stage1_backup_20261010_091123_247426` |

## 3. 参考包集成

`SoulfireDragon_GPT6_Round7G_StagedSourcePatch.zip` 解压到 `D:\MinecraftModding\_patches\Soulfire7G\`。

**stage 1 的 7 项全部通过补丁自带安装器的哈希校验并原样安装**（`READY ×7` → `APPLIED ×7`）：

```
READY java compat/iceandfire/client/SoulfireDragonAnimator.java     (7F 基线 85514c25 ✔)
READY java compat/iceandfire/client/SoulfireEyesLayer.java          (新增)
READY java compat/iceandfire/client/SoulfireIafDragonRenderer.java  (7F 基线 e535614f ✔)
READY java compat/iceandfire/client/SoulfireIafTabulaModel.java     (7F 基线 385a09af ✔)
READY java soulfire/SoulfireDragonEntity.java                       (7F 基线 dfe4d8dc ✔)
READY java soulfire/goal/SoulfireCommandGoal.java                   (7F 基线 08af8309 ✔)
READY resource textures/entity/soulfire_eye_glow.png                (新增)
```

* 补丁自身的 `new_sha256` **7/7 全部匹配**（PATCH-OK）；
* 我 7F 的修复（vanilla `EntityModel` 委托、`boneModel()` 探针访问器）被补丁**正确保留**（其 `SoulfireIafTabulaModel` 就是我的委托版本 + `tabula()`）；README 也明确要求不得回归 Citadel 直接继承 —— 与 7F 的结论一致；
* **stage 2 未安装**（多段碰撞箱本轮不做）；
* 用到的 API 已在真实 jar 中逐条核对：`TabulaModel.getCubes()/getCube(String)`、`AdvancedModelBox.boxName/getParent()/translateAndRotate()/cubeList`、`EnumDragonPoses.STREAM_BREATH`、实体 `isBreathing()/isSitting()` 全部存在 ✔。

## 4. 根因与修复

### 4.1 动作未实装（问题 1）

**取证过程（先证实、再改）**：在 7F 的构建上装了调用计数器，实机得到

```
[round7G] model: setupAnim call 1 , delegate=true , limbAmount=0.0
[round7G] animator: setRotationAngles call 1 flying=false sitting=false ... ground=true fly1=true walk1=true sittingPose=true
[round7G] model: renderToBuffer call 1 , delegate=true
```

即 **调用链是通的、姿态库也有值**，问题出在"有没有把姿态真正写回被绘制的骨骼"。7F 的适配器走 `IceAndFireTabulaModelAnimator.moveToPose()`，而该方法构造的是 Citadel **关键帧动画增量**，需要一个 `IAnimatedEntity` 关键帧驱动器才会逐帧生效；我们的实体没有（也不该有）I&F 的 `EntityDragonBase`，所以模型停在默认姿态 = 一条会平移的骨架 —— **与玩家描述完全一致**。

**修复**：stage 1 的新 `SoulfireDragonAnimator` 改为**逐帧按骨骼名把姿态角度/支点插值写回真实 Tabula 实例**：
* 飞行/悬停 → I&F `FLIGHT1..6` 循环（每姿态 10 tick 插值）；
* 行走 → `WALK1..4`（受 `limbSwingAmount` 权重）；
* 坐下 → `SITTING_POSE`；否则 `GROUND_POSE`；
* 吐息 → `STREAM_BREATH` 只驱动 头/颚/颈（`applyBreathingHead`）。

**客观结果（实机探针）**：
```
[round7G] rig probe: flying   - 44 of 104 boxes moved, the sum of all rotations travelled 2.05  (stayed between 81.33 and 82.61)
[round7G] rig probe: flying   - 46 of 104 boxes moved, the sum of all rotations travelled 39.28 (stayed between 80.16 and 90.47)   ← 带行走混合的一轮
[round7G] rig probe: standing - 0 of 104 boxes moved, the sum of all rotations travelled 0.0    ← 站立姿态＝模型默认姿态，静止是正确的
```
> 诚实说明：我第一次测量时报 `0 of 104 moved`，那是**我的探针把指令发给了客户端副本**导致的无效测量（服务端没飞），不是代码问题；改正后再测即为上表。玩家在 7F 版看到的"纯平移"是真实存在的缺陷，本轮的实现确实改变了它。

### 4.2 发光蓝眼睛（问题 2）

补丁新增 `SoulfireEyesLayer`（`RenderType.eyes` + `soulfire_eye_glow.png`），挂在真实 I&F `Head` 骨骼上，沿父链 `translateAndRotate` 绘制一对眼睛。**问题在于坐标与单位**：

* 实测骨骼：`Head pivot=(0.0,0.1,-5.5) box x -2..2 / y -2.2..1.8 / z -4..0`、`HeadFront box z -5..0`、颈链逐级向 **-z**（⇒ -z 为前方，吻端到 z=-5 即 -0.3125 方块）；
* Citadel 的 `translateAndRotate` 会**把枢轴除以 16**（字节码已确认 `ldc 16.0f`）⇒ 该图层绘制空间是**方块**，而补丁原来的 `EYE_X=0.26 / EYE_Y=-0.15 / EYE_Z=-0.57 / size=0.13` 会把手绘眼片放到**头骨前方 0.26 格、下方 0.15 格**（浮空），且尺寸相对头骨（仅 0.25 格长）过大。

**修复（按实测几何重写常量）**：头骨在绘制空间为 `x ±0.125 / y -0.1375..0.1125 / z -0.25..0`：

```java
EYE_X = 0.13F;    // 刚好在头骨侧面之外（±0.125 之外），否则会被头骨自身遮住
EYE_Y = 0.055F;   // 头骨上半部
EYE_Z = -0.19F;   // 吻端（-0.3125）之后约 0.06 格
EYE_SIZE = 0.03F; // 眼片半宽；乘 stage 缩放后可见
```

**仍需玩家确认**：眼片是否正好落在眼眶（本机无法自动判读画面）。若偏差，只需调整这 4 个常量，不必动结构 —— 常量已在类头注释中写明推导过程。

### 4.3 跟随距离过近（问题 4）

补丁把 FOLLOW 从"走到玩家脚下、半径 6 格"改为**环形阵位**：

```java
FOLLOW_MIN_STOP = 14.0;                        // 最小 14 格
stop    = max(14.0, getBbWidth() * 4.0 + 6.0); // 本龙宽 3 ⇒ 18 格
restart = stop + 5.0;                          // 5 格滞回，避免抽搐
formation = queen.position() + outwards * stop;// 永远不把 queen.position() 当终点
```
换向时不再导航到玩家脚下；已在阵位内则 `stand()`（含"空中锚点改写"）✔。

**客观结果（实机探针，真实玩家）**：
```
[round7G] rig probe: PASS - the follower settled 18.05 blocks from the player after 53 ticks (stop ring would be 18.0)
[round7G] rig probe: PASS - the follower settled 21.78 blocks from the player after 67 ticks (stop ring would be 18.0)
```
（第二次 21.78 是"落地瞬间"的位置：它先飞近再降落，从未进入 18 格以内 ⇒ 不再顶进玩家模型 ✔）

### 4.4 吐息口部（附加题，问题 3）

**不再估算，改为实测**：探针在真实客户端沿骨骼链把 `Head` 的**吻端角点**变换到模型空间，得到

```
[round7G] rig probe: the mouth of the drawn skeleton sits at model (0.0 , 0.79 , -1.72) blocks ,
          which the server must aim at as forward 7.75 and up 3.57 at stage scale 4.5
```

补丁原本用 `forward 3.25 / up 2.55`（固定经验值）⇒ 对 4.5 倍巨兽而言只有实测值的一半，吐息看起来从胸口出来。现在：

```java
MUZZLE_FORWARD = 1.72;   // 模型方块，实测
MUZZLE_UP      = 0.79;   // 模型方块，实测
float scale = this.stageScale();                       // 0.9 / 1.8 / 3.0 / 4.5 / 6.0
return position().add(facing.scale(MUZZLE_FORWARD * scale)).add(0, MUZZLE_UP * scale, 0);
```

并把 **stage 缩放表从渲染器搬到实体** `SoulfireDragonEntity.stageScale()`（服务端也能读），渲染器改为 `entity.stageScale()` —— 于是"吐息口部"与"视觉大小"永远用同一张表，不会再各自漂移。

**说明**：该值是站立姿态下的实测；飞行姿态头部会有小幅偏移，属可接受误差（多段碰撞箱那一轮可用头部件精确校正，本轮不做）。真正的"从嘴里喷出"仍**需要玩家肉眼确认**。

## 5. 修改清单（哪些文件、为什么）

| 文件 | 来源 | 我是否改动 | 说明 |
|---|---|---|---|
| `soulfire/SoulfireDragonEntity.java` | 补丁 stage1 | **是** | 补丁的 `getMuzzlePosition()` 用 3.25/2.55 经验值；改为实测 `MUZZLE_FORWARD=1.72 / MUZZLE_UP=0.79 × stageScale()`；新增 `stageScale(int)/stageScale()`（单一真值表） |
| `soulfire/goal/SoulfireCommandGoal.java` | 补丁 stage1 | 否 | 环形跟随 + 5 格滞回，原样使用 |
| `compat/iceandfire/client/SoulfireDragonAnimator.java` | 补丁 stage1 | **是（仅诊断）** | 逐帧姿态插值原样保留；增加 `-Dspiderqueen.soulfire.rig` 才输出的每 1000 次调用日志（默认关闭，正式游戏不产生日志） |
| `compat/iceandfire/client/SoulfireIafTabulaModel.java` | 补丁 stage1 | **是（仅诊断）** | 同上（`setupAnim`/`renderToBuffer` 计数） |
| `compat/iceandfire/client/SoulfireIafDragonRenderer.java` | 补丁 stage1 | **是** | 眼层注册原样；`scale()` 改用 `entity.stageScale()`（消除重复表） |
| `compat/iceandfire/client/SoulfireEyesLayer.java` | 补丁 stage1（新增） | **是** | 眼片坐标/尺寸按实测头骨几何重写（原值单位错误、位置浮空） |
| `textures/entity/soulfire_eye_glow.png` | 补丁 stage1（新增） | 否 | 眼睛发光贴图 |
| `client/SoulfireRigProbe.java` | 本轮新增 | — | 7G 验证探针（`-Dspiderqueen.soulfire.rig=true`，默认关闭） |
| `build/launch_probe.ps1`、`build/run_probe_retry.ps1`、`build/probe_paths.txt`、`build/launch_client.bat` | 本轮新增/改动 | — | 探针启动与自动重试（本轮中文路径在命令行被 codepage 破坏，改为 ASCII 路径 + 世界副本） |

**没有做的**：多段碰撞箱（stage 2）、骑乘、吐息特效本身、蓝色眼睛之外的视觉。

## 6. 客观验证（本轮实际跑过的）

* **GameTest**：`soulfire_dragon` 批量 19 项 —— 见第 7 节（含 7F 的 5 项降落测试，用于确认 7G 的 FOLLOW 改动没有破坏降落闭环）。
* **实机探针**（真实客户端 + 真实集成服务端，真实世界 `probe_world` 副本）：
  * 渲染器/贴图：`SoulfireIafDragonRenderer` + `iceandfire:fire_skeleton_4.png`；
  * 骨架：`cubes=104`，`bone "Head" found=true`、`bone "Jaw" found=true`，头/颈/颚枢轴与盒体全部读出；
  * 动画：飞行 `44/104` 骨骼在动（travel 2.05；带行走混合的一轮为 `46/104`，travel 39.28），站立 0/104（＝默认姿态，正确）；
  * 跟随：18.05 / 21.78 格（环半径 18）；
  * 口部：模型空间 `(0.0, 0.79, -1.72)` 方块，stage 4 折算 `forward 7.75 / up 3.57`。

## 7. GameTest 结果

* 总数 173；本轮关注的 `soulfire_dragon:1` 批量 **19/19 通过**（无 `failed!` 行）；
* 与 7F 相同的既有无关失败（人类/Sweatlord 家族）依旧存在，本轮**未触碰**其代码；
* 日志：`build\gametest7g2.log`。（与 7F 同一约定：确认 soulfire 批量通过后即终止全量运行。）

## 8. 未完成 / 待玩家确认

* **眼睛是否正好在眼眶**：本机无法判读画面，只能给出按实测几何推导的常量；若偏，只改 4 个常量。
* **吐息是否真从嘴里喷出**：服务端位置已按实测标定，但 4.5 倍体型的观感需要人眼确认；多段碰撞箱落地后可用头部件做最终校正（下一轮）。
* **多段碰撞箱（F3+B 六个部位）**：**本轮按你的要求未实现**。
* 行走动画的权重/节奏（`limbSwingAmount * 3` 的观感）、飞行姿态 10 tick/姿态的节奏，属于主观项，交给你。
* 预览世界：探针运行在**副本** `D:\mcinst\saves\probe_world`（由你的 `新的世界 (1)` 复制而来），**没有**污染原存档。

## 9. 下一轮建议

1. **多段碰撞箱**（参考包 stage 2 + 你要求的头/颈/躯干/双翼/尾六箱）：它同时能让吐息口部与眼睛位置从"实测常量"升级为"骨骼精确对齐"。
2. 若接受，可把 `probe_world` 副本删掉（探针专用）。
3. 骑乘（座椅点可直接取骨链坐标）。

## 10. 已部署 jar

| 项目 | 值 |
|---|---|
| 文件 | `spiderqueen_hungrycraft-0.0.1-dev.jar` |
| 大小 / 时间 | 6759.3 KB / 2026-10-10 **10:17:40** |
| SHA-256 | `23558696915B20B619E1BF78C44A1B22B2C352BDA82DA4FF51C1C838EA6980F1` |
| 位置 | `D:\我的世界测试版\2\versions\1.20.1-Forge_47.4.23\mods\`（`D:\mcinst\mods` 为同一目录的联接） |
| 构建链 | `gradlew build`（含 reobfJar）→ `gradlew deployIceAndFireTest`，均 BUILD SUCCESSFUL |

## 11. 实机验收步骤（请你来看）

1. 生出一只灵火龙 → **走动时腿/翼/颈有相对动作**，飞行/悬停时翅膀按 I&F 飞行姿态扇动（不再整体平移）。
2. **蓝眼睛**：白天/黑夜都能看到眼窝处的青色发光点；若位置偏（浮空/陷进头骨），告诉我偏的方向即可微调。
3. **跟随后距离**：让它跟着你走，应停在约 18 格（4 阶段体型），**不会再把你顶进模型**。
4. **吐息**：让它对地面喷息，观察蓝色火焰是否从**嘴部**开始（而不是胸口/头顶）。
5. 顺手确认 7F 的回归：高空 HOLD / 远程 MOVE / FOLLOW 最终都会 `onGround` 落地收翼。
