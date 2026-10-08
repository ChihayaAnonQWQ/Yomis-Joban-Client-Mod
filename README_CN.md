# Yomi's Joban Client Mod-JSPIDS

[English](README.md) | **中文**

Joban Client Mod 的非官方版本。

仅支持 1.20(.1)。

Yomi's Joban Client Mod（简称 YJCM）是一个基于 [Minecraft Transit Railway](https://github.com/jonafanho/Minecraft-Transit-Railway) 的扩展模组，加入了大量港铁风格的方块，以及能显著改善世界搭建体验的工具方块。

它加入的方块包括自定义信号灯、优惠站机、Railway Vision 乘客资讯显示屏等等。

![](https://user-images.githubusercontent.com/40461728/187031355-e71be327-e520-4add-aaba-dc9b6421fb44.png)

> [!IMPORTANT]
> ## 许可
> 本仓库的代码来自**两个不同的来源**：
>
> | 部分 | 许可 | 版权 |
> |---|---|---|
> | JCM（Joban Client Mod）代码 | MIT（见 [LICENSE_JCM](LICENSE_JCM)） | AmberFrost、StrikeSNC、AozoraSky |
> | YJCM 原创代码（由 Yomi 编写） | 保留所有权利（见 [LICENSE](LICENSE)） | Yomi（Yomi_307） |
>
> - JCM 部分的代码可以按 MIT 许可使用、修改和分发。
> - **YJCM 专有代码并非开源**。未经许可复制、再分发或修改均被禁止。
> - 如果你 fork 本仓库，必须保留两份许可文件和本声明。

## 本分支做了什么

> [!WARNING]
> 本分支大部分工作由 **DeepSeek**（一个 AI 代理）完成，它的做法是拿自己的成果去和游戏对照，
> 而不是照着规格书实现。因此**不保证**与所有资源包和世界兼容，也**不保证稳定性**。

分支 **`feat/jcm-pids-components`** 把 **JCM 2.x / MTR 4 的 PIDS 体系**移植到了
**MTR 3 上的 YJCM**（Minecraft 1.20.1、YMTR 3.6.3）—— 并顺手修掉了"把那些面板真正放进世界里"
才暴露出来的一批问题。一个 PIDS 预设要么能在一块真实的方块上正常排布，要么不能，而只有游戏能回答是哪种。

完整的技术记录（中文，含反编译证据和实机日志）：**[MTR3-PIDS-PORT.md](MTR3-PIDS-PORT.md)**。本节只是摘要。

### 新增

| | |
|---|---|
| 组件式布局 | `com.jsblock.pids` —— 11 个组件；资源包用 JSON（`components`）声明布局 |
| JavaScript 预设 | `com.jsblock.script` —— 内嵌 Rhino 1.7.15；JCM 2.x 的 `.js` 预设**原样**运行（`scriptFiles`） |
| 脚本沙箱 | 类访问白名单、关闭前的警告界面、面向玩家的失败提示、调试浮层 |
| 1A PIDS 预设 | `jsblock:pids_1a` 拿到了 LCD / RV PIDS 早就有的预设存储、自动切换和配置界面 |
| 贴图 | JCM 2.x 的 PIDS 素材（`weather_*`、`plat_circle`、`rv_default`、`black`） |
| 检查程序 | `tools/run-pids-check.ps1` —— 三个无头检查，用真实引擎跑真实预设 |
| 诊断 | `-Djsblock.pids.trace=true` 记录预设发出的每一次绘制调用（类型、深度、颜色、文字） |
| 文档 | `MTR3-PIDS-PORT.md` |

既不声明 `components` 也不声明 `scriptFiles` 的预设，仍然走原来那套硬编码渲染路径，未做改动。

### 修好的上游缺陷 —— 不修这些，这个 fork 根本构建不出来

| | |
|---|---|
| 缺少 `ForgeConfig.java` | `JobanForge` 引用它，而上游从未提交 |
| Gradle 8 | `classifier` 已被移除，现在叫 `archiveClassifier` |
| `font` 与 `fonts` | 预设读取端用 `fonts`，所有渲染端用 `font`，于是这个键被静默丢弃 |

### 实机发现的渲染缺陷

| 症状 | 原因 |
|---|---|
| 天气图标变成一个白方块；面板背景闪烁 | 脚本贴图走了不透明渲染层；JCM 2.x 用的是半透明层 |
| 线路号 / 车厢数徽章没有底色，站台圆圈只剩数字，线路图横条和站点圆点消失 | `.color()` 收的是六位 RGB，而 MTR 3 直接从它里面读 alpha；JCM 2.x 会先加上 `ARGB_BLACK` |
| 跑马灯每跑一遍文字就变大一点，最后滚出面板 | 滚动是用前导空格实现的，于是它也参与了宽度测量 |
| 整块面板往左偏了约 40% | 面板原点是从面板尺寸推算的，而不是照抄 JCM 2.x 的硬编码字面量 |
| 双方块 PIDS 每块只显示单面 | 有一半被跳过了；JCM 2.x 两端都画，各画一面 |
| 1A 面板画两遍，或者干脆不画 | `pids.isKeyBlock()` 是写死的，预设无法分辨两半 |
| 中日韩文字只有它自己预留格子的一半大 | 布局和绘制用了两个不同的 CJK 倍率 |

### 实机发现的数据缺陷

| 症状 | 原因 |
|---|---|
| 空行被填上"暂无服务"，每条还带着一个永久的"1 分钟"并在两种语言之间跳 | `arrivals().get(i)` 越过列表末尾时返回的是占位对象而不是 `null`，于是预设自己的判空从来不触发 |
| 每个自动检测站台的面板上 `pids.station()` 都是 null | 查询被传入了一个它自己的守卫会拒绝的 `null` 世界 —— 这也让线路图永远从第一站开始 |
| `Route.getDestination` 在多数线路上什么都不返回 | MTR 3 只读逐站自定义终点；MTR 4 会回退到终点站 |

现在，越界索引且不判空的预设会像在 JCM 2.x 里一样抛错 —— 而引擎会用占位数据**重试一次**来救回这一帧，
因为资源包不是模组能改的东西。抛错仍会写进日志；玩家只会看到一行提示，只有重试也救不回来时才变红。

### 像素化

一个预设可以「画小再放大」，让它的文字和图标全部落在同一个粗网格上——也就是点阵屏或 LCD 屏的样子。
默认关闭，能提要求的人有两个：

| 谁 | 在哪里 | 写什么 |
|---|---|---|
| 玩家 | `config/jsclient.json` → `"pixelScaleByPreset": { "预设id": 3 }` | 多粗，按预设；写 `1` 是否决资源包的要求 |
| 玩家 | `"pixelShapeByPreset": { "预设id": "circle" }`，或 `"pixelShapeDefault": "circle"` | 用哪种网格，按预设或对所有预设 |
| 资源包 | 预设条目里：`"pixelScale": 3`、`"pixelShape": "circle"` | 它当初是按什么屏画的 |
| 资源包 | 预设条目里或整包：`"pixelResolution": 96`（横向多少点）、`[96, 54]`、或 `{"width": 96, "height": 54}` | 它照哪块板画的；倍率不够精确时用这个 |
| 资源包 | `"pixelDots": [68, 38]`（整包写 `dots`）| 板子上有多少颗灯珠；想让画面比点更细时用这个 |
| 资源包 | `joban_custom_resources.json` 顶层 | 一样的东西，一次管它所有预设 |

```json
"pixelation": { "enabled": true, "scale": 3, "shape": "circle" }
```

`enabled: false` 是资源包在说「我的预设都不要像素化」——这在 2.1 之前它没法说。玩家的条目仍然优先，
包括玩家写 `1` 的时候。

> **资源包作者请直接看完整指南：[docs/pixelation-guide.zh.md](docs/pixelation-guide.zh.md)** —— 每个键的写法、三种画布的精确比例、以及哪组点数看起来像什么。

分辨率比倍率精确：倍率是**除数**，只能落在能整除画布的网格上（136 除以 3 不是整数）。而且
**网格的比例永远等于画布的比例**——只取宽度作为意图，高度按画布重算。原因很实在：放大后的离屏纹理是
被拉伸铺满整块面板的，比例不对就是画面被压扁。136×76 的画布上写 `96` 会得到 `96x54`；一旦和包里写的高度
对不上，日志会说明改成了多少、为什么。

`square` 是像素紧挨着的方块，既是默认值，也是 1.4 一直画的样子；`circle` 是圆点加暗缝，每个离屏像素一格
遮罩，代价是每块面板多一个 quad，而不是多一趟渲染。**唯一不适合 `circle` 的情况**是预设的画布有透明
区域：缝隙是照着遮罩画的，不把目标读回 CPU 就拿不到逐像素的 alpha。

### 脚本 API 覆盖情况

口径是**对着官方脚本文档**（<https://jcm.joban.org/v2.2/dev/scripting/>）比对，而不是"手头这些预设恰好用到什么"。
那些页面共记录了 **515 条 API 条目**，下面是本分支当前的位置。

比对是机械的：从 37 个文档页面抓出每一条 `Class.method(...)`，本分支自己的成员来自对构建出的 jar
跑 `javap -public`（含嵌套类和继承来的成员），然后逐条 diff。

**已实现** —— PIDS 脚本够得到的全局对象：

| | |
|---|---|
| 绘制 | `Text` `Texture` `Rectangle` `Vector3f` `Matrices` |
| 时间与状态 | `Timing` `StateTracker` `CycleTracker` `RateLimit` |
| 资源 | `Resources`（含 `getMTRVersion`、`getAddonVersion`） `TextUtil` |
| 世界 | `MinecraftClient` `MinecraftClient.localPlayer()` `PlayerEntity` |
| 耗时操作 | `BackgroundWorker` `Networking` `NetworkResponse` `DataReader` |
| 声音 | `ctx.getSoundManager()` `SoundManager` `TickableSoundInstance` |
| 杂项 | `console` `print` `include` `SCRIPT_INPUT` |
| 面板本身 | `pids.*`、`arrivals().*`、`arrival.*`、`pids.station()`、`route().getPlatforms()`、`ctx.setAutoZOrdering()` `ctx.setZOrderStep()` |
| 本分支独有 | `arrival.routeType` 与 `arrival.isLightRailRoute`（MTR 3 的 `Route.routeType`：`NORMAL` / `LIGHT_RAIL` / `HIGH_SPEED`）。MTR 4 没有线路制式，而且**两个版本都没有快慢车**——所以预设想要「快慢车」是在要一份不存在的数据；要体现它，只能像那些包一样去匹配线路名里的关键字 |

**还缺的，按组列出。** 约 154 条，已知没有任何 PIDS 预设会调它们；列在这里是为了让写包的人一眼看清，
而不是靠试。*（译注：分组标题中的英文类名与官方文档一致，便于对照。）*

*类在、方法还没加的（81 条）：*

| 类 | 缺的 |
|---|---|
| `Resources` | `read` `readString` `readBufferedImage` `readFont` `idr` `exist` `manager` `getNTEVersion` `getNTEVersionInt` `getNTEProtoVersion` `getSystemFont` `hasSystemFont` `ensureStrFonts` `getFontRenderContext` |
| `Station` | `getZone1/2/3`、`getMinX/Y/Z`、`getMaxX/Y/Z`、`getExits`、`inArea`、`isTransportMode`、`getCenter` —— 需要保留车站几何，而当前包装类没存 |
| `Stop` | `distance` `dwellTime` `dwellTimeMillis` `platform` `destinationName` `destinationStation` `customDestination` 等 6 条 |
| `PlayerEntity` | `activeItem` `mainHandItem` `offHandItem` `yaw` `pitch` `bodyYaw` `isSneaking` `isSprinting` `isSwimming` `isHoldingItem` `playerName` |
| `Platform` | `getId` `getHexId` `getName` `getMidPosition` `getDwellTime` `containsPos` `routes` `routeColors` |
| `MinecraftClient` | `blockLightAt` `skyLightAt` `getRedstoneLevel` `getScoreboardScore` `getWorldPlayers` `spawnParticleInWorld` |
| `DataReader` | `asInputStream` `openInputStream` `asByteArray` `asBufferedImage` `asFont` |
| `SimplifiedRoute` | `getId` `getColor` `getCircularState` `getPlatformIndex` |
| `NetworkResponse` | `success` `exception` `getHeaders` |
| `SimplifiedRoutePlatform` | `getDestination` `getStationId` |
| `PIDSScriptContext` | `getRenderManager()`（3D 模型渲染）、`setDebugInfo()` |

*整个类都还没有的（73 条）：*

| 类 | 条数 | 说明 |
|---|---|---|
| `VanillaText` | 11 | 配合 `displayMessage(VanillaText, ...)` 的富文本 |
| `Siding` `PathData` `Vector` `Position` `Rail` | 43 | TSC 数据类，实际是**车辆脚本**在用 |
| `Files` | 5 | 读写数据文件 |
| `VoxelShape` `ItemStack` `TransportMode` `UtilitiesClient` | 11 | 边角料 |
| `StationExit` | 2 | 车站出口 |
| `CarDetails` | 1 | `getVehicleId()` —— MTR 3 不向客户端下发逐节车厢数据，所以 `cars()` 是**故意**返回空列表 |

*范围之外：那是另一种脚本类型（18 个类、约 162 条）。* `VehicleWrapper`、`VehicleScriptContext`、
`VehicleExtraData`、`EyecandyWrapper`、`EyecandyScriptContext`、`RenderManager`、`ModelManager`、
`Model`、`RawModel`、`RawMeshBuilder`、`DynamicModelHolder`、`GraphicsTexture`、`QuadDrawCall`、
`DisplayHelper`、`BlockUseEvent`、`EyecandyEvents`、`ModelData`、`Vehicle` —— 这些属于 Vehicle Scripting
和 Eyecandy Scripting，不是 PIDS。

如果某个包确实需要上面某一样，**前四组是最便宜的**（每项几行），其中 `Resources.read*` 最可能真被用到 ——
那是包读取自己文件的必经之路。

### 资源包的 PIDS 无法正常工作怎么办

如果您遇到有资源包的 PIDS 无法正常工作，可以把**游戏版本信息**、**游戏日志**、**资源包下载链接**
发给我，或者直接提 [Issue](https://github.com/ChihayaAnonQWQ/Yomis-Joban-Client-Mod/issues/new)。

- **游戏版本信息**：Minecraft 版本、MTR / YMTR 版本、本模组版本（当前 `1.2.12-JSPIDS-2.0`）
- **游戏日志**：`logs/latest.log`。面板报错会写成一行
  `[Joban Client] PIDS script "..." threw in render(): ...`，
  它直接写明是哪个预设、脚本哪一行、缺的是哪个 API —— 多数情况下不用复现就能定位
- **资源包下载链接**：包名或下载地址，方便我把预设拆出来在无头检查里跑一遍

### 配置界面

预设选择框原本画在它的输入框**下面**，盖住了下面几行，而那几行又把标签画在它上面 —— 结果两边都读不了。
现在它画在输入框**旁边**，带背景和边框，就是下图这样。两个 PIDS 配置界面共用这个控件，所以两边一起修好了。

![预设列表画在输入框旁边，下面的行标签保持可读](docs/pids-config-preset-list.png)

### 已知问题

- **没有选预设的 1A PIDS 会用 Railway Vision PIDS 的样式绘制。** 1A 乘客资讯显示屏为了让预设体系能
  管到它，被移到了 YJCM 的 RV 渲染器上，而没有选预设的方块会回退到这个渲染器自己的布局。
  **绕法：** 拿刷子右键方块，把「PIDS 显示格式」切成任意一个资源包提供的显示格式 —— 之后这块面板
  就由预设绘制，而不是内置布局。

![没有选预设的 1A PIDS，用的是 Railway Vision PIDS 的样式](docs/pids-1a-no-preset-close.png)

下图车站里那些浅蓝条纹的板子就是没选预设的：

![同一批板子混在车站其余部分里](docs/pids-1a-no-preset-station.png)

### 怎么对着本分支构建 MTR 3

MTR 的 dev jar（`MTR-common-1.20-*-dev.jar`）已经 404，所以构建用的是一份 Mojang 映射的 MTR 3 jar：

```
~/.gradle/caches/forge_gradle/deobf_dependencies/maven/modrinth/ymtr/
    1.20.1-3.6.3_mapped_official_1.20.1/ymtr-1.20.1-3.6.3_mapped_official_1.20.1.jar
```

把它复制到 `checkouts/1.20/mtr-common.jar`（以及 `mtr-fabric.jar` / `mtr-forge.jar`；三个文件内容相同）。
`checkouts/` 已被 git 忽略。

```powershell
$env:JAVA_HOME = 'C:\Program Files\Zulu\zulu-21'
gradle build --no-daemon --console=plain --max-workers=1        # -> build/MTR-YJCM-1.20-*.jar
.\tools\run-pids-check.ps1                                       # 无头检查，失败时返回非零
```

配置阶段出现一长串堆栈是 `build.gradle` 里 `setupFiles` 的 catch 分支在打印一次失败的
`Minecraft-Mappings` 下载，不是构建失败。

## 常见问题与支持

### 游戏为什么崩了？

原因很多，其中一个主要原因是<b>你用了错误版本的 MTR Mod</b>。
标注为 <u>Pre-release</u> 的版本只能配合 MTR Mod 的预览版使用（见[他们的 Discord](https://discord.gg/hvddbya8rh)）。
如果你拿不到 MTR 的预览版，请下载标注为 <u>Release</u> 的版本。

### 游戏还是崩 / 我想报 bug / 我想提建议

大部分支持都在 Discord 里做，沟通更方便，请加入我们的 [Discord 服务器](https://discord.gg/FNc2rgWmP2)。

### 我想了解更多！

模组的大部分内容都写进了我们的 [wiki](https://www.joban.tk/wiki/JCM:Joban_Client_Mod)。

## 环境搭建

1. 克隆本仓库
2. 同步 Gradle 工程
3. 首次运行时：
   1. 同步 Gradle 工程
   2. 完成后再同步一次 Gradle 工程
   3. 重启 IntelliJ IDEA

## 更新 MTR Mod

MTR Mod 是 Joban Client Mod 的必需依赖。

要在开发环境里把 MTR Mod 更新到最新版，运行 `setupLibrary` 任务。

## 跨版本开发

我们使用 [Manifold](https://github.com/manifold-systems/manifold)，它同时提供了一个预处理器，
用来按条件编译 Java 代码。

预处理器会拿到一个 `MC_VERSION`，它的样子类似 `11904`：
- 第 1 位是主版本（1）
- 第 2–3 位是主要版本（1.19 对应 19）
- 第 3–4 位是次要版本（1.19.4 对应 04）

例如，你想让一段代码只在 1.19.3 及以上生效：

```
   #if MC_VERSION >= "11903"
      LOGGER.info("This line will appear in 1.19.3 and above")
   #else
      LOGGER.info("This line will appear in 1.19.2 or below")
   #endif
```

## 疑难杂症

### Command line is too long. Shorten the command line and rerun.

开启 Shorten Command Line（Edit Configuration）
<img src=https://i.imgur.com/1XKc6ts.png>

### Modules generated_XXXXXX and jsblock export package com.jsblock to module architectury

<b>目前只有 Forge 1.16.5 可用</b>

### 全都不对，一个错接一个错

1. 删除用户目录下的 `.gradle` 文件夹
2. <b>[重要]</b> 提交并推送所有未提交的文件
3. 删除整个 Joban Client Mod 文件夹
4. 重新克隆本工程
