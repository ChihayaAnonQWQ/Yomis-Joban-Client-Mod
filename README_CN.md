# Yomi's Joban Client Mod-JSPIDS

[English](README.md) | **中文**

Joban Client Mod 的非官方版本。

**Minecraft 1.20.1，Forge 与 Fabric 通用同一颗 jar。** 两边各自还需要在旁边装什么 —— 对应平台的
MTR 3（YMTR）、Fabric API、Architectury API —— 见[平台与环境要求](#平台与环境要求)。

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

## 平台与环境要求

| | |
|---|---|
| Minecraft | **1.20.1**。模组自己的清单只接受 1.20 与 1.20.1：`META-INF/mods.toml` 写的是 `[1.20,1.20.2)`，`fabric.mod.json` 写的是 `>=1.20 <=1.20.1` |
| 加载器 | **Forge 与 Fabric，同一颗 jar 都能装** |
| MTR | MTR 3，也就是 **YMTR** 这一支 —— 1.20.1 上对应 3.6.3 —— 而且必须是**你那个平台**的那一份 |

**「同一颗 jar」是当场可以验证的。** `build/MTR-YJCM-1.20-1.2.12-JSPIDS-2.5.jar` 是合并产物：
把它打开，两份清单并排躺在根目录，各自带着自己的 mixin 配置。

| jar 里面 | Forge | Fabric |
|---|---|---|
| 模组清单 | `META-INF/mods.toml`（`modLoader = "javafml"`、`modId = "jsblock"`） | `fabric.mod.json`（`id = "jsblock"`） |
| 入口 | `forge/com/jsblock/` 下的 `@Mod` 类 | `entrypoints.main` → `fabric.com.jsblock.JobanFabric`，另有 `.client`、`.modmenu` |
| Mixin | `META-INF/MANIFEST.MF` 里的 `MixinConfigs: forge-jsblock-common.mixins.json` | `"mixins": ["fabric-jsblock-common.mixins.json"]` |
| Minecraft | `minecraft` `[1.20,1.20.2)` | `minecraft` `>=1.20 <=1.20.1` |

没有单独的 Forge 下载，也没有单独的 Fabric 下载：发布的就是这一个文件。所以下面这张表说的不是
「该下哪一个」，而是「它旁边还得装什么」。

### 两边分别要装什么

| 平台 | 需要装什么 | 说明 |
|---|---|---|
| **两边都要** | 本模组：`MTR-YJCM-1.20-1.2.12-JSPIDS-2.5.jar` 放进 `mods/` | 合并包；两个加载器装的是同一个文件 |
| **两边都要** | MTR 3 / YMTR 3.6.3 —— **对应你平台的那一份** | Forge 用 `MTR-forge-1.20.1-3.6.3.jar`，Fabric 用 `MTR-fabric-1.20.1-3.6.3.jar`。MTR 每个加载器各一个 jar，两者不能互换；两份清单都把 `mtr` 列为必需依赖 |
| **两边都要** | Architectury API `9.2.14` | Forge 侧：`META-INF/mods.toml` 把 `architectury` `[1.26.37,)` 标成 **mandatory**。Fabric 侧：`fabric.mod.json` 现在也列上了它 —— `architectury` `>=9`，9.x 就是这个构件在自己的 `fabric.mod.json` 里写的版本号 —— 所以缺依赖时是**加载器在启动时报出缺的是哪一个**，而不是等到已通过检查的类被载入时才 `NoClassDefFoundError`。模组自己的类链接了 `dev.architectury.event.Event`、`ClientGuiEvent`、`ClientTickEvent`（在 `com.jsblock.JobanClient`，一个声明过的入口），而 jar 里并没有打包它们。版本取自 `gradle.properties` 的 `architectury_version` |
| **Forge** | 1.20.1 的 Forge，build 36 或更高 | `META-INF/mods.toml` 写的是 `forge` `[36,)`。Forge 侧没有别的必需项 |
| **Fabric** | Fabric Loader | `fabric.mod.json` 写的是 `fabricloader: *`；本构建编译与运行所用的是 `0.19.5`（`gradle.properties`） |
| **Fabric** | Fabric API `0.83.0+1.20` | `fabric.mod.json` 里写作 `fabric: *`；版本取自 `gradle.properties` 的 `fabric_api_version` |
| **Fabric** | 可选：ModMenu `7.0.1` | 只影响配置界面的按钮。`fabric.mod.json` 里有 `modmenu` 入口（`com.jsblock.ModMenuConfig`）；版本取自 `gradle.properties` 的 `mod_menu_version` |

### 已经实机跑过的，和还没跑过的

**Forge 侧是真正启动过、用过的。** 那几次是 `Minecraft 1.20.1 + Forge 47.4.10 + YMTR 3.6.3`，
`mods/` 里放的是本分支构建出的 jar；下面修掉的那些缺陷就是从这些运行里来的：脚本管线同时画出多个资源包的预设、
预设通过 `Files` 把状态写进磁盘、投影仪与它的瞄准线、以及几个 PIDS 配置界面。
日志见 `MTR3-PIDS-PORT.md` 的 §7.8 与 §7.9。

**Fabric 侧现在也在 Fabric 实例里启动过了。** 在 Fabric 1.20.1 实例上可以启动、正常使用（维护者实测）。
`gradle build` 能编出它，合并步骤把它放进同一颗 jar，产物在：

| 路径 | 是什么 |
|---|---|
| `fabric/build/libs/fabric-1.20-1.2.12-JSPIDS-2.5.jar` | Fabric 侧、重映射后的 jar，合并步骤的输入 |
| `forge/build/libs/forge-1.20-1.2.12-JSPIDS-2.5.jar` | Forge 侧，合并步骤的输入 |
| `build/MTR-YJCM-1.20-1.2.12-JSPIDS-2.5.jar` | 合并包，要装的是这一个 |

这次 Fabric 侧是「能启动、能正常用」这一级的确认，不是 Forge 那份逐项过一遍的清单 —— 逐项验证过的仍然
只有 Forge 侧。上面那张平台表的读法仍然是「清单和字节码要求这样」，Fabric 1.20.1 实例就是照它装起来的。

## 安装

两个加载器放的是同一个文件、放的也是同一个地方，不一样的是它旁边那颗 MTR。

### Forge

1. 1.20.1 的 Forge —— build 36 或更高（`META-INF/mods.toml` 里的 `forge` `[36,)`）。
2. `mods/` —— MTR 3 / YMTR 的 **Forge** 版：`MTR-forge-1.20.1-3.6.3.jar`。
3. `mods/` —— Architectury API。`META-INF/mods.toml` 把它标成必需，缺了 Forge 会直接拒绝启动。
4. `mods/` —— `MTR-YJCM-1.20-1.2.12-JSPIDS-2.5.jar`。

### Fabric

1. 1.20.1、带 Fabric Loader 的 Fabric 档案。
2. `mods/` —— MTR 3 / YMTR 的 **Fabric** 版：`MTR-fabric-1.20.1-3.6.3.jar`。
3. `mods/` —— Fabric API。
4. `mods/` —— Architectury API。`fabric.mod.json` 现在声明了它（`architectury` `>=9`），和 Forge 侧一样是必需依赖，理由见上表。
5. `mods/` —— `MTR-YJCM-1.20-1.2.12-JSPIDS-2.5.jar`。
6. 可选 —— ModMenu，想让配置界面出现在模组列表里就装。

### 容易装错的几种情况

| 装成了什么 | 会发生什么 |
|---|---|
| Fabric 实例里放了 MTR 的 **Forge** 版（或反过来） | MTR 每个加载器各一个 jar，放错的那颗对该加载器来说根本不是模组。装与档案匹配的那一份 |
| 没装 MTR，或装了别的 Minecraft 版本的那份 | 两份清单都把 `mtr` 列为必需依赖，模组不会加载 |
| Fabric 上缺 Fabric API 或缺 Architectury API | 两者都写在 `fabric.mod.json` 的 `depends` 里（`fabric: *`、`architectury: >=9`），加载器会直接拒绝加载，并点名缺的是哪一个 |
| 1.20.2 以上，或 1.19.x 的档案 | 清单会拒绝：Forge 侧 `[1.20,1.20.2)`，Fabric 侧 `>=1.20 <=1.20.1` |
| 往 `mods/` 里放了 `fabric/build/libs/fabric-1.20-….jar` 或 `forge/build/libs/forge-1.20-….jar` | 那是合并步骤的**输入**，不是发布件。要装的是合并后的 `build/MTR-YJCM-1.20-….jar` |

## 功能一览

每一行在下面都有展开，这里只是地图。

| | |
|---|---|
| PIDS 预设 | 三种，都在方块自己的配置界面里选：JCM 2.x 的 **JavaScript** 预设（`scriptFiles`）、JSON 声明的**组件式**布局（`components`）、以及**只有贴图**的传统资源包。既不声明 `components` 也不声明 `scriptFiles` 的预设，仍然走 YJCM 原来那套硬编码渲染路径 |
| PIDS 投影仪 | `jsblock:pids_projector` —— 面板悬在空中，自带显示格式、偏移、旋转、缩放、逐行信息与隐藏月台号 |
| 像素化与点阵 | 默认关闭：资源包声明它是按什么屏画的，玩家决定要多粗（按预设）。每个键与三种画布的精确比例见 [docs/pixelation-guide.zh.md](docs/pixelation-guide.zh.md) |
| 脚本 API | 逐条对着官方 JCM 2.x 脚本文档比对，而不是「手头这些预设恰好用到什么」；对照表是 [`V2-API-覆盖表.md`](V2-API-覆盖表.md) |
| 脚本沙箱 | 类访问白名单、关闭前的警告界面、面向玩家的失败提示，以及记录预设每一次绘制调用的诊断 |
| 无头检查 | `tools/run-pids-check.ps1` 不开游戏，直接用真实引擎跑随模组附带的预设与脚本 API |

### 哪些方块有配置界面

| 方块 | 界面 |
|---|---|
| `jsblock:pids_1a`、`jsblock:pids_4`（LCD PIDS） | Joban PIDS 界面：显示格式（预设）、月台筛选、逐行信息、逐行隐藏、自动切换（发车另有一套自动切换） |
| `jsblock:pids_rv`、`jsblock:pids_rv_sil`、`jsblock:pids_rv_sil_2` | RV PIDS 界面，字段相同，另外多一个「隐藏月台号」 |
| `jsblock:pids_projector` | 投影仪自己的界面：显示格式、偏移、旋转、缩放、逐行信息、逐行隐藏、隐藏月台号、月台筛选 |
| 模组本身 | `ConfigScreen` —— Fabric 从 ModMenu 进，Forge 从模组列表进。客户端设置都在这里，包括脚本调试开关 |

> **不是每块板子都能给自定义信息。** 上游 JCM 2.x 自己也有几个 PIDS 界面不提供信息输入框 —— MTR 4 上一样 ——
> 那是界面本身的设计，不是这次移植漏掉的。这一点写在 [docs/pixelation-guide.zh.md](docs/pixelation-guide.zh.md) 里，
> 免得下一个人再为此花一个晚上。

## 版本差异

上游的 Joban 客户端 Mod 有三种变体，先弄清本 fork 属于哪一支，因为它决定了某个资源包或预设能不能在这里跑：

| 变体 | 面向 | 游戏版本 | 上游状态 | 本 fork |
|---|---|---|---|---|
| **v1** | MTR 3 | Fabric / Forge 1.16.5 – 1.20.1 | 已停止支持 | **本 fork 的基础** —— YJCM 是 v1 的分支 |
| **v2** | MTR 4 | Fabric / Forge 1.16.5 – 1.20.4 | 持续支持的那一支 | 本 fork 回移的就是它的 PIDS 体系 |
| **neo** | NeoMTR（MTR 3 的衍生版） | Fabric / NeoForge 1.21.1 | 半支持 | 不是本 fork 的目标 |

版本号里的 **JSPIDS** 指的就是这次回移：**把 v2 的 PIDS 体系搬到 v1 上** —— JavaScript 预设、组件式布局、
PIDS 投影仪，以及它们所依赖的那套脚本 API。

这里的 MTR 3 指 **YMTR** 这一支，对应 Minecraft 1.20.1。上游现在只维护 v2（MTR 4），所以如果你在
MTR 3 上，本 fork 就是还在继续做 PIDS 的那条线。

如果你是 MTR 或 JCM 的新手、还没有存档，上游的建议是对的：从 **MTR 4 + JCM v2** 开始。
本 fork 是给那些**已经在 MTR 3 上**的存档、车站和资源包用的。

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
| PIDS 投影仪 | `jsblock:pids_projector` —— JCM 2.x 的投影仪：面板悬在空中，自带显示格式、偏移、旋转、缩放、逐行信息与隐藏月台号 |

既不声明 `components` 也不声明 `scriptFiles` 的预设，仍然走原来那套硬编码渲染路径，未做改动。

### PIDS 投影仪

JCM 2.x 的 PIDS 投影仪已移植：`jsblock:pids_projector` 把一块乘客资讯面板投到空中，投在哪由你摆。

| | |
|---|---|
| 摆放 | 偏移、旋转、缩放 —— 一台投影仪可以把面板放到几格之外、带角度、任意大小 |
| 内容 | 显示格式（预设）、MTR 的月台筛选、逐行自定义信息、逐行隐藏、隐藏月台号码 |
| 预设 | 三种都支持：JCM 2.x 的 `.js` 预设、JSON `components` 布局、以及只有贴图的传统资源包 |
| 瞄准 | 手拿刷子时面板会被框出来；投影仪**未旋转**时还会显示 JCM 2.x 那四条红色投影范围线 |
| 界面 | 拿刷子右键打开，打开时显示的是面板当前的内容，不会是空白框 |

面板本身不是方块、也没有碰撞 —— 瞄准的是投影仪方块。

面板上的文字就是脚本用 `pids.getCustomMessage(i)` 读到的内容，隐藏行对应 `pids.isRowHidden(i)`。

**传统资源包的面板按内置尺寸绘制**，与脚本画布不是一个尺寸 —— 这两套尺寸在 JCM 2.x 里来自不同的部分。
脚本预设与 components 预设占满画布（投影仪的缩放作用在它上面），内置素材则跟随同一套偏移、旋转与缩放。

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
| 资源 | `Resources`（含 `getMTRVersion`、`getAddonVersion`、`readBufferedImage`） `TextUtil` |
| 世界 | `MinecraftClient` `MinecraftClient.localPlayer()` `PlayerEntity` |
| MTR 客户端数据 | `MTRClientData`（= `mtr.client.ClientData`：`STATIONS` / `PLATFORMS` / `SCHEDULES_FOR_PLATFORM` / `DATA_CACHE`。**不是** `MinecraftClient` 的别名，理由见下） |
| 声明式组件 | `ctx.parseComponent(json)` → `render(ctx)` / `canRender()` / `x()` / `y()` / `width()` / `height()` / `type()`；也可以 `ctx.draw(component)` |
| 运行时画布 | `GraphicsTexture(w, h)`：`graphics` / `bufferedImage` / `identifier`、`upload()`、`close()`、`clear` / `fillRect` / `drawText` / `measureText` / `drawTexture` |
| 耗时操作 | `BackgroundWorker` `Networking` `NetworkResponse` `DataReader` |
| 持久化 | `Files`：`read` / `readData` / `saveData` / `deleteData` / `hasData` —— 即 v2 的 `FilesUtil`，同名、同签名、同两个根目录（`<游戏目录>` 与 `<游戏目录>/data/mtrscripting`） |
| 声音 | `ctx.getSoundManager()` `SoundManager` `TickableSoundInstance` |
| 杂项 | `console` `print` `include` `SCRIPT_INPUT` |
| 面板本身 | `pids.*`、`arrivals().*`、`arrival.*`、`pids.station()`、`route().getPlatforms()`、`ctx.setAutoZOrdering()` `ctx.setZOrderStep()` |
| 本分支独有 | `arrival.routeType` 与 `arrival.isLightRailRoute`（MTR 3 的 `Route.routeType`：`NORMAL` / `LIGHT_RAIL` / `HIGH_SPEED`）。MTR 4 没有线路制式，而**两个版本都没有「快慢车」这个标志位**——在意的包是把种别写进线路的**线路号**里再做关键字匹配（见 HKR 的 `getColorByKeyword`）。MTR 3 的线路号藏在界面文案为 **Has Route Number** 的那个勾选框后面，勾上它，那些颜色才会在这里出现 |

**还缺的，按组列出。** 约 149 条，已知没有任何 PIDS 预设会调它们；列在这里是为了让写包的人一眼看清，
而不是靠试。这个条数与下面的清单都按真实资源包重新核过：**全库里每一处 `Resources.read*` 调用都在
`assets/mtr/**`**——那是 MTR 自己的地图 / LCD 脚本宿主，不是 PIDS——所以 **PIDS 侧的缺口是零**。
完整对照见仓库里的 [`V2-API-覆盖表.md`](V2-API-覆盖表.md)。*（译注：分组标题中的英文类名与官方文档一致，便于对照。）*

*类在、方法还没加的（81 条）：*

| 类 | 缺的 |
|---|---|
| `Resources` | `read` `readString` `readFont` `idr` `exist` `manager` `getNTEVersion` `getNTEVersionInt` `getNTEProtoVersion` `getSystemFont` `hasSystemFont` `ensureStrFonts` `getFontRenderContext` |
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

*整个类都还没有的（68 条）：*

| 类 | 条数 | 说明 |
|---|---|---|
| `VanillaText` | 11 | 配合 `displayMessage(VanillaText, ...)` 的富文本 |
| `Siding` `PathData` `Vector` `Position` `Rail` | 43 | TSC 数据类，实际是**车辆脚本**在用 |
| `VoxelShape` `ItemStack` `TransportMode` `UtilitiesClient` | 11 | 边角料 |
| `StationExit` | 2 | 车站出口 |
| `CarDetails` | 1 | `getVehicleId()` —— MTR 3 不向客户端下发逐节车厢数据，所以 `cars()` 是**故意**返回空列表 |

（`Files` 原本列在这里；现在已实现，见上面的「持久化」一行。）

*范围之外：那是另一种脚本类型（18 个类、约 162 条）。* `VehicleWrapper`、`VehicleScriptContext`、
`VehicleExtraData`、`EyecandyWrapper`、`EyecandyScriptContext`、`RenderManager`、`ModelManager`、
`Model`、`RawModel`、`RawMeshBuilder`、`DynamicModelHolder`、`QuadDrawCall`、
`DisplayHelper`、`BlockUseEvent`、`EyecandyEvents`、`ModelData`、`Vehicle` —— 这些属于 Vehicle Scripting
和 Eyecandy Scripting，不是 PIDS。（`GraphicsTexture` 本来列在这里，它已经作为脚本画布实现了，
见下。）

如果某个包确实需要上面某一样，**前四组是最便宜的**（每项几行），其中 `Resources.read*` 最可能真被用到 ——
那是包读取自己文件的必经之路。

### 脚本：声明式组件与运行时画布

这两样是 JCM 2.x 有、而本分支此前没有的能力，都按 v2 的函数名与参数形状接上。

**一、把 JSON 组件交给脚本** —— v2 进入组件体系的唯一入口是
`ctx.parseComponent(jsonString)`，参数与预设 JSON 的 `components` 数组**逐字相同**：

```js
function render(ctx, state, pids) {
    // 背景
    Texture.create("Bg").texture("mypack:pids/board.png").size(pids.width, pids.height).draw(ctx);

    // 一个时钟，放在 (4, 2)、40x10 的位置 —— 与写进 JSON 预设时一模一样
    const clock = ctx.parseComponent('{"component":"clock","x":4,"y":2,"width":40,"height":10,"format":"HH:mm"}');
    if (clock.canRender()) {
        clock.render(ctx);        // ctx.draw(clock) 等价
    }

    // 一个到站行，行号语义与 JSON 预设的 row 相同
    const row = ctx.parseComponent('{"component":"arrival_destination","x":4,"y":14,"width":80,"height":12,"row":0}');
    row.render(ctx);
}
```

`component` 对象上可用的东西：`render(ctx)`、`canRender()`、`x()` / `y()` / `width()` / `height()`、
`type()`。写错的声明（JSON 坏了、给的是数组、没有 `component` 键、组件名不认识）会**明确报错并列出
已知类型**，面板退回预设背景，不会黑屏也不会崩。

**二、运行时画布** —— 脚本自己画一张贴图，再当普通贴图贴到面板上：

```js
function create(ctx, state, pids) {
    state.canvas = new GraphicsTexture(128, 32);          // 只在真正需要时分配
    state.canvas.fillRect(0, 0, 128, 32, 0x101010);
    state.canvas.drawText("06:00", 4, 4, 0xFC9700, 20);
    // 把资源包里已有的贴图贴进来（同一张图也可以直接用 Texture 画，这里演示合成）
    state.canvas.drawTexture("jsblock:textures/block/pids/plat_circle.png", 100, 4, 24, 24);
    state.canvas.upload();
}

function render(ctx, state, pids) {
    Texture.create("Board").texture(state.canvas.identifier).pos(0, 0).size(128, 32).draw(ctx);
}

function dispose(ctx, state, pids) {
    state.canvas.close();                                  // 必须释放：纹理不会被 GC 回收
}
```

要点：坐标是**画布自己的像素**；颜色是 ARGB（`0xRRGGBB` 视为不透明，`0` 视为透明）；
`upload()` 之后 `identifier` 就是一张普通贴图；**一个脚本最多同时持有 64 张未释放的画布**
（第 65 张会报错并提示 `close()`），资源重载时引擎会释放遗留的画布。画布里画的贴图
**与像素化面板共存**：它和任何贴图走同一条路，不做离屏嵌套。

**三、`MTRClientData` 与 `MinecraftClient` 是两个东西**。JCM 2.x 的
`MTRClientData` 是 **MTR 自己的客户端数据**（其字节码里 `ldc class mtr/client/ClientData`），
而 `MinecraftClient` 是它的世界状态工具类（`MinecraftClientUtil`）。本分支照此接：

```js
const station = MTRClientData.STATIONS.get(someId);   // 站台/车站/到站表
const raining = MinecraftClient.worldIsRaining();     // 世界状态
```

**四、脚本读不到资源包之外的文件**。`include()` 与 `Texture.texture(...)` 都在读取**之前**校验路径：
拒绝 `..`、前导 `/`、反斜杠与盘符（例如 `include("jsblock:../../../../secret.js")`）。
拒绝时日志一行、聊天栏一行，脚本继续执行 —— 这既是安全边界，也是本分支新增读取入口后的必要配套。

### 资源包的 PIDS 无法正常工作怎么办

如果您遇到有资源包的 PIDS 无法正常工作，可以把**游戏版本信息**、**游戏日志**、**资源包下载链接**
发给我，或者直接提 [Issue](https://github.com/ChihayaAnonQWQ/Yomis-Joban-Client-Mod/issues/new)。

- **游戏版本信息**：Minecraft 版本、MTR / YMTR 版本、本模组版本（当前 `1.2.12-JSPIDS-2.5`）
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

## 从源码构建

`settings.gradle` 里有三个 Gradle 模块（根工程名是 `Joban-Client-Mod`）：

| 模块 | 装什么 |
|---|---|
| `common` | 共用代码：方块、PIDS 渲染器、组件体系、脚本引擎，以及 `tools/checks` 编译时依赖的那些类。依赖 MTR 与 Architectury 的 common 构件 |
| `fabric` | Fabric 入口（`com.jsblock.JobanFabric`、`JobanFabricClient`、`ModMenuConfig`）与 `fabric.mod.json`。通过 `shadowCommon` 把 Rhino 打进去 |
| `forge` | Forge 入口与 `META-INF/mods.toml`，同样方式把 Rhino 打进去 |

把两个平台 jar 合成发布用的那一颗，靠的是 `build.finalizedBy mergeJars`（forgix，
`mergedJarName = "MTR-YJCM-${minecraft_version}-${project.mod_version}.jar"`）。

| | |
|---|---|
| JDK | 源码目标是 **Java 17**（`build.gradle` 里 1.20 走 `options.release = 17`）。这里的构建是用 `JAVA_HOME` 指向 Zulu 21 跑的 |
| Gradle | **8.8**。仓库里有 `gradlew` / `gradlew.bat`，但没有 `gradle/wrapper/gradle-wrapper.jar`，所以 wrapper 按现状跑不起来 —— 用你自己的 Gradle 8.8，或用 `tools/run-pids-check.ps1` 解析到的那个：`%USERPROFILE%\.gradle\wrapper\dists\gradle-8.8-bin\dl7vupf4psengwqhwktix4v1\gradle-8.8\bin\gradle.bat` |

```powershell
$env:JAVA_HOME = 'C:\Program Files\Zulu\zulu-21'
gradle build --no-daemon --console=plain --max-workers=1
```

产物如下（`mod_version` 为 `1.2.12-JSPIDS-2.5` 时，文件名里的版本就是它）：

| 路径 | 是什么 |
|---|---|
| `build/MTR-YJCM-1.20-1.2.12-JSPIDS-2.5.jar` | 合并包：Forge 与 Fabric 在同一颗文件里。要装的是这一个 |
| `fabric/build/libs/fabric-1.20-1.2.12-JSPIDS-2.5.jar` | 合并用的 Fabric 侧 jar |
| `forge/build/libs/forge-1.20-1.2.12-JSPIDS-2.5.jar` | 合并用的 Forge 侧 jar |
| 名字里带 `-dev-shadow` 的 `fabric/build/libs/fabric-1.20-….jar`、`forge/build/libs/forge-1.20-….jar` | 重映射之前的中间 shadow jar |

构建需要 `checkouts/1.20/` 下的 MTR 开发 jar（已被 git 忽略），而 MTR 3 已经不在 Maven 上了 ——
这颗 jar 从哪来，见上面的[怎么对着本分支构建 MTR 3](#怎么对着本分支构建-mtr-3)。

无头检查会先构建，再拿编译出的类跑 `tools/checks` 下的每一项；任何一项失败就返回非零：

```powershell
.\tools\run-pids-check.ps1
```

## 常见问题与支持

### 游戏为什么崩了？

原因很多，其中一个主要原因是<b>你用了错误版本的 MTR Mod</b>。
标注为 <u>Pre-release</u> 的版本只能配合 MTR Mod 的预览版使用（见[他们的 Discord](https://discord.gg/hvddbya8rh)）。
如果你拿不到 MTR 的预览版，请下载标注为 <u>Release</u> 的版本。

### 游戏还是崩 / 我想报 bug / 我想提建议

大部分支持都在 Discord 里做，沟通更方便，请加入我们的 [Discord 服务器](https://discord.gg/FNc2rgWmP2)。

### 我想了解更多！

模组的大部分内容都写进了我们的 [wiki](https://www.joban.tk/wiki/JCM:Joban_Client_Mod)。

### 资源包的 PIDS 还是不工作

上面有专门一节，需要提供什么也在那里：[资源包的 PIDS 无法正常工作怎么办](#资源包的-pids-无法正常工作怎么办)。

### 脚本报错在哪里看

`logs/latest.log`。预设抛错的面板会写一行：

```
[Joban Client] PIDS script "..." threw in render(): ...
```

它会写明是哪个预设、脚本哪一行、缺的是哪个 API。之后引擎会用占位数据**重试一次**这一帧，
因为资源包不是模组能改的东西；抛错无论重试成败都会留在日志里，玩家只有在重试也救不回来时才会看到聊天栏提示。

### 怎么开调试

两个开关，开一个就够：

| 开关 | 怎么开 |
|---|---|
| 配置界面 | `ConfigScreen` 里的脚本调试开关 —— Fabric 从 ModMenu 进，Forge 从模组列表进 |
| 启动参数 | 加 `-Djsblock.pids.trace=true` 启动游戏 |

开了之后，预设发出的每一次绘制调用都会连类型、深度、颜色和文字一起写进日志。

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
