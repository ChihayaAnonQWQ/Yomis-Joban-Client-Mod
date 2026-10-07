# MTR 3 PIDS 移植说明 —— JCM 2.x / MTR 4.0 的 PIDS 体系 → YJCM / MTR 3

本分支把 **JCM（Joban Client Mod）2.x / MTR 4.0** 的 PIDS 体系移植到
**YJCM / MTR 3（YMTR 3.6.3，MC 1.20.1）**，包含两个子系统：

| 子系统 | 来源（JCM 2.x） | 本分支实现 |
|---|---|---|
| **组件化布局预设** | `jcm/mod/data/pids/*` + `PIDSComponent` 组件树 | `com.jsblock.pids.*`，11 种组件，JSON 声明式版式 |
| **JavaScript 脚本预设** | `jcm/mod/scripting/pids/*` + `mtrscripting` + Rhino | `com.jsblock.script.*`，内置 Rhino 1.7.15，可直接跑 JCM 2.x 的 `.js` 预设 |

两个子系统共用同一条预设入口：资源包里的 PIDS 预设 JSON 里写 `components`
就走组件布局，写 `scriptFiles` 就走 JS 脚本，两者都不写则完全沿用 YJCM 原有的
硬编码渲染路径（**向后兼容**）。

---

## 1. 为什么要移植

移植前，YJCM 与 JCM 在 PIDS 上的差距是**架构性**的：

| | YJCM（移植前） | JCM 2.x（MTR 4.0） |
|---|---|---|
| 布局 | 每个元素位置**硬编码**在 `RenderLCDPIDS` / `RenderRVPIDS` 里 | `PIDSComponent` 声明式布局（`x/y/width/height`） |
| 可扩展性 | 想改版式只能改 Java、重新编译 | 资源包写 JSON / JS 即可换版式 |
| 组件类型 | 无 | 11 种（时钟 / 天气 / 站名 / 目的地 / ETA / 车厢数 / 自定义文本 / 自定义贴图 / 轮播 …） |
| 脚本 | 无 | Rhino JS，脚本自己画整块面板 |

YJCM 已有预设系统（`com.jsblock.data.PIDSPreset`，从 `JobanCustomResources` 载入，
支持到站/离站自动切换、`{time}`/`{weather}` 变量替换），但它描述的是**外观**，
不是**版式**，更不能让预设自己决定画什么。本分支补上的正是这两层。

---

## 2. 新增模块

### 2.1 组件化布局（`com.jsblock.pids`）

```
common/src/main/java/com/jsblock/pids/
├── PIDSContext.java      渲染一帧所需的全部数据快照
├── PIDSGraphics.java     渲染状态打包（PoseStack / MultiBufferSource / 光照 / 颜色 / 字体）
├── PIDSGeometry.java     面板几何（位置、缩放、尺寸），由各渲染器提供
├── PIDSComponent.java    组件基类 + 组件注册表 + 共享绘制助手
├── ComponentParser.java  组件工厂函数式接口
├── PIDSAlign.java        JSON 对齐/颜色解析助手
├── PIDSData.java         MTR 3 客户端数据只读门面
├── PIDSLayout.java       预设解析 + 整版渲染
└── component/            11 个组件实现（clock / weather_text / weather_icon /
                          custom_text / custom_texture / cycle / platform_text /
                          station_name / arrival_destination / arrival_eta / arrival_car）
```

### 2.2 JavaScript 脚本预设（`com.jsblock.script`）

```
common/src/main/java/com/jsblock/script/
├── ScriptEngine.java           Rhino 作用域、全局对象、脚本解析与实例缓存、include()
├── ScriptDrawCalls.java        Text / Texture / Rectangle 三条构建链
├── ScriptDrawCall.java         绘制调用基类（pos / size / zOrder）
├── ScriptRenderContext.java    绘制上下文；dryRun() 供无头检查记录调用而不真的画
├── PIDSWrapper.java            暴露给脚本的 pids / arrivals / arrival / route / station / platform
└── ScriptTextures.java         贴图解析（JCM 2.x 的贴图加载语义）

common/src/main/resources/assets/jsblock/scripts/
├── pids_util.js                       PIDSUtil 工具函数（与 JCM 2.x 同名同内容）
└── builtin/pids_1a.js                 随 mod 附带的 1A 预设（JCM 2.x 脚本写法）
```

### 2.3 改动的既有文件

| 文件 | 改动 |
|---|---|
| `common/.../data/PIDSPreset.java` | 新增可空字段 `layout` / `scriptFiles` / `blacklist` / `name` / `builtin`；`fromJson` 解析 `components` 与 `scriptFiles`。**旧预设行为完全不变。** |
| `common/.../render/RenderPIDSBase.java` | 新增三条路径的分发：脚本 → 布局 → 内置；新增 `getLayoutGeometry()`、`renderScripted()`、`renderLayout()` 与面板字面量钩子 |
| `common/.../render/RenderLCDPIDS.java` | 实现 `getLayoutGeometry()`，覆写 LCD 的 JCM 2.x 面板字面量 |
| `common/.../block/PIDS1A.java` | 父类上移到 `JobanPIDSBase`，接入预设存储与配置界面 |
| `common/.../JobanClient.java` | 1A 改用 `RenderRVPIDS` 并对该实例应用 1A 的面板字面量 |
| `gradle.properties` | 新增 `rhino_version=1.7.15`；按离线构建需要固定各依赖版本 |
| `forge/.../mappings/ForgeConfig.java` | **新建**（原仓库缺失该文件，见 §5.1） |
| `fabric/build.gradle`、`forge/build.gradle` | `classifier` → `archiveClassifier`（见 §5.2） |

---

## 3. 预设 JSON 格式（组件布局）

放进资源包的 PIDS 预设仍是原来的 JSON；加一个 `components` 数组即可启用组件布局：

```json
{
  "id": "hk_example",
  "background": "jsblock:textures/block/pids_rv_screen.png",
  "size": [100, 50],
  "color": "FFFFFF",
  "font": "mtr:mtr",
  "components": [
    { "component": "station_name",        "x": 2,  "y": 2,  "width": 46, "height": 8, "prefix": "往 " },
    { "component": "clock",               "x": 80, "y": 2,  "width": 18, "height": 8, "format": "HH:mm" },
    { "component": "arrival_destination", "x": 4,  "y": 14, "width": 62, "height": 8, "row": 0 },
    { "component": "arrival_eta",         "x": 80, "y": 14, "width": 18, "height": 8, "row": 0 },
    { "component": "arrival_car",         "x": 80, "y": 24, "width": 18, "height": 6, "row": 0,
      "only_when_varying": true },
    { "component": "cycle", "x": 2, "y": 36, "width": 96, "height": 10, "interval": 100, "components": [
        { "component": "weather_text", "x": 0,  "y": 0, "width": 48, "height": 10 },
        { "component": "custom_text",  "x": 0,  "y": 0, "width": 96, "height": 10,
          "text": "第 {day} 天 · {worldPlayer} 人在线" }
    ]}
  ]
}
```

- **`size`**：设计画布尺寸（默认 `[100, 50]`）。所有组件的 `x/y/width/height` 都用这套
  设计单位；渲染时整块画布**等比**缩放到面板上，因此同一份预设在小尺寸站台 PIDS 和
  大尺寸投影仪上表现一致。
- **`color` / `font`**：与旧预设含义相同，作为组件未单独指定颜色/字体时的默认值。
- **`background`**：可选。带布局的预设会**自己绘制**背景图（内置渲染器的背景绘制被绕过，
  所以这一层由布局路径补上）；不写则只有组件。
- 旧预设（无 `components`）继续走原有硬编码渲染路径，**完全向后兼容**。

### `row` 的语义：显示行，不是班次下标

`arrival_*` 组件的 `row` 是**显示行号**，采用与内置渲染器（`RenderLCDPIDS` /
`RenderRVPIDS`）完全一致的规则：

> 被 `hideRow` 标记为隐藏的行**不消耗**班次，因此该班次会顶到下一个可见行。

所以 `row: 2` 指的始终是用户在屏幕上看到的第 3 行，与预设隐藏了几行无关。

### 随 mod 附带的示例预设

`common/src/main/resources/assets/jsblock/joban_custom_resources.json` 内置了三个可直接使用的布局预设，
同时也是本引擎的活体测试用例：

| id | 演示内容 |
|---|---|
| `layout_lcd` | 复刻内置 LCD PIDS：站名 + 时钟表头，4 行「终点站 + ETA」 |
| `layout_rv` | 复刻内置 RV PIDS，并补上内置渲染器只在特定 tick 闪现的**车厢数** |
| `layout_notice` | 演示 `cycle` 组件：天气 / 日期 / 在线人数三屏轮播 |

### 通用选项（所有组件）

| 键 | 说明 |
|---|---|
| `halign` | `left` / `center` / `right`（默认随组件而定） |
| `valign` | `top` / `center` / `bottom` |
| `color` | `RRGGBB` 或 `AARRGGBB` 十六进制 |
| `scale` | 文本缩放倍数，默认 `1` |

### 各组件专属选项

| 组件 | 专属键 |
|---|---|
| `clock` | `format`（默认 `HH:mm`，支持 `ss`）、`seconds` |
| `weather_text` | `sunny`、`raining`、`thundering`（文案覆盖） |
| `weather_icon` | `sunny_texture`、`raining_texture`、`thundering_texture`、`tint`、`translucent` |
| `custom_text` | `text`（必需）、`use_variables`（默认 `true`） |
| `custom_texture` | `texture`（必需）、`tint`、`translucent` |
| `cycle` | `interval`（tick，默认 `100`）、`components`（子组件数组） |
| `platform_text` | `prefix`、`fallback` |
| `station_name` | `prefix`、`use_mtr_formatting`（默认 `true`） |
| `arrival_destination` | `row`（默认 `0`）、`show_route_name` |
| `arrival_eta` | `row` |
| `arrival_car` | `row`、`only_when_varying` |

---

## 4. JavaScript 脚本预设

一个 JCM 2.x 的脚本预设，在 YJCM 里**不需要任何改动**即可运行。预设 JSON 声明脚本文件：

```json
{
  "id": "nanbin_crt_pids_1",
  "name": "CRT PIDS (Style 1)",
  "builtin": true,
  "background": "nanbin:pids/image/crt_pids_1.png",
  "fonts": "nanbin:harmonyos_sanssc_medium",
  "scriptFiles": ["nanbin:pids/script/crt_pids_1.js"],
  "blacklist": ["pids_1a"]
}
```

脚本从 `assets/<namespace>/<path>` 读取，位置与 JCM 2.x 完全一致。

### 4.1 脚本生命周期

```js
function create(ctx, state, pids)  { }   // 面板首次出现时调用一次
function render(ctx, state, pids)  { }   // 每帧调用，在这里 draw
function dispose(ctx, state, pids) { }   // 面板消失时调用
```

`state` 是一个每块面板独立的空对象，用来存放脚本自己的状态（轮播计时器等）。
**每块 PIDS 方块位置有独立的脚本实例**，同一面板的两半不会共用状态。

### 4.2 注册的全局对象

| 全局 | 作用 |
|---|---|
| `Text` / `Texture` / `Rectangle` | 三条绘制构建链，见 §4.3 |
| `Resources.id("namespace:path")` | 引用资源 |
| `include(Resources.id("...:....js"))` | 把另一份脚本并入当前作用域（JCM 2.x 的依赖写法） |
| `TextUtil` | `cycleString(text[, ticks])`、`getCjkParts` / `getNonCjkParts` / `getExtraParts` / `getNonExtraParts` / `getNonCjkAndExtraParts`、`isCjk` |
| `MinecraftClient` | `worldDayTime()`、`worldIsRaining()`、`worldIsThundering()` |
| `RateLimit(seconds)` | 新建一个限流器：`shouldUpdate()` / `resetCoolDown()` |
| `print(...)` | 输出到游戏日志（`[PIDS script]` 前缀） |

`PIDSUtil`（`formatTime` / `getETAText` / `getCarText` / `formatDateTime`）**由 JS 提供**，
随 mod 附带在 `assets/jsblock/scripts/pids_util.js`，与 JCM 2.x 的自带文件同名同内容——
预设用 `include(Resources.id("jsblock:scripts/pids_util.js"))` 引入即可。

### 4.3 绘制构建链

```js
Text.create("Clock").text("06:00").color(0xFFFFFF).pos(124, 6).scale(0.8).rightAlign().draw(ctx);

Texture.create("Background")
    .texture("nanbin:pids/image/crt_pids_1.png")
    .size(pids.width, pids.height)
    .draw(ctx);

Rectangle.create("Bar").color(0xFF0000).pos(0, 0).size(100, 4).draw(ctx);
```

`Text` 支持 `scale` / `leftAlign` / `centerAlign` / `rightAlign` / `shadowed` / `stretchXY` /
`scaleXY` / `wrapText` / `marquee` / `marquee(ticks)` / `withMarqueeProgress` / `lineHeight` /
`fontMC` / `font(id)` / `renderType` / `italic` / `bold` / `color` / `naturalLight` / `measureWidth`。
`Texture` 支持 `texture` / `uv` / `color` / `naturalLight` / `renderType`。
三条链都支持 `pos(x, y)`、`size(w, h)`、`zOrder(n)`。

### 4.4 脚本能读到的数据（`pids`）

| 成员 | 说明 |
|---|---|
| `pids.width` / `pids.height` | 画布尺寸（JCM 2.x 的 1/96 方块单位） |
| `pids.type` / `pids.name` / `pids.id` | 面板类型与预设标识 |
| `pids.blockPos()` / `pids.targetPlatformIds()` | 方块坐标与绑定的站台 |
| `pids.getCustomMessage(i)` / `pids.isRowHidden(i)` | 自定义文本与行隐藏标记 |
| `pids.station()` | `name()` / `getName()` / `id()` / `zone()` / `stationName()` |
| `pids.arrivals()` | `get(i)`（越界返回 `null`，与 JCM 2.x 一致）、`size()`、`mixedCarLength()`、`toArray()`、`platforms()` |
| `arrival.*` | `arrivalTime()` / `departureTime()` / `arrived()` / `departed()` / `destination()` / `carCount()` / `routeNumber()` / `routeName()` / `routeColor()` / `circularState()` / `platformName()` / `currentStationIndex()` / `deviation()` / `realtime()` / `cars()` / `terminating()` / `platform()` / `route()` |
| `arrival.route()` | `getPlatforms()` → `size()` / `get(i)` → `getStationName()` / `getPlatformId()` |

> `pids.arrivals().get(i)` **越界返回 `null`**，这是 JCM 2.x 的正式契约（它自己的
> `pids_1a.js` 就是这么守的）。没有任何班次时脚本必须自己判空。

### 4.5 画布尺寸与面板字面量

脚本坐标单位是 JCM 2.x 的 **1/96 方块**，画布尺寸是每种 PIDS 型号的硬编码字面量——
脚本作者就是照这些数字排版，所以必须逐字照抄，不能从面板尺寸反推：

| 渲染器 | JCM 2.x `translate` | 画布 |
|---|---|---|
| `RVPIDSRenderer` | `(-0.21, -0.14, -0.128)` | 136 × 76 |
| `PIDS1ARenderer` | `(-0.47, -0.155, -0.130)` | 186 × 60 |
| `LCDPIDSRenderer` | `(-0.19, -0.125, -0.130)` | 133 × 72 |

Z 值另外按实机观感微调过（面板要离开方块表面，否则会与方块模型争深度）：
RV 与 LCD 见 `RenderPIDSBase` / `RenderLCDPIDS`，1A 为 `-0.112`（在 JCM 2.x 的
`-0.130` 基础上向方块内侧收回 0.018）。

---

## 5. 顺带修复的三个上游缺陷

移植过程中发现 YJCM 公开仓库本身**编不过**，以下问题与本分支的功能无关，但必须修复。

### 5.1 缺失 `ForgeConfig.java`

`forge/src/main/java/com/jsblock/JobanForge.java` 引用了 `com.jsblock.mappings.ForgeConfig`，
但该文件**从未被提交**——它位于 `com.jsblock.mappings` 这个由 `setupFiles` 任务生成、
且未被 `.gitignore` 覆盖却也没进版本控制的目录里。`setupFiles` 下载的
`Minecraft-Mappings-1.20.zip` 里只有 `FabricRegistryUtilities.java` 和 `ForgeUtilities.java`，
不含 `ForgeConfig`。

已按其在 Fabric 侧的对等物 `ModMenuConfig` 还原：把 YJCM 的 `ConfigScreen`
注册为 Forge 的配置界面扩展点（`ConfigScreenHandler.ConfigScreenFactory`）。

### 5.2 Gradle 8 不兼容

`fabric/build.gradle` 与 `forge/build.gradle` 使用了 `classifier`，该 API 在 Gradle 8.0
已被移除（项目原本面向 Gradle 7）。已改为等价的 `archiveClassifier`。

### 5.3 `font` / `fonts` 键不一致

上游 `PIDSPreset.fromJson` 读的键是 **`fonts`（复数）**，而字段和所有渲染器都叫
`font`。资源包写 `font` 时字体被静默丢弃。现在两个键都接受。

---

## 6. 构建

```powershell
$env:JAVA_HOME = 'C:\Program Files\Zulu\zulu-21'
$gradle = "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.8-bin\dl7vupf4psengwqhwktix4v1\gradle-8.8\bin\gradle.bat"
cd work\yjcm-mtr3
& $gradle build --no-daemon --console=plain --max-workers=1
```

### 6.1 MTR 3 编译依赖从哪来

MTR 官方的开发 jar（`MTR-common-1.20-*-dev.jar`）下载地址**已经全面 404**，
`storage.zbx1425.cn` 的 `latest` 路径同样 404。解决办法是利用本机 Gradle 缓存中已有的
**YMTR 3.6.3 经 ForgeGradle 反混淆后的 Mojang 映射版 jar**：

```
~/.gradle/caches/forge_gradle/deobf_dependencies/maven/modrinth/ymtr/
    1.20.1-3.6.3_mapped_official_1.20.1/ymtr-1.20.1-3.6.3_mapped_official_1.20.1.jar
```

把它复制成 `checkouts/1.20/mtr-common.jar`（以及 `mtr-fabric.jar` / `mtr-forge.jar`）即可。
已核对 YJCM 需要的 **65 个 MTR 类全部存在**。注意 `fabric` 与 `forge` 子模块本身
**不依赖 MTR**——MTR 只是 `common` 模块的 `files(...)` 依赖。

### 6.2 离线配置

`gradle.properties` 把 `architectury_version` / `fabric_loader_version` / `fabric_api_version` /
`forge_version` / `mod_menu_version` 全部固定，`build.gradle` 只在缺少这些值时才去查
Modrinth / Fabric meta / Forge promotions。这样配置阶段不需要网络。

> `setupFiles` 仍会尝试下载 `Minecraft-Mappings/1.20.zip`，失败时 `build.gradle` 会打印
> 一整段堆栈再继续——**那段堆栈不是构建失败**，是它的 `catch` 分支在 `printStackTrace()`。

### 6.3 Gradle 代理

`~/.gradle/gradle.properties` 里曾把 Gradle 指向 FlClash 的 `127.0.0.1:7890`。该端口
失效后，`architectury-plugin:3.4-SNAPSHOT` / `loom:1.0-SNAPSHOT` 这类**动态版本**的
依赖解析会带着退避反复重试，构建看起来像是卡死在 `:forge:compileJava`（实测 20 分钟
无输出，线程停在 `ErrorHandlingModuleComponentRepository...listModuleVersions`）。
直连可达时已把代理配置注释掉，并把 `max.tentatives` 从 10 降到 3。

---

## 7. 验证状态（务必阅读）

### 7.1 一条命令跑完全部无头检查

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\run-pids-check.ps1
```

它会：构建 → 导出 `:common` 运行时 classpath → 用 `javac` 编译 `tools/checks/` 下的两个
检查 → 运行它们。任何一步失败都以非零码退出。

| 检查 | 覆盖内容 |
|---|---|
| `PIDSPresetCheck` | 三个附带布局预设的解析（逐条打印组件树）、未知组件的降级、**显示行映射**语义（4 组用例比对内置渲染器的推进规则） |
| `ScriptApiCheck` | 真实 JCM 2.x 脚本经真实包装对象跑完整生命周期。除 GPU 绘制外全部真跑：Rhino 编译、`include()`、全局对象、`Text`/`Texture`/`Rectangle` 构建链。`ScriptRenderContext.dryRun()` 记录绘制调用而不是真的画 |

`ScriptApiCheck` 默认对随 mod 附带的 `pids_1a.js` 跑 **0 / 1 / 2 / 4 四种班次数**
（0 是空站台，4 超过所有 PIDS 的行数），并且每个用例跑 2 帧以捕捉状态漂移。

### 7.2 把资源包预设也纳入检查

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\run-pids-check.ps1 `
    -ResourceRoot ..\..\yjcm-recon\hkr, ..\..\yjcm-recon\pids-fixed `
    -Script `
      ..\..\yjcm-recon\hkr\assets\jsblock\scripts\hkr_pids_default.js, `
      ..\..\yjcm-recon\hkr\assets\jsblock\scripts\hkr_pids_platform.js, `
      ..\..\yjcm-recon\pids-fixed\assets\nanbin\pids\script\crt_pids_1.js
```

### 7.3 最近一次验证结果

| 项目 | 状态 | 证据 |
|---|---|---|
| `:common` / `:fabric` / `:forge` 全量 `gradle build`（含 `mergeJars`） | ✅ **BUILD SUCCESSFUL**（35s，6 executed / 15 up-to-date） | 本次运行 |
| `PIDSPresetCheck` | ✅ **ALL CHECKS PASSED** | 本次运行 |
| `ScriptApiCheck` × 4 预设 × 4 班次数 = **16 个用例** | ✅ 全部 `RESULT: SCRIPT OK` | 本次运行 |
| 游戏内视觉效果 | ✅ LCD / RV / 1A 均已实机加载确认 | 前序会话（本机 `Minecraft 1.20.1 + Forge 47.4.10 + YMTR 3.6.3`） |

被 `ScriptApiCheck` 实际跑过的预设：

| 脚本 | 来源 | 规模 |
|---|---|---|
| `jsblock:scripts/builtin/pids_1a.js` | 随 mod 附带 | 75 行 |
| `hkr_pids_default.js` | HKR 资源包 | 13.4 KB |
| `hkr_pids_platform.js` | HKR 资源包 | 8.4 KB |
| `nanbin/pids/script/crt_pids_1.js` | 重庆轨道交通资源包 | 4.7 KB |

四个预设都能解析出完整的绘制调用序列（含贴图、文本、z 序），空站台（0 班次）也不会抛异常。

> **一个反例，说明这套检查的价值**：把检查从 1 个预设扩到 3 个 × 3 种班车数时，
> 立刻抓出 3 个真 bug——`departureTime()` 未守空、缺 `worldIsRaining` / `worldIsThundering`、
> 缺 `route()`。1A 的面板偏移问题同样属于「换个型号就暴露」的类型。

### 7.4 编译通过证明不了的事

`.ps1` 检查覆盖的是脚本 API、JSON 解析与行语义，**覆盖不到矩阵变换**。
第一次进游戏实测时 PIDS 画面整体向左偏移约 40%，根因是面板原点被从尺寸反推：

```java
public float panelLeft() { return startX - panelWidth / 2F; }   // 错的
```

两个渲染器**并不是**把背景以 `startX` 居中画的：

| 渲染器 | 背景实际左边界 | `startX - panelWidth/2` | 误差 |
|---|---|---|---|
| `RenderLCDPIDS` | `startX - 21F/2` = `startX - 10.5` | `startX - 111/2` = `startX - 55.5` | 偏左 45 |
| `RenderRVPIDS` | `startX - 26F/2` = `startX - 13` | `startX - 119/2` = `startX - 59.5` | 偏左 46.5 |

`21F/2` 和 `26F/2` 这两个常数**既不等于背景宽度的一半，两者之间也没有比例关系**——
它们是原作者硬写的。所以原点必须**逐字照抄**那两个绘制调用，不能推导。
`PIDSGeometry` 因此改成显式的 `panelOffsetX`，两个渲染器分别传 `-21F/2F` 与 `-26F/2F`。

### 7.5 检查程序为什么不放进 Gradle 的 test 源集

`build.gradle` 的 `allprojects` 块把 `-Xplugin:Manifold` 加到了每一个 `JavaCompile` 任务上，
而 Manifold 编译器插件在 test 源集上会失败（`找不到符号: Manifold`），导致 `gradle build`
整体变红。放在 `tools/checks/` 下不进入任何源集，`gradle build` 保持干净。

### 7.6 已知限制

- `weather_icon` 不附带任何贴图——MTR 3/YJCM 没有可复用的天气美术资源，
  必须由预设提供三张纹理，否则该组件自动跳过。
- `PIDSComponent.COMPONENTS` 只含 JCM 那 11 种组件；组件树里若有未实现的类型，
  加载时会**一次性告警**并跳过该元素，其余组件照常渲染，不会整份预设失败。
- 脚本子系统目前只服务 PIDS。JCM 2.x 的 `mtrscripting` 还带 Lift / Vehicle / EyeCandy
  三套脚本绑定，本分支**未移植**，也没有移植其 GUI 脚本编辑器。
- 脚本的 GPU 绘制路径只有实机才能验证；无头检查覆盖不到像素结果。

---

## 8. 与 JCM 2.x 的 API 对照（供后续移植参考）

| JCM 2.x (MTR 4) | 本分支 (MTR 3) |
|---|---|
| `org.mtr.core.operation.ArrivalResponse` | `mtr.data.ScheduleEntry`（`arrivalMillis` / `trainCars` / `routeId` / `currentStationIndex`） |
| `org.mtr.mapping.holder.*`、`org.mtr.mapping.mapper.*` | 直接用 Minecraft 类 + `mtr.mappings.*` |
| `GraphicsHolder` / `GuiDrawing` | `PoseStack` + `MultiBufferSource` |
| `Identifier` | `ResourceLocation` |
| `org.mtr.mod.render.StoredMatrixTransformations` | `mtr.render.StoredMatrixTransformations` |
| `org.mtr.mod.client.MinecraftClientData` | `mtr.client.ClientData` |
| `PIDSPresetBase.BASE_SCALE` | 由 `PIDSLayout` 的 `size` 画布等比换算 |
| `Text.translatable`（MTR 4） | `mtr.mappings.Text.translatable` |
| `ComponentParser` / `PIDSComponent.componentList` | 同名接口 / `PIDSComponent.COMPONENTS`（同样可被第三方扩展） |
| `ScriptPIDSPreset` + `mtrscripting` + 内嵌 Rhino | `ScriptEngine` + `ScriptDrawCalls` + Rhino 1.7.15 依赖 |
| `PIDSWrapper` / `TextWrapper` / `TextureWrapper` / `RectangleWrapper` | `PIDSWrapper` / `ScriptDrawCalls.Text` / `.Texture` / `.Rectangle` |
| `ScriptRenderManager` / `ScriptSoundManager` | `ScriptRenderContext`（音效未移植） |

---

# 附录：1A PIDS（`jsblock:pids_1a`）预设支持

## 目标

让 `jsblock:pids_1a` 像 LCD / RV 型号一样支持「刷子右键切换显示格式（预设）」。

## 改动

只有三处，都能编译、且**界面已在游戏内确认出现**：

```java
// PIDS1A.java
public class PIDS1A extends JobanPIDSBase {                       // 原 BlockPIDSBaseHorizontal
public static class TileEntityBlockPIDS1A
        extends JobanPIDSBase.TileEntityBlockJobanPIDS { ... }    // 原 ...TileEntityBlockPIDSBaseHorizontal

// JobanClient.java
new RenderRVPIDS<>(...)                                           // 原 MTR 的 RenderPIDS<>
renderer.setScriptPanelProfile(-0.47F, -0.155F, -0.112F, 186, 60); // 1A 自己的字面量
```

**为什么前两行就够**：预设存储、自动切换、消息、站台筛选和它们的 NBT 读写**全部在
`JobanPIDSBase.TileEntityBlockJobanPIDS` 里**，而它继承的正是 `TileEntityBlockPIDS1A`
原本用的 `TileEntityBlockPIDSBaseHorizontal`；`JobanPIDSBase.use()` 也已实现了
「刷子 → 带预设框的配置界面」。1A 只是没接入这个继承体系。

而原渲染器是 MTR 的 `RenderPIDS<>`（配置界面也是 MTR 自带的 `PIDSScreen`，因此**没有预设
字段**——界面上有「页码」是识别它的标志）。换成 `RenderRVPIDS<>` 后即接入 `RenderPIDSBase`
的 `renderScripted` 路径。

## 面板偏移问题：已解决

**症状**：1A 经 `RenderRVPIDS` 渲染时走的是基类默认值（= RV 的字面量），
而 1A 的字面量是 `(-0.47, -0.155, -0.130)`，X 方向差
`|-0.47 - (-0.21)| = 0.26` 方块——四分之一格的横向偏移足以让面板陷进方块内部。

**解法**：`RenderPIDSBase` 提供可覆写的 `scriptPanelTranslateX/Y/Z()` 与
`scriptCanvasWidth/Height()`；在 1A 的注册处**用一个泛型实参写明的局部变量接住实例**，
再调用 `setScriptPanelProfile(...)`：

```java
final RenderRVPIDS<PIDS1A.TileEntityBlockPIDS1A> renderer =
        new RenderRVPIDS<>(dispatcher, PIDS1A.TileEntityBlockPIDS1A.MAX_ARRIVALS, ...);
renderer.setScriptPanelProfile(-0.47F, -0.155F, -0.112F, 186, 60);
```

Z 值在 JCM 2.x 的 `-0.130` 基础上向方块内侧收回 **0.018**，即 `-0.112`。

## 两次失败的接法（勿重复）

两次都在类型系统上失败，均**已回退、构建保持绿色**：

| 尝试 | 写法 | 报错 |
|---|---|---|
| 1 | 块 lambda + 声明 `RenderRVPIDS<PIDS1A.TileEntityBlockPIDS1A> renderer = new RenderRVPIDS<>(...)`，再调 setter | `JobanClient.java:86: 无法推断 RenderRVPIDS<> 的类型参数` |
| 2 | 让 `setScriptPanelProfile` 返回 `this` 并链式调用 `new RenderRVPIDS<>(...).setScriptPanelProfile(...)` | `JobanClient.java:85: 找不到符号` |

**失败原因**：两次都建立在「`RenderRVPIDS` 有一个与 MTR `RenderPIDS` 同形的 13 参数
构造函数」这一**未经核实的假设**上。该行最初能编译，是因为 `new RenderPIDS<>(...)` 与
`new RenderRVPIDS<>(...)` 在**替换时**恰好都通过；但一旦需要接住实例（声明变量或链式
调用），类型推断就暴露了假设不成立。

**教训**：先一次读完构造函数重载、`RegistryClient.registerTileEntityRenderer(...)` 的
参数类型、以及能编译的那四行（LCD / RV / RV-SIL）的确切写法，再动手。

## 备份与回滚点

```
tag                 backup-before-pids1a-config   （1A 改动之前的最后一版）
branch              backup/pids1a-start
jar 备份            yjcm-recon\backup-jar\
```

回滚 1A 全部改动：

```powershell
git checkout backup-before-pids1a-config -- common/src/main/java/com/jsblock/block/PIDS1A.java
git checkout backup-before-pids1a-config -- common/src/main/java/com/jsblock/JobanClient.java
```
