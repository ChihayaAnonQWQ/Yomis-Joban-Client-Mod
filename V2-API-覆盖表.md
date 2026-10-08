# JCM v2 脚本 API 覆盖表（YJCM / MTR 3 分支）

> **这份文件回答三个问题**：v2 的每个脚本 API 我们支不支持、依据是什么、真实资源包用得多不多；
> 真实包的每个脚本跑没跑通；还缺什么、为什么缺。
>
> **分支**：`feat/jcm-pids-components`，版本 `1.2.12-JSPIDS-2.4（未发布）`
> **仓库**：`work/yjcm-mtr3`
> **日期**：2026-10-09
> **方法**：全程只读参考实现与资源包；结论分「字节码依据」「实测」「推断」三类，逐条标注。
>
> **证据来源**
> | 代号 | 来源 |
> |---|---|
> | `javap` | `javap -classpath NeoJCM-neoforge-2.0.0-prerelease.2+fork.1+1.21.1.jar -p [-c]` |
> | `包扫描` | 资源包 `assets/**/*.js` 的词法扫描（121 个 `.js`，11 个包） |
> | `实测` | `tools/run-pids-check.ps1` 与 `ScriptApiCheck` 的真实执行 |
> | `源码` | 本仓库 `common/src/main/java/com/jsblock/script/**` |

---

## 0. 结论摘要

1. **v2 脚本作用域一共注册 18 个全局**，其中 17 个此前已在位；**唯一真缺的是 `Files`**，本次补齐（`FilesUtil`）。
2. **`Files` 在真实包里只有 `met transit` 一个包用**（`met_running_board.js`，3 处调用），但缺了它那个预设的第一行就抛，所以它是「用得少、但一缺就整包废」的那一类。
3. **发现并修复了第二个真缺口，而且它不是 API 面问题而是「名字问题」**：`met transit` 把脚本存成
   `Digital_Rail.js` / `Cyberpunk_Transit.js`（**存档里就是大写**），而 `ResourceLocation` 的构造器
   **拒绝大写**（实测：`ResourceLocationException: Non [a-z0-9/._-] character in path`），Minecraft 的
   包加载器索引时又把每个路径段**首字母小写化**（`scripts/Digital_Rail.js` → `scripts/igital_Rail.js`）。
   两头都够不着 → 该包 14 个预设里有 2 个此前**永远编译不出来**。本次加了 `ScriptPackFiles`，绕过资源
   管理器直接问包本身。
4. **真实包实测通过率：112 个脚本，108 通过**。4 个失败里 **2 个是 `-Include` 用的工具模块**（本来就不该
   被当成预设跑）、**2 个是无头环境没有世界 / 玩家数据**导致的 fixture 局限，**没有一个是缺 API**。
   即 110 个可执行预设脚本里 **108 通过、2 因环境不适用**。
5. **`Resources.read*` 一族（`read` / `readString` / `readFont` / `idr` / `getNTEVersion*`）和
   `VanillaText`、TSC 数据类仍然缺**，但证据显示**没有任何一个 PIDS 预设调用它们**——唯一用量在
   无锡包的 `assets/mtr/**`（另一套 MTR 脚本宿主，见 §3）。

---

## 1. v2 脚本 API → 我们的支持状态

### 1.1 作用域全局（v2 注册点：`ParsedScript` 构造器 + `JCMScripting` 的 parse 回调）

`javap -p -c com.lx862.mtrscripting.core.ParsedScript` 的 `ldc String / ldc class` 序列给出全部 12 个
通用全局，`javap -p -c com.lx862.jcm.mod.scripting.jcm.JCMScripting` 的 PIDS 分支再给 4 个。

| # | v2 全局 | v2 绑定的类（javap） | 我们 | 我们绑定的实现 | 真实包用量 | 依据 |
|---|---|---|---|---|---|---|
| 1 | `include` | `ScriptResourceUtil.includeScript` | ✅ | `ScriptEngine` 内联函数 | **11 包 / 111 处** | javap + 实测 |
| 2 | `print` | `ScriptResourceUtil.print` | ✅ | `ScriptEngine` 内联函数 | 4 包 / 54 处 | javap + 实测 |
| 3 | `Resources` | `ScriptResourceUtil` | ✅ | `ScriptEngine.Resources` | **11 包 / 131 处** | javap + 实测 |
| 4 | `GraphicsTexture` | `mtrscripting.util.GraphicsTexture` | ✅ | `script.GraphicsTexture` | 0（无包使用） | javap + 实测 |
| 5 | `Timing` | `mtrscripting.util.TimingUtil` | ✅ | `ScriptTrackers.Timing` | 1 包 / 1 处 | javap + 实测 |
| 6 | `StateTracker` | `mtrscripting.util.StateTracker` | ✅ | `ScriptTrackers.StateTracker` | 0 | javap + 实测 |
| 7 | `CycleTracker` | `mtrscripting.util.CycleTracker` | ✅ | `ScriptTrackers.CycleTracker` | 0（正则命中，实为局部名） | javap + 实测 |
| 8 | `RateLimit` | `mtrscripting.util.RateLimit` | ✅ | `ScriptEngine.RateLimit` | 0（正则命中，实为局部名） | javap + 实测 |
| 9 | `Networking` | `mtrscripting.util.NetworkingUtil` | ✅ | `ScriptNetwork.Networking` | 1 包 / 3 处 | javap + 实测 |
| 10 | **`Files`** | **`mtrscripting.util.FilesUtil`** | ✅ **本次新增** | **`script.FilesUtil`** | **1 包 / 3 处（met transit）** | javap -c 全量 + 实测 |
| 11 | `Matrices` | `cn.zbx1425.sowcer.math.Matrices` | ✅ | `ScriptMath.Matrices` | 0（正则命中，实为局部名） | javap + 实测 |
| 12 | `MinecraftClient` | `mtrscripting.util.MinecraftClientUtil` | ✅ | `ScriptEngine.MinecraftClient` | 6 包 / 82 处 | javap + 实测 |
| 13 | `MTRClientData` | **`mtr.client.ClientData`** | ✅ | `ScriptEngine` 直接绑 MTR 类 | 2 包 / 5 处 | javap + 实测 |
| 14 | `TextUtil` | `jcm.mod.scripting.mtr.util.TextUtil` | ✅ | `ScriptEngine.TextUtil` | 9 包 / 333 处 | javap + 实测 |
| 15 | `Text` | `jcm.mod.scripting.jcm.pids.TextWrapper` | ✅ | `ScriptDrawCalls.Text` | 10 包 / 967 处 | javap + 实测 |
| 16 | `Texture` | `jcm.mod.scripting.jcm.pids.TextureWrapper` | ✅ | `ScriptDrawCalls.Texture` | 10 包 / 630 处 | javap + 实测 |
| 17 | `console` | 未注册（Rhino 标准对象） | ✅（我们额外提供，`log/debug/info/warn/error`） | `ScriptEngine.Console` | 1 包 / 11 处 | 实测 |
| 18 | `Rectangle` / `Vector3f` | **v2 未注册**（我们的扩展） | ✅（仅我们） | `ScriptDrawCalls.Rectangle` / `ScriptMath.Vector3f` | 0（正则命中，实为局部名） | javap 未见 |

> **注**：第 17、18 行是「v2 没有、我们有」的**反向输出**，不是缺口，列出来是为了说明扫描表里那些
> `console.*` 命中是真实调用、而 `CycleTracker` / `Matrices` / `RateLimit` 那几条同名命中是**局部变量**
> 而不是全局用法（扫描器把 `let Matrices = ...` 之类写法也算了进去，逐条回看源码确认）。

### 1.2 `Files` 的逐方法对照（本次新增，v2 依据最完整）

`javap -p -c com.lx862.mtrscripting.util.FilesUtil` 给出 5 个公开方法、2 条路径根、2 个私有防护函数。
我们逐条对齐，**同名、同参数、同返回、同目录**：

| v2 `FilesUtil` | 签名（javap） | 我们 | 依据与说明 |
|---|---|---|---|
| `read` | `static String read(String...) throws IOException` | ✅ 同名同签名 | `rootMinecraftPath` 解析（v2：`Minecraft.gameDirectory`） |
| `readData` | `static String readData(String...) throws IOException` | ✅ 同名同签名 | `dataPath` = `<gameDir>/data/mtrscripting`（v2 的 `resolve("data").resolve("mtrscripting")`） |
| `saveData` | `static void saveData(String, String...) throws IOException` | ✅ 同名同签名 | 额外 `createDirectories(parent)`——v2 靠 `FileUtils.writeStringToFile` 隐含做这件事，不补的话第一次 `saveData` 就抛 |
| `deleteData` | `static void deleteData(String...) throws IOException` | ✅ 同名同签名 | v2 用 `Files.deleteIfExists`，不存在的文件不报错 |
| `hasData` | `static boolean hasData(String, String) throws IOException` | ✅ 两个参数（不是可变参数） | v2 是固定两参；扫描显示真实包写 `hasData("met_running_board", fileName)`，正好两参 |
| `resolvePathSafe` | `private static Path (Path, String...)` | ✅ 同名同逻辑 | 逐段 `resolve` 后交给防护；顺序与 v2 一致 |
| `ensurePathNotEscaped` | `private static void (Path, Path)` | ✅（在 `ScriptPaths.ensureWithin`） | 从规范化后的路径向上走父目录，走到根之前耗尽即拒绝；**错误文案逐字沿用 v2**：`Path must be within the "%s" directory!` |

**两处刻意的实现差异（都不是行为差异）**：

1. **不缓存在静态初始化里**。v2 的 `FilesUtil.<clinit>` 直接读 `Minecraft.getInstance().gameDirectory`
   （`javap -c` 的 `static {}` 段），意味着**没有客户端时这个类根本加载不了**。我们的端口改成惰性解析
   （`rootMinecraftPath()`），无客户端时退回进程工作目录——否则 `FilesCheck` 没办法把 `Files` 放上作用域
   去验证防护。**这是一个可验证性上的改动，不是行为改动**：有客户端时两条路径得到同一个目录。
2. **测试用的 `rootOverride` 是包私有**。原因是实测发现的一件事：Rhino 对 `NativeJavaClass` 会暴露
   **全部 `public static` 成员**给脚本，所以一个 `public` 的根目录 setter 等于让预设自己决定文件写在哪。
   它保持包私有，`FilesCheckSupport` 以同包身份调用；`FilesCheck` 里有一条断言专门盯这件事。

### 1.3 真实包用到的 `Resources` / `TextUtil` / `MinecraftClient` 成员

| 成员 | 真实包用量 | 我们 | 备注 |
|---|---|---|---|
| `Resources.id` | 11 包 / 115 处 | ✅ | |
| `Resources.getMTRVersion` | 1 包 / 2 处 | ✅ | |
| `Resources.readBufferedImage` | 1 包 / 1 处 | ✅ | |
| `Resources.idr` | 1 包 / 6 处 | ❌ | **只在无锡包 `assets/mtr/**`，非 PIDS（§3）** |
| `Resources.readString` | 1 包 / 1 处 | ❌ | 同上 |
| `Resources.readFont` | 1 包 / 4 处 | ❌ | 同上 |
| `Resources.getNTEVersionInt` | 1 包 / 2 处 | ❌ | 同上（NTE = 另一个 mod 的版本号） |
| `TextUtil.cycleString` | 8 包 / 276 处 | ✅ | |
| `TextUtil.getNonCjkParts` | 2 包 / 46 处 | ✅ | |
| `TextUtil.getNonExtraParts` | 2 包 / 6 处 | ✅ | |
| `TextUtil.getCjkParts` | 1 包 / 5 处 | ✅ | |
| `TextUtil.getExtraParts` | 1 包 / 2 处 | ✅ | |
| `TextUtil.isCjk` | 1 包 / 4 处 | ✅ | |
| `MinecraftClient.worldDayTime` | 4 包 / 31 处 | ✅ | |
| `MinecraftClient.worldIsRaining` | 4 包 / 18 处 | ✅ | |
| `MinecraftClient.worldIsThundering` | 4 包 / 18 处 | ✅ | |
| `MinecraftClient.displayMessage` | 1 包 / 12 处 | ✅ | |
| `MinecraftClient.localPlayer` | 1 包 / 2 处 | ✅ | 无头环境返回 `null`（见 §2.2） |
| `MinecraftClient.narrate` | 1 包 / 1 处 | ✅ | |

### 1.4 `Text` / `Texture` 构建链成员

扫描覆盖 10 个包、约 1600 处调用。清单与状态：

| 链 | 成员 | 用量 | 我们 |
|---|---|---|---|
| `Text` | `create` | 978 处 | ✅ |
| `Text` | `.text` | 1073 处（含 `Texture` 同名统计，逐条回看确认） | ✅ |
| `Text` | `.pos` / `.size` / `.color` / `.draw` / `.scale` | 各 800–1600 处 | ✅ |
| `Text` | `.centerAlign` (267) / `.rightAlign` (215) / `.leftAlign` | 9 包 | ✅ |
| `Text` | `.stretchXY` (43) / `.scaleXY` / `.wrapText` / `.marquee` (55) | 2–6 包 | ✅ |
| `Text` | `.bold` / `.italic` / `.font` / `.fontMC` | 1–3 包 | ✅ |
| `Texture` | `create` (631) / `.texture` (630) / `.uv` / `.color` | 10 包 | ✅ |

**未发现任何真实包调用我们缺失的链成员。**

### 1.5 v2 的 PIDS 上下文（`ctx.*`）与 `pids.*`

| v2 成员 | 我们 | 用量 | 备注 |
|---|---|---|---|
| `ctx.parseComponent(json)` | ✅ | 0 | 无真实包使用，但已实现并有检查用例 |
| `ctx.draw(...)` | ✅ | 1598 处 | |
| `ctx.getSoundManager()` | ✅ | 1 包 / 1 处 | 琼岭 |
| `ctx.setZOrderStep()` | ✅ | 1 包 / 1 处 | 琼岭 |
| `ctx.setAutoZOrdering()` | ✅ | 0 | |
| `pids.type` / `.rows` / `.width` / `.height` | ✅ | 5–10 包 | |
| `pids.arrivals()` / `.arrivals().size()` / `.get(i)` / `.mixedCarLength()` | ✅ | 10 包 / 260 处 | |
| `pids.isRowHidden(i)` / `.getCustomMessage(i)` / `.isPlatformNumberHidden()` | ✅ | 2–7 包 | |
| `pids.station()` | ✅ | 7 包 / 156 处 | 无头环境返回 `null`（见 §2.2） |
| `pids.blockPos()` | ✅（我们的扩展） | 4 包 / 9 处 | v2 没有 |
| `pids.getTargetPlatformIds()` / `.isKeyBlock()` | ✅（我们的扩展） | 3 包 | v2 没有 |
| `arrival.routeNumber()` / `.destination()` / `.arrivalTime()` / `.carCount()` / `.routeColor()` / `.departureTime()` / `.platformName()` / `.deviation()` / `.realtime()` / `.departureIndex()` / `.terminating()` / `.circularState()` | ✅ | 5–10 包 | 我们的 `ArrivalWrapper` 是 v2 的超集 |

---

## 2. 真实资源包逐个脚本的通过 / 失败表

**测试方法**：把每个包解到临时目录，按 **Minecraft 包加载器的索引规则** 生成一套「小写化路径」副本
（首字母小写）与一套「存档原名」副本，然后对每个脚本跑
`ScriptApiCheck`，`--arrivals=0,2,4` 各一次、每次 `--iterations=2`。
`--arrivals=0` 是空站台（v2 预设用 `arrival == null` 防的那种），`4` 超过所有 PIDS 面板的行数。

**总计：112 个 `.js`，108 通过，4 失败。**

| 包 | 脚本数 | 通过 | 失败 | 备注 |
|---|---|---|---|---|
| `met transit` | 14 | **14** | 0 | 本次重点包之一 |
| `GURIGRUI_PIDS_JCM2.2.1_MTR4.0.5` | 1 | **1** | 0 | 本次重点包之二 |
| `World_PIDS-Pack-200` | 66 | 66 | 0 | 全通过 |
| `US PIDS Pack v4.2` | 21 | 20 | 1 | 见 §2.2 |
| `琼岭追加包26.8.3` | 4 | 2 | 2 | 1 个环境 + 1 个工具模块 |
| `HKR PIDS` | 2 | 2 | 0 | |
| `上海地铁-PIDS` | 2 | 1 | 1 | 工具模块 |
| `Japan_Style_PIDS日式PIDSv1.0.6` | 1 | 1 | 0 | |
| `Japanese_PIDS v1.5` | 1 | 1 | 0 | |
| **合计** | **112** | **108** | **4** | |

### 2.1 两个重点包：逐脚本明细

#### `met transit.zip`（8.96 MB，v2 时代）

资源根：`_v2api/packs/met transit/lower` + `.../raw`。全部 14 个预设脚本：

| # | 脚本（存档原名） | 预设 id | 结果 | 绘制调用数（arrivals=2） |
|---|---|---|---|---|
| 1 | `transit_v1.js` | `met_transit_v1` | ✅ 通过 | 17 |
| 2 | `transit_v2.js` | `met_transit_v2` | ✅ 通过 | 31 |
| 3 | `met_railroad_v2.js` | `met_railroad_pids_v2` | ✅ 通过 | 28 |
| 4 | `met_railroad_classic.js` | `met_railroad_classic` | ✅ 通过 | 17 |
| 5 | **`Digital_Rail.js`** | `met_digital_rail` | ✅ 通过 | 20 |
| 6 | **`Cyberpunk_Transit.js`** | `met_cyberpunk_transit` | ✅ 通过 | 66 |
| 7 | `met_outdoor.js` | `met_outdoor` | ✅ 通过 | 32 |
| 8 | `met_outdoor_dark.js` | `met_outdoor_dark` | ✅ 通过 | 32 |
| 9 | **`met_running_board.js`** | `met_running_board` | ✅ 通过 | 54 |
| 10 | `met_grand_terminal.js` | `met_grand_terminal` | ✅ 通过 | 35 |
| 11 | `met_glass.js` | `met_glass` | ✅ 通过 | 39 |
| 12 | `met_panorama.js` | `met_panorama` | ✅ 通过 | 39 |
| 13 | `met_bus_stop.js` | `met_bus_stop` | ✅ 通过 | 46 |
| 14 | `met_western_station.js` | `met_western_station` | ✅ 通过 | 13 |

**该包依赖的三个 API，逐个说明为什么通过：**

- **`Files`**（只有 #9 用）→ 本次新增。实测输出证明它真的跑到了文件系统：
  ```
  [Joban Client] [PIDS script] [MET Running Board] Station ID: 0_64_0
  [Joban Client] [PIDS script] [MET Running Board] Loading from: met_running_board/0_64_0_departed.json
  [Joban Client] [PIDS script] [MET Running Board] No saved data found
  ```
  三行分别对应 `pids.blockPos()`、`pids.stationId` 拼路径、`Files.hasData(...)` 返回 false 走
  「没有存档」分支。**注意这三行在补 `Files` 之前是不可能出现的**——第一行碰到 `Files` 就
  `ReferenceError` 了。
- **大写文件名**（#5、#6）→ 本次新增 `ScriptPackFiles`。见 §2.3。
- **`include(Resources.id("jsblock:scripts/pids_util.js"))`** → 早已支持；`PIDSUtil` 随 mod 附带。

#### `GURIGRUI_PIDS_JCM2.2.1_MTR4.0.5.zip`

资源根：`_v2api/packs/GURIGRUI_PIDS_JCM2.2.1_MTR4.0.5/...`

| # | 脚本 | 预设 id | 结果 | 绘制调用数 |
|---|---|---|---|---|
| 1 | `gurigrui_pids.js`（26443 字节，最大的单个预设脚本） | `gurigrui_japanese_departure_board` | ✅ 通过 | 11 |

**注意**：该包的名字里写着 `MTR4.0.5`，但脚本本身只用了 PIDS 脚本面（`Resources` / `MTRClientData` /
`Text` / `Texture` / `include`），**没有任何 MTR 4 专有调用**，所以在 MTR 3 上原样通过。
它用到的 `MTRClientData.getInstance()` 是 MTR 自己的静态方法，MTR 3.6.3 里同样存在。

### 2.2 4 个失败逐个归因（**没有一个是缺 API**）

| # | 脚本 | 包 | 失败行 | 失败信息 | 归因 | 依据 |
|---|---|---|---|---|---|---|
| 1 | `sound_transit.js` | `US PIDS Pack v4.2` | 第 24 行 → 报在第 31 行 | `TypeError: Cannot call method "getName" of null` | **环境（fixture）**：`pids.station()` 在无头环境返回 `null`（没有世界、没有站台数据），而脚本直接 `.getName()` 没有判空。**在游戏里这是有站台的面板，不会走到 null** | `pids.station()` 的 javadoc 与实现都写明「无法解析时返回 null」；`ScriptApiCheck` 的 stub `pids` 没有世界，`PIDSData.stationOf()` 必然返回 null |
| 2 | `pids_ql.js` | `琼岭追加包26.8.3` | 第 108 行 | `TypeError: Cannot call method "pos" of null` | **环境（fixture）**：`MinecraftClient.localPlayer()` 在无头环境返回 `null`（没有玩家），脚本没判空。**`localPlayer()` 本身已实现** | 该类成员已存在（`ScriptEngine.MinecraftClient.localPlayer()`），文档明写「无客户端时为 null」；实测打印的栈指向 `.pos()` 而不是 `Cannot find function localPlayer` |
| 3 | `tools.js` | `琼岭追加包26.8.3` | — | `FAIL no render() defined` | **不适用（not a preset）**：这是 `pids_ql*.js` 用 `include` 引的工具模块，只定义 `PIDSUtil` 对象，**本来就没有 `render()`**。它出现在 `.js` 清单里但不是预设 | 文件头注释写着「changed from jcm mod's build-in script」，内容是 `const PIDSUtil = {...}` |
| 4 | `pids_util.js` | `上海地铁-PIDS` | — | `FAIL no render() defined` | **不适用（not a preset）**：同上，是随包附带的 `PIDSUtil` 工具副本 | 内容是 `const PIDSUtil = {...}`，与 mod 自带的 `jsblock:scripts/pids_util.js` 同构 |

**因此换算口径**：112 个 `.js` 里，**110 个是可执行的预设脚本**（扣掉 2 个工具模块），
其中 **108 个通过**，2 个因无头环境缺世界数据而无法在检查里成立 —— 这 2 个记为
**「不适用（无头环境）」**，不记为缺陷。

### 2.3 大写文件名的定位与修复（第二个真缺口）

**现象**：`met transit.zip` 的条目表里存的是
```
assets/jsblock/scripts/Cyberpunk_Transit.js
assets/jsblock/scripts/Digital_Rail.js
```
（`zipfile` 原始条目名，**不是**展示用的）

**为什么够不着**（两条独立的原因，缺一不可）：

1. 实测 `ResourceLocation` 的构造器直接拒绝：
   ```
   REFUSED jsblock:scripts/Digital_Rail.js
     -> ResourceLocationException: Non [a-z0-9/._-] character in path of location: jsblock:scripts/Digital_Rail.js
   ```
   所以 `ScriptPaths.resource("jsblock:scripts/Digital_Rail.js")` 抛异常，根本到不了读文件那一步。
2. 退一步，用折叠后的小写名 `jsblock:scripts/digital_rail.js` 去问资源管理器也不行：
   Minecraft 的包加载器在**索引**时把每个路径段的首字母小写化（`Digital_Rail.js` →
   `igital_Rail.js`，`Cyberpunk_Transit.js` → `yberpunk_Transit.js`），索引里没有这个键。

**修复**：新增 `ScriptPackFiles`，在资源管理器两条路都失败后**直接问包本身**：

- 包列表来自 `Minecraft.getResourcePackRepository().openAllSelected()`（公开 API）；
- 目录包走 `PathPackResources` 的 `root` 字段，逐段做**忽略大小写**的目录列举，返回值取自
  **目录列举的真实拼写**（不是我们请求的拼写——在大小写不敏感的文件系统上 `Path` 会回显请求名，
  这一点是本次写检查时才发现的）；
- zip 包走 `FilePackResources` 的 `file` 字段，用 `ZipFile` 直接按条目名匹配（精确一次哈希查找，
  再做一次忽略大小写的扫描）；
- **只在资源管理器失败后才走这条**，所以正常包一次也不会碰到；
- 全程 `try/catch(Throwable)`：拿不到字段就当这个包搜不了（并只报一次日志），**永远不会把渲染器带崩**。

**这是「对包的宽容」，不是 MC 规范**。文档与日志都写明了这一点；`ScriptCaseCheck` 里还有一组断言
专门盯住「宽容不能变成逃逸」：路径里的 `..`、绝对路径、反斜杠在任何一条路径上都仍然被拒。

### 2.4 检查用的资源根为什么是「小写化副本」

无头检查直接读磁盘，没有 Minecraft 的包加载器，所以**为了忠实复现「资源管理器能解析哪些名字」**，
测试树按包加载器的规则生成了两套：

| 树 | 内容 | 模拟什么 |
|---|---|---|
| `lower/` | 每个路径段首字母小写 | 资源管理器**能**解析的名字（绝大多数包的真实情况） |
| `raw/` | zip 里的原始拼写 | 存档原名，只有在资源管理器失败、`ScriptPackFiles` 接手时才用得上 |

两棵树都作为 `-ResourceRoot` 传给 `ScriptApiCheck`，顺序是 `lower` → `raw` → 仓库自带资源
（最后一个是让 `jsblock:scripts/pids_util.js` 解析得到）。

**这不是在放宽检查**：`raw` 存在恰恰是因为**游戏侧**也会退到包本身；如果只在 `lower` 里跑，
覆盖的就不是游戏里真实会发生的那条路径。

---

## 3. 仍然缺什么，以及为什么

### 3.1 缺、但**没有任何 PIDS 预设使用**（低优先）

| 缺口 | v2 出处（javap） | 用量 | 为什么先不做 |
|---|---|---|---|
| `Resources.read` / `readString` / `readFont` / `idr` / `exist` / `manager` / `getNTEVersion*` / `getSystemFont` / `hasSystemFont` / `ensureStrFonts` / `getFontRenderContext` | `ScriptResourceUtil` | **1 个包（无锡）**，且**全部在 `assets/mtr/**`** | 见 §3.2：那是**另一套脚本宿主**（MTR 的 map / LCD 脚本），不是 `assets/jsblock/scripts/**` 的 PIDS 预设。PIDS 脚本一个都没用过 |
| `TextUtil.getNonCjkAndExtraParts` | `jcm.mod.scripting.mtr.util.TextUtil` | 0 | 已实现（我们甚至有），只是 v2 对照表里的一行 |
| `VanillaText`（11 条） | `jcm.mod.scripting.mtr.util` 一族 | 0 | 只服务 `displayMessage(VanillaText, ...)` 的富文本重载 |
| `Siding` / `PathData` / `Vector` / `Position` / `Rail`（43 条） | TSC 数据类 | 0（PIDS 内） | 实际由 **车辆脚本** 使用，不属于 PIDS |
| `Station` 的几何成员（`getMinX/Y/Z`、`getMaxX/Y/Z`、`getExits`、`inArea`、`getCenter`、`isTransportMode`） | `mtr.data.Station`（MTR 4） | 0 | MTR 3 的 `Station` **没有**这些成员（javap 确认：只有 `zone` / `exits` / `getGeneratedExits` 等），要做得自己算站台包围盒 |
| `PIDSScriptContext.getRenderManager()` / `setDebugInfo()` | `jcm.mod.scripting.jcm.pids.PIDSScriptContext` | 0 | 3D 模型渲染，与 PIDS 面板无关 |
| `CarDetails.getVehicleId()` | `jcm.mod.scripting.mtr.util` | 0 | MTR 3 **不向客户端推送逐车厢数据**，所以 `cars()` 故意返回空列表 |

### 3.2 一个需要说清楚的**不适用项**：无锡包的 `assets/mtr/**`

`[V1.3.3F]无锡地铁追加包 (免费).zip` 里有 7 个 `.js`，但路径是：

```
assets/mtr/map/main.js
assets/mtr/shlcd_xh/draw.js
assets/mtr/shlcd_xh/font_util.js
assets/mtr/shlcd_xh/js_util.js
assets/mtr/shlcd_xh/main_k.js
assets/mtr/shlcd_xh/main_wxb.js
assets/mtr/shlcd_xh/mtr_util.js
```

**这些不是 PIDS 预设**，`assets/jsblock/scripts/**` 下一个都没有。它们的内容证明它们面向
**另一套宿主 API**：

- `include("js_util.js")` —— 相对路径形式的 include（我们的 `include` 走 `namespace:path`）；
- `Resources.idr("texture.png")` —— 相对资源 id；
- `Resources.readFont(Resources.idr("fonts/roboto/roboto-regular.ttf"))`、`AttributedString`、
  `LineBreakMeasurer`、`Graphics2D.fillRoundRect`、`deriveFont` —— **Java2D 绘图**；
- `train.getAllPlatforms()` / `train.railProgress()` / `train.getThisRoutePlatforms()` —— **车辆脚本** API；
- `Resources.getNTEVersionInt() >= 500` —— 一个叫 NTE 的 mod 的版本号。

**判定：不适用（out of scope）。依据**：这些脚本既不在 PIDS 的 `scriptFiles` 里，也不使用 PIDS
上下文；它们要的是 MTR 的 map / LCD 脚本宿主 + Java2D 画布 + 车辆数据，那是一条**与本次任务不同的**
脚本面。硬造这些 API 不会让任何一个 PIDS 预设跑起来。

> 同一份证据反过来也是一条好消息：**扫描出来的 `Resources.read*` / `getNTEVersionInt` / `idr` 用量
> 全部来自这 7 个文件**。扣掉它们，PIDS 侧的 `Resources` 缺口是**零**。

### 3.3 本次**没有**做的事（诚实清单）

- **没有实机验证**。全部结论来自编译 + 无头检查 + 真实包脚本执行，**没有启动过游戏**。
  特别是 `ScriptPackFiles` 的**包发现（`openAllSelected()`）与两个私有字段的反射**属于
  **源码/字节码推断**，未在游戏里跑过；**但它的目录查找逻辑已被 `ScriptCaseCheck` 直接断言**
  （`readFromDirectoryRoot` 对 met transit 的真实资产路径命中），所以「读到哪个文件」这一半是实测过的，
  未实测的是「包对象从哪来」那一半。zip 分支（`FilePackResources`）**整体属于推断**。
- **没有验证 Forge/Fabric 侧的包实现差异**。`ScriptPackFiles` 只认 `PathPackResources` 与
  `FilePackResources`；若加载器换了同名字段的其它实现，反射会沿类层次向上找，找不到就跳过
  （软失败，只报一次日志）。**推断**，未实测。这一条在 Forge 上尤其值得实机确认一次。
- **`Files` 的并发语义未测**。v2 也没有锁；多个预设同时写同一个文件时谁赢未定义（与 v2 一致）。
- **`Files.read`（非 Data 那条）在真实包里零调用**，所以它只是按字节码对齐实现，**没有真实用例**。
- 扫描器的**全局识别是词法的**：`CycleTracker` / `Matrices` / `RateLimit` / `Rectangle` / `Vector3f`
  在原始表里出现，逐条回看源码后确认是**局部变量名**而非全局用法；`console` 则确认是真用法。
  表格里已按回看结果标注，但**这是一次人工校正，不是自动判定**。

---

## 4. 本次提交的检查用例

`tools/run-pids-check.ps1` 最终输出 `RESULT: ALL CHECKS PASSED`。新增两个检查：

| 检查 | 盯住什么 | 关键断言 |
|---|---|---|
| `FilesCheck` | `Files` 全局（唯一写盘的那一个） | 存档-读回一致；缺文件返回 `null` 不抛；UTF-8 多行往返；`saveData` 自动建目录；`deleteData` 幂等；**7 种逃逸形态**（`..`、`..`×2、`../..`、`a/../../..`、深逃逸、绝对路径、Windows 路径）× 4 个方法全部拒绝且文案为 v2 原文；从脚本侧（Rhino + 真作用域）跑 `hasData/readData/saveData`，含 `met_running_board` 的真实调用形状；`rootOverride` 对脚本不可达 |
| `ScriptCaseCheck` | 引用拼写的宽容 | 精确引用**不被折叠**；大写折叠成可用 `ResourceLocation`；命名空间大小写也折叠；**6 种逃逸形态在两条路径上都仍被拒绝**；`caseFoldedCandidates` 的候选顺序；磁盘查找：小写引用找到大写文件、精确拼写优先于近似、多文件互不串味、拒绝向上爬出根；**以及 `ScriptPackFiles.readFromDirectoryRoot` 本身**——用 met transit 的真实资产路径（`assets/jsblock/Scripts/Digital_Rail.js`）断言精确命中、小写拼写同样命中、不存在的名字返回 `null` |

`ScriptApiCheck` 未改动其断言，只是（1）资源根可以给多个、（2）`include` 的磁盘解析加了同样的大小写宽容。

---

## 5. 复现命令

```powershell
# 无头检查（含新增两个用例）
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
cd work\yjcm-mtr3
.\tools\run-pids-check.ps1

# 单个真实包脚本
.\tools\run-pids-check.ps1 -SkipBuild `
  -ResourceRoot ..\..\_v2api\packs\met transit\lower `
  -ResourceRoot ..\..\_v2api\packs\met transit\raw `
  -Script       ..\..\_v2api\packs\met transit\lower\assets\jsblock\scripts\met_running_board.js
```

真实包的提取与批量运行脚本（本次一次性工具，留在 `_v2api/` 供复算）：
`scan_api.py`、`members.py`、`diff_globals.py`、`presets.py`、`prepare_packs.py`、`prepare_all.py`、
`run-pack.ps1`、`run-all.ps1`；
原始产物：`usage-table.md`、`members-by-root.md`、`preset-scriptfiles.txt`、`ref-check.txt`、
`result-*.txt`、`run-all.log`。

---

## 附：本表涉及的行数统计

- §1.1 全局对照 **18 行**；§1.2 `Files` 方法对照 **7 行**；§1.3 成员对照 **18 行**；§1.5 上下文对照 **14 行**
- §2.1 重点包逐脚本 **15 行**；§2.2 失败归因 **4 行**
- §3.1 已知缺口 **7 组**；§3.3 未验证清单 **7 条**
