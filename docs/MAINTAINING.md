# 维护与开发指南

这份文档写给**接手这个分支的人**（也包括下一个 AI 会话）。它只讲"东西在哪、改哪里、怎么验证"，
不讲移植的历史与取舍——那些在 [`MTR3-PIDS-PORT.md`](../MTR3-PIDS-PORT.md)，
逐版本的"做了什么/没做什么"在 [`CHANGELOG.md`](../CHANGELOG.md)。

先读这三份，顺序不要反：

1. 本文件：模块地图 + 常见任务 + 验证命令；
2. `MTR3-PIDS-PORT.md` §4：脚本子系统的设计与每一处踩坑；
3. `V2-API-COVERAGE.md`：JCM 2.x 每个脚本 API 在本分支的状态。

---

## 1. 目录地图

| 路径 | 作用 | 什么时候要动 |
|---|---|---|
| `common/src/main/java/com/jsblock/pids/` | 组件式布局（`components` 数组），11 种组件 | 新增一种 JSON 组件 |
| `common/src/main/java/com/jsblock/script/` | JavaScript 预设：引擎、全局对象、绘制调用、异步执行、画布 | 新增脚本 API、改执行模型 |
| `common/src/main/java/com/jsblock/render/` | 三种渲染路径 + 投影仪 + 像素化 | 面板画错、性能问题 |
| `common/src/main/java/com/jsblock/screen/` | 配置界面（AmberUI 版 + `Legacy*` 旧版） | 改界面布局/文案 |
| `common/src/main/java/com/jsblock/amberui/` | JCM 2.3 的 GUI 工具箱移植（MIT，见 `LICENSE_JCM`） | 一般不改；改前先看 §7 |
| `common/src/main/java/com/jsblock/data/` | 预设解析（`PIDSPreset`）、像素化配置 | 预设 JSON 加字段 |
| `common/src/main/java/com/jsblock/client/` | 客户端配置、资源重载、预设注册表 | 加配置项、改内置预设 |
| `common/src/main/java/com/jsblock/packet/` | C2S / S2C 包（含快捷配置工具） | 加与方块交互的功能 |
| `common/src/main/resources/assets/jsblock/` | 预设 JSON、语言键、贴图、内置脚本 | 加预设/加文案 |
| `fabric/`、`forge/` | 两个加载器的注册与事件 | 平台相关的东西 |
| `tools/run-pids-check.ps1` + `tools/checks/` | 11 个无头检查 | 加检查、修检查 |
| `docs/history/` | 历史记录（实机日志等），不参与构建 | 只读 |

`common` 里没有任何加载器 API 的直接调用，平台差异走 `mtr.mappings.*` 与 `fabric/`/`forge/`
下的注册函数；往 `common` 里加平台代码会让两边构建一起挂。

## 2. 一次渲染的生命周期

```
RenderPIDSBase.render()                 ← 每帧、每块面板
  ├─ 取预设、解析自定义信息、收集时刻表
  ├─ 到达/发车自动切换（可能换掉 preset）
  └─ 三条路选一条：
       preset.layout != null      → renderLayout()      组件式
       preset 有 scriptFiles      → renderScripted()    脚本
       都没有                     → 子类自己的内置布局（RenderRVPIDS / RenderLCDPIDS）
```

`renderScripted()` 里再分两条：开像素化时先渲染到离屏 FBO 再合成，否则脚本的每个绘制调用
**直接画到世界**。两条路的坐标空间不同（canvas vs 方块面板几何），改绘制相关的东西前先确认
自己在哪条路上。

脚本执行是**异步**的：渲染线程提交一次 pass 并重放上一帧的 `RecordedCall` 列表，首帧同步执行。
`PIDSWrapper` 及其子对象因此必须在**渲染线程的构造函数里**把数据解析完（不能懒加载），
这是硬约束，见 §4.23。

## 3. 常见任务

### 3.1 加一个内置预设

1. 脚本放 `common/src/main/resources/assets/jsblock/scripts/builtin/<id>.js`；
2. 在 `assets/jsblock/joban_custom_resources.json` 的 `pids_images` 里加一条，至少写
   `id` / `name` / `builtin: true` / `thumbnail` / `scriptFiles`；
3. 跑 `tools\run-pids-check.ps1`——它会用真实引擎跑一遍新脚本，`RESULT: SCRIPT OK` 才算过；
4. 缩略图必须存在，`PIDSPresetCheck` 会校验。

内置预设**只从这一个 JSON 来**。不要在 Java 里再硬编码一份：历史上那三份硬编码副本占用了
同名 id，导致 JSON 条目被跳过、界面按 id 显示。

### 3.2 加一个脚本 API

| 要加的东西 | 改哪里 |
|---|---|
| 一个全局函数/对象 | `ScriptEngine.registerGlobals()`，再在 `ScriptEngine` 里加实现类 |
| `pids` 上的一个字段 | `PIDSWrapper`（构造函数里解析完，加 getter），必要时同步 `V2-API-COVERAGE.md` |
| 一种新的绘制调用 | `ScriptDrawCalls`（builder + `draw()`）+ `RecordedCall`（录制/重放），两处都要 |
| 新的 `ctx` 方法 | `ScriptRenderContext`，并考虑录制路径 |

**只加 `ScriptDrawCalls` 而不加 `RecordedCall`**，异步模式下这个调用会被丢掉（重放时找不到类型）；
`ScriptAsyncCheck` 会覆盖"录制→重放"一致性，跑它就知道了。

### 3.3 加一个配置项

`ClientConfig`：加字段 → `loadConfig()` 读 → `writeConfig()` 写 → 加 getter/setter
（**setter 不落盘**，界面关闭时统一 `writeConfig()`）→ 在 `ClientConfigScreen` 里用
`checkboxForConfig(...)` 加一行，并补 `en_us.json` / `zh_cn.json` 的语言键。
语言键漏了会在界面上显示裸键，`run-pids-check` 检查不出来，只能实机看。

### 3.4 加一个物品/方块

注册点在 `Joban.init(...)`：`registerBlockItem`（方块+物品+创造页）、`registerItem`（纯物品）、
`registerTabItem`（纯物品并进本模组的创造页）。创造页 id 在 `ItemGroups`。

Forge 侧注意：**不要在 `JobanForge` 的静态初始化里调 `item.get()`**，那时注册表已冻结，
会抛 `Registry is already frozen`；要在 `ITEMS.register(path, supplier)` 的 supplier 里构造。

### 3.5 改界面

三个 PIDS 配置界面都继承 `PIDSScreen`（AmberUI 版）或 `LegacyPIDSScreen` 系列（旧版）。
列表由 `ListViewWidget` 组织，一行 = `ContentItem(label, widget)`；带图标用
`setIconCallback`。**列表高度不要写死百分比**：JCM 2.3 的 75% 上限会把 YJCM 自己的
自动切换行挤出可视区，现在按"标题与底部按钮之外的全部空间"算。

## 4. 构建与验证

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-21'      # 必须 Windows 路径
cd <repo>
.\gradlew build --no-daemon --console=plain --max-workers=1
```

产物：`build/MTR-YJCM-1.20-<mod_version>.jar`（Forge + Fabric 合并包，装这一个）。

改完代码至少要跑：

```powershell
.\tools\run-pids-check.ps1 -SkipBuild     # 期望最后一行 RESULT: ALL CHECKS PASSED
```

它包含 11 个检查：组件预设解析、像素化尺寸、沙箱、importer、路径、画布、文件存储、大小写、
字体回退、异步录制/重放、HKR 终点站规则，另外用真实引擎跑 `pids_1a.js`（arrivals=0/1/2/4）。
这些检查**不需要游戏**，但覆盖不到 GUI 与实机渲染——那两类只能进游戏看。

版本号只有一个来源：`gradle.properties` 的 `mod_version`，
`Joban.VERSION` 从产物元数据里读回来，不要在 Java 里写死。

### 4.1 平台验证：Forge 和 Fabric 必须都跑

**两边都要进游戏看一次，不能只测 Forge。** 不是形式主义，Fabric 运行时会用 intermediary 映射重命名
所有 Minecraft 类，Forge 用 Mojang 名，于是任何**按类名字符串**做判断的代码会在其中一边失效，而另
一边完全正常：

| 写法 | Forge | Fabric |
|---|---|---|
| `Class.forName("net.minecraft.resources.ResourceLocation")` | 可用 | **失败**，运行时叫 `net.minecraft.class_2960` |
| 沙箱白名单里写死 `"net.minecraft.resources.ResourceLocation"` | 可用 | **永远匹配不上**，合法调用被当成越权 |
| `SomeClass.class.getName()` | 正确 | 正确（自动跟随映射） |

已踩过的坑：脚本沙箱按名字放行 `ResourceLocation`，Fabric 上把引擎自己返回的值也拦掉，PIDS 面板变
全黑，而 Forge 一切正常；版本号用 `getResourceAsStream("/fabric.mod.json")` 读，Fabric 上所有模组
共用一条类路径，读到了 architectury 的 `9.2.14`。

**规则**：跨平台的判断一律走 `Class` 对象（`Class.getName()`、`isAssignableFrom`），不要写死映射名；
需要写死名字时，两边各验一次。

## 5. 已知限制（改之前先看）

| 现象 | 原因 | 能不能修 |
|---|---|---|
| 只有贴图的板块（投影仪、Japanese PIDS）背后若有通透玻璃、别的模组的屏幕，那些东西会**透上来** | MTR 只暴露两种渲染图层：不写深度的半透明、写深度的不透明。面板需要"半透明且写深度"，拿不到；自定义 `RenderType` 所需的 `create` 与 `RenderStateShard` 常量都不是 public，生产环境还有 SRG 重映射 | 需要 Access Transformer / Access Widener，或对目标模组做 mixin。**当前未解决**，临时办法是在板子后面放实体方块 |
| 快照上传与画布重绘在极端情况下可能延迟一帧 | 异步执行模型（重放上一帧） | 设计取舍，见 §4.23 |
| 像素化预设仍同步执行 | 离屏 FBO 的生命周期就是这一帧，重放它风险更大 | 可以做成每面板常驻 FBO，属于重构 |
| Rhino 未重定位（`org.mozilla.javascript` 原样打进 jar） | 与其他自带 Rhino 的模组可能撞版本 | 可以做（`shadowJar { relocate ... }` + 白名单同步），但收益仅限运行时隔离 |

## 6. 约定

- 文件编码 UTF-8、换行 LF（`git` 在 Windows 上会提示 CRLF，别提交 CRLF 版本）；
- 注释写**为什么**，不写"这行在做什么"；每处非常规写法都应有存在的理由（很多是踩坑换来的）；
- 面板/脚本相关的新代码，尽量加一个无头检查覆盖，`tools/checks/` 里已有 11 个可参考；
- 不要动 `amberui/` 的对外行为——那是 JCM 2.3 的移植，改动会让上游 diff 变脏；
- 这个分支的代码有相当比例由 AI 参与产出：**结论要可验证**，注释里凡是"实测/日志"的说法
  都应能在 `docs/history/` 或 CHANGELOG 里找到出处。

## 7. 故障排查

| 症状 | 常见原因 |
|---|---|
| `Registry is already frozen` | 在 mod 类的静态初始化里提前构造了注册对象 |
| `@SubscribeEvent ... not a subtype of IModBusEvent` | 事件注册到了错误的 bus（`LeftClickBlock`/`RightClickBlock` 属于 Forge bus） |
| 启动即退、`zip END header not found` | `mods/` 里有下坏的 jar（下载中断留下的 HTML 页面） |
| 界面显示 `gui.jsblock.xxx` 裸键 | 语言键漏了，`en_us.json` / `zh_cn.json` 都要补 |
| 面板黑屏但日志无异常 | 预设既无 `components` 也无 `scriptFiles`，且没有背景图 |
| 脚本报错但面板不黑 | 正常：失败的 pass 保留上一帧，错误进聊天栏/日志 |
