# MTR 3 PIDS 组件化布局引擎 —— 移植说明

本分支把 **JCM（Joban Client Mod）2.x / MTR 4.0** 的 PIDS 组件化预设体系移植到
**YJCM / MTR 3（YMTR 3.6.3，MC 1.20.1）**。

---

## 1. 为什么要移植这个

移植前，YJCM 与 JCM 在 PIDS 上的差距是**架构性**的：

| | YJCM（移植前） | JCM 2.x（MTR 4.0） |
|---|---|---|
| 布局 | 每个元素位置**硬编码**在 `RenderLCDPIDS` / `RenderRVPIDS` 里 | `PIDSComponent` 声明式布局（`x/y/width/height`） |
| 可扩展性 | 想改版式只能改 Java、重新编译 | 资源包写 JSON 即可换版式 |
| 组件类型 | 无 | 11 种（时钟 / 天气 / 站名 / 目的地 / ETA / 车厢数 / 自定义文本 / 自定义贴图 / 轮播 …） |
| 预设 | 背景图 + 字体 + 颜色 + 行可见性 | 上述全部 **+ 完整组件树** |

YJCM 已有预设系统（`com.jsblock.data.PIDSPreset`，从 `JobanCustomResources` 载入，
支持到站/离站自动切换、`{time}`/`{weather}` 变量替换），但它描述的是**外观**，
不是**版式**。本分支补上的正是版式这一层。

---

## 2. 新增模块

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
└── component/
    ├── ClockComponent.java                  时钟
    ├── WeatherTextComponent.java            天气文字
    ├── WeatherIconComponent.java            天气图标
    ├── StaticCustomMessageComponent.java    固定文本（含变量替换）
    ├── CustomTextureComponent.java          自定义贴图
    ├── CycleComponent.java                  轮播容器
    ├── PlatformComponent.java               站台名
    ├── StationNameComponent.java            车站名
    ├── ArrivalDestinationComponent.java     终点站
    ├── ArrivalETAComponent.java             预计到达时间
    └── ArrivalCarComponent.java             车厢数
```

### 改动的既有文件

| 文件 | 改动 |
|---|---|
| `common/.../data/PIDSPreset.java` | 新增可空字段 `layout`；`fromJson` 解析 `components` 数组。**没有 `components` 的旧预设行为完全不变。** |
| `common/.../render/RenderPIDSBase.java` | 新增布局钩子：预设带 `layout` 时整版交给组件引擎，跳过硬编码路径；新增 `getLayoutGeometry()` |
| `common/.../render/RenderLCDPIDS.java` | 实现 `getLayoutGeometry()` |
| `common/.../render/RenderRVPIDS.java` | 实现 `getLayoutGeometry()` |
| `forge/.../mappings/ForgeConfig.java` | **新建**（原仓库缺失该文件，见 §4） |
| `fabric/build.gradle`、`forge/build.gradle` | `classifier` → `archiveClassifier`（见 §4） |

---

## 3. 预设 JSON 格式

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
（内置渲染器的 `entryIndex` 只在可见行推进；见 `RenderLCDPIDS.java` 结尾的
`if(hideArrivals[i]) continue;` 与 `RenderRVPIDS.java` 的对应位置。）

### 随 mod 附带的示例预设

`common/src/main/resources/assets/jsblock/joban_custom_resources.json` 内置了三个可直接使用的布局预设，
同时也是本引擎的活体测试用例：

| id | 演示内容 |
|---|---|
| `layout_lcd` | 复刻内置 LCD PIDS：站名 + 时钟表头，4 行「终点站 + ETA」 |
| `layout_rv` | 复刻内置 RV PIDS，并补上内置渲染器只在特定 tick 闪现的**车厢数** |
| `layout_notice` | 演示 `cycle` 组件：天气 / 日期 / 在线人数三屏轮播 |

它们是普通的 `pids_images` 预设，资源包可以用**相同的 id** 在更高优先级覆盖，也可以新增自己的。

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

## 4. 顺带修复的两个上游缺陷

移植过程中发现 YJCM 公开仓库本身**编不过**，两处问题与本分支的功能无关，但必须修复：

### 4.1 缺失 `ForgeConfig.java`

`forge/src/main/java/com/jsblock/JobanForge.java` 第 4 行 `import com.jsblock.mappings.ForgeConfig;`，
但该文件**从未被提交**——它位于 `com.jsblock.mappings` 这个由 `setupFiles` 任务生成、
且未被 `.gitignore` 覆盖却也没进版本控制的目录里。`setupFiles` 下载的
`Minecraft-Mappings-1.20.zip` 里只有 `FabricRegistryUtilities.java` 和 `ForgeUtilities.java`，
不含 `ForgeConfig`。

已按其在 Fabric 侧的对等物 `ModMenuConfig` 还原：把 YJCM 的 `ConfigScreen`
注册为 Forge 的配置界面扩展点（`ConfigScreenHandler.ConfigScreenFactory`）。

### 4.2 Gradle 8 不兼容

`fabric/build.gradle:39,45` 与 `forge/build.gradle:45,51` 使用了 `classifier`，
该 API 在 Gradle 8.0 已被移除（项目原本面向 Gradle 7）。已改为等价的 `archiveClassifier`。

---

## 5. 构建

本机原本没有 git / gradle，YJCM 仓库也缺 `gradle/wrapper/`。当前可用配置：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Zulu\zulu-21'
$gradle = "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.8-bin\dl7vupf4psengwqhwktix4v1\gradle-8.8\bin\gradle.bat"
cd work\yjcm-mtr3
& $gradle build --no-daemon --console=plain --max-workers=1
```

### MTR 3 编译依赖从哪来

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

---

## 6. 验证状态（务必阅读）

| 项目 | 状态 |
|---|---|
| `:common` / `:fabric` / `:forge` 全量 `gradle build` | ✅ **已通过**（含 `mergeJars`） |
| 附带预设的 JSON 解析（真实调用 `PIDSPreset.fromJson`） | ✅ **已通过** —— 见下方 `tools/run-pids-check.ps1` |
| 未知组件类型的降级行为 | ✅ **已通过** —— 告警并跳过该元素，其余组件保留 |
| **显示行映射语义**（隐藏行不消耗班次） | ✅ **已通过** —— 4 组用例逐一比对内置渲染器的推进规则 |
| **游戏内视觉效果** | ✅ 已在 `Minecraft 1.20.1 + Forge 47.4.10 + YMTR 3.6.3` 实测加载；**首轮实测发现并已修正横向偏移**（见下） |

### 首个游戏内 bug：面板原点不能用尺寸反推（已修正）

第一次进游戏实测时，PIDS 画面整体向左偏移约 40%，右侧露出一大块黑色。

根因是我最初的实现里写了：

```java
public float panelLeft() { return startX - panelWidth / 2F; }   // 错的
```

前提是"背景图以 `startX` 居中"，但两个渲染器**并不是**这么画的：

| 渲染器 | 背景实际左边界 | `startX - panelWidth/2` | 误差 |
|---|---|---|---|
| `RenderLCDPIDS` | `startX - 21F/2` = `startX - 10.5` | `startX - 111/2` = `startX - 55.5` | 偏左 45 |
| `RenderRVPIDS` | `startX - 26F/2` = `startX - 13` | `startX - 119/2` = `startX - 59.5` | 偏左 46.5 |

`21F/2` 和 `26F/2` 这两个常数**既不等于背景宽度的一半，两者之间也没有比例关系** —— 它们是原作者硬写的。所以原点必须**逐字照抄**那两个绘制调用，不能从尺寸推导。

修正方式：`PIDSGeometry` 增加显式的 `panelOffsetX`，两个渲染器分别传 `-21F/2F` 与 `-26F/2F`；`panelLeft()` 改为 `startX + panelOffsetX`。

> 这个 bug 说明**编译通过与解析测试通过都证明不了版式正确** —— 只有真的把方块放下去看才能发现。`.ps1` 检查覆盖的是 JSON 解析与行语义，覆盖不到矩阵变换。

### 怎么验证（不需要启动游戏）

```powershell
powershell -NoProfile -File tools\run-pids-check.ps1
```

该脚本会：构建 → 导出 `:common` 运行时 classpath → 用 `javac` 编译
`tools/checks/com/jsblock/pids/PIDSPresetCheck.java` → 运行它，逐条打印每个附带预设解析出的
组件树，并单独验证「一个未知组件不会拖垮整份预设」。任何一步失败都会以非零码退出。

这个检查不是摆设——它当场抓到了一个真实缺陷：三个附带预设的字体全部为 `null`。
原因是上游 `PIDSPreset.fromJson` 读的键是 **`fonts`（复数）**，而字段和所有渲染器都叫
`font`。现在两边都接受（见 `PIDSPreset.java`），并且资源包无论写哪个键都不会静默丢字体。

> 检查程序**刻意不放进 Gradle 的 test 源集**：`build.gradle` 的 `allprojects` 块把
> `-Xplugin:Manifold` 加到了每一个 `JavaCompile` 任务上，而 Manifold 编译器插件在 test
> 源集上会失败（`找不到符号: Manifold`），导致 `gradle build` 整体变红。放在
> `tools/checks/` 下不进入任何源集，`gradle build` 保持干净。

布局渲染的矩阵变换刻意复刻了 `RenderLCDPIDS` / `RenderRVPIDS` 绘制背景时所用的那一段
（平移到方块中心 → 朝向/90° 旋转 → 移到面板原点 → 除以 `scale`），因此理论上会精确
落在面板矩形上；但**这一条只有进游戏才能证实**。

### 已知限制

- `weather_icon` 不附带任何贴图——MTR 3/YJCM 没有可复用的天气美术资源，
  必须由预设提供三张纹理，否则该组件自动跳过。
- 未实现 JCM 的 `ScriptPIDSPreset`（Rhino 脚本预设）。它依赖 JCM 2.x 的
  `mtrscripting` 整个子系统，属于另一个量级的移植。
- `PIDSComponent.COMPONENTS` 目前只含 JCM 那 11 种组件；JCM 的组件树里若还有本分支
  未实现的类型，加载时会**一次性告警**并跳过该元素，其余组件照常渲染，不会整份预设失败。

---

## 7. 与 JCM 2.x 的 API 对照（供后续移植参考）

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
