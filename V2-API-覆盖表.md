# JCM v2 脚本 API 覆盖表（YJCM / MTR 3 分支）

> **这份文件回答三个问题**：v2 的每个脚本 API 我们支不支持、依据是什么、真实资源包用得多不多；
> 真实包的每个脚本跑没跑通；还缺什么、为什么缺。
>
> **分支**：`feat/jcm-pids-components`，版本 `1.2.12-JSPIDS-2.5（未发布）`
> **仓库**：`work/yjcm-mtr3`
> **日期**：2026-10-09 首版；**2026-10-10 追加 §1.1b / §1.3 / §1.3b / §1.5 / §3.4（2.5：importer 全局、
> `Resources.getSystemFont`、`SoundManager` 参数个数、HKR Split-Flap Display 的透明面板）**
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

> **2.5 追加（本次）**——下面 1–5 条仍是 2.4 的结论，成立；2.5 在其上多出四项，**来源都是同一个真实包**
> （`HKR-Split-Flap-Display.zip`，预设 `next_train`，其面板本来是**透明的**）：
>
> 6. **真缺口一：Rhino importer 的 9 个全局**（`importPackage` / `importClass` / `Packages` /
>    `java` / `javax` / `org` / `com` / `edu` / `net`）。v2 不逐个注册它们，它们是
>    `new ImporterTopLevel(cx)` 这个**作用域类型**自带的（javap `ParsedScript.<init>`）。我们此前用
>    `initStandardObjects()`，于是 `next_train.js#27` 的 `importPackage(java.awt);` 在**加载期**抛
>    `ReferenceError`，`create()` 从未定义 → 一帧都没画 → 面板透明。见 §1.1b。
> 7. **真缺口二：`Resources.getSystemFont(name)`**（该包 3 处调用）。按 `javap -p -c` 三分支实现。
>    见 §1.3、§1.3b。
> 8. **真缺口三：`SoundManager` 的可选参数**。Rhino 按参数个数**精确**匹配（实测），HKR 的
>    4 参 / 3 参调用此前解析失败，而脚本自己 `catch` 掉了 → 没声音且无日志。见 §1.3b。
> 9. **一处「不适用」而不是缺口**：`ctx.setDebugInfo` 在参照 jar 里**不存在**（全 jar 扫描 0 命中），
>    包只在自身 `DEBUG_INFO = false` 分支里调用且外面套 `try{}catch{}` → 不实现，记为版本差。见 §1.5。
>
> 9 个既有真实包、112 个 `.js` 在 2.5 之后**重跑：仍是 108 通过 / 4 失败**，与 2.4 逐条相同。
> 新增的 `ScriptImporterCheck` 单独覆盖 importer 全局、经 importer 的沙箱、以及 AWT 画布路径。

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

#### 1.1b Rhino importer 的 9 个全局（2.5 新增；**v2 不注册它们，但它们的来源就是 v2 建作用域的方式**）

这 9 个名字不在 `ParsedScript` 的 `ldc String` 序列里，因为它们不是 v2 逐个 `put` 进去的：它们由
**作用域本身的类型**带来。`javap -p -c com.lx862.mtrscripting.core.ParsedScript.<init>` 用
`new ImporterTopLevel(cx)` 建作用域，而 `ImporterTopLevel` 的构造器会
`exportAsJSClass(3, this, false)` 装上 `importClass` / `importPackage` / `Packages`（`Packages` 一转手
就是 `NativeJavaTopPackage`，逐段解析 `java` / `javax` / `org` / `com` / `edu` / `net`）。

| # | 全局 | 来源 | 我们 | 真实包用量 | 依据 |
|---|---|---|---|---|---|
| 19 | `importPackage(pkg)` | `ImporterTopLevel` 的 `exportAsJSClass` | ✅ **2.5 新增** | **1 包 / 2 处（HKR Split-Flap Display）** | javap `ParsedScript` + `ImporterTopLevel` + 实测 |
| 20 | `importClass(cls)` | 同上 | ✅ **2.5 新增** | 0 | 同上 |
| 21 | `Packages` | 同上（`NativeJavaTopPackage`） | ✅ **2.5 新增** | 0 | 同上 |
| 22–27 | `java` / `javax` / `org` / `com` / `edu` / `net` | 同上（6 个包根） | ✅ **2.5 新增** | 0（包都走 `importPackage`） | 同上 |

**为什么它不是「v2 没有」的扩展项**：v2 的每一个预设脚本都**已经**跑在这个 importer 作用域里，
只是此前没有一个包用到它。缺了它，脚本在**加载阶段**就抛 `ReferenceError`——`create()` 从未定义，
面板一帧都没画过。HKR 的 `next_train.js` 正是这样：详见 `MTR3-PIDS-PORT.md` §4.15。

**沙箱**：importer 是第二条够到 Java 类的路，但它不绕过 `ClassShutter`——`importPackage` 只把包记在
scope 上（javap 全量），真正的解析发生在 `NativeJavaPackage.getPkgProperty`，那里先调
`ClassShutter.visibleToScripts(name)` 再 `Kit.classOrNull`。`ScriptImporterCheck` 有 12 条断言
（`importClass(java.lang.System)`、`Packages.java.lang.System.nanoTime()`、`importPackage(java.lang)`
后 `System.nanoTime()` 等）钉住这件事，**白名单一个字没放宽**。

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
| `Resources.getSystemFont` | **1 包 / 3 处（HKR Split-Flap Display）** | ✅ **2.5 新增** | 语义按 `javap -p -c ScriptResourceUtil.getSystemFont` 三个分支实现：`"Noto Sans"` → MTR 的 `mtr:font/noto-sans-semibold.ttf`（`Font.createFont`）、`"Noto Serif"` → `mtr:font/noto-serif-cjk-tc-semibold.ttf`、**其余名字** → 先在 JVM 字体注册表查 `new Font(name, PLAIN, 1)`，**系统里有的原样返回**（与 v2 逐字一致，`Monospaced` 这类逻辑族也算「有」）。HKR 写的 `"Noto Sans CJK TC Medium"` / `"Noto Sans SemiBold"` / `"Monospaced"` 三个都落第三支：`"Monospaced"` 不变；另外两个在没装它们的机器上 AWT 会**静默替换**（本机实测 `getFamily()`/`getFontName()` = `Dialog` / `Dialog.plain`）。对它们本移植**有意偏离 v2**，但**不采用替换字体**：`"Noto Sans SemiBold"`（拉丁）→ 自带拉丁 Noto；标记了 CJK 脚本的名字 → 先取**真实安装的无衬线 CJK 族**（`Noto Sans SC/TC/HK/JP/KR`、`Source Han Sans …`、`Microsoft YaHei`/`JhengHei`、`PingFang …`、`Hiragino Sans`、`Meiryo`、`Yu Gothic …`、`Malgun Gothic`，并优先匹配字重），一个都没有才用自带 CJK 文件（它是衬线，故为最后手段）。**不用替换字体的依据是像素实测**：本机 `Dialog` 的中文就是 `SimSun`（`下` 与 `SimSun` 的 `下` 像素完全相同，198 px），即"无衬线拉丁 + 宋体中文"。最终规则与四次尝试的错处见 `MTR3-PIDS-PORT.md` §4.20（§4.19 是被推翻的第二次尝试）。v2 里恒为 `false` 的 `hasNotoSansCjk` 分支未照搬（依据见 §1.3b） |
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

### 1.3b 三处「照字节码逐条对过」的成员（2.5）

| 成员 | v2 依据 | 我们的实现 | 差异与理由 |
|---|---|---|---|
| `Resources.getSystemFont(name)` | `javap -p -c com.lx862.mtrscripting.util.ScriptResourceUtil.getSystemFont` 全量；`<clinit>` 给出三个 `ResourceLocation`；`readFont` = `Font.createFont(0, readStream(id))` | `ScriptEngine.Resources.getSystemFont` + `ScriptFonts` | **只省略了一个恒不可达的分支**：`hasNotoSansCjk` 在整个类里只有一处赋值（静态初始化块，`iconst_0`）→ 恒 `false`，所以 `.otf` 那条路在参照 jar 里永远走不到；MTR 3.6.3 也没有这个文件（列 `libs/mtr-common.jar`：只有 `noto-sans-semibold.ttf` 515204 B、`noto-serif-cjk-tc-semibold.ttf` 28120132 B、`mtr.json`）。缓存字段加了 `volatile`/同步，值一样 |
| `GraphicsTexture` 的构造/上传链 | `javap -p -c` 的构造器、`upload`、`toAbgr`、`close` 全量 | `script.GraphicsTexture` | 逐条一致：`TYPE_INT_ARGB`、两条 RenderingHint、`for x∈[0,w) for y∈[0,h)`、`setPixelRGBA(x,y,toAbgr(getRGB(x,y)))`、`toAbgr = (a<<24)\|(b<<16)\|(g<<8)\|r`、`new NativeImage(w,h,false)`、`DynamicTexture` 的 `setFilter(false,false)`。**两处刻意的时序差异**：`DynamicTexture` 改到第一次 `upload()` 才建（v2 在构造器里建，且构造器里就 `Minecraft.execute(register)`），真上传在渲染线程上就地做（v2 一律 `RenderSystem.recordRenderCall` 延迟到帧末）。理由都是「让画布在没有游戏时也能创建/画/关」，脚本可观察语义不变 |
| `SoundManager.playSound` / `playLocalSound` 的**参数个数** | 参考 jar 里**没有** `SoundManager`（全 jar 扫描 `getSoundManager` / `playLocalSound` 字符串 0 命中），故依官方文档签名 | `ScriptSound.SoundManager` | **实测**（独立最小复现程序）：Rhino 按参数个数**精确**匹配，不补默认值——`SM.playSound('id',null,0.5,1.2)` 抛 `EvaluatorException: Can't find method ...playSound(string,null,number,number)`。HKR 用的正是 4 参 / 3 参形式，两次调用都解析失败，而脚本把播放包在 `try{}catch(e){}` 里 → **没声音、日志里什么都没有**。2.5 补了两个少参数重载，`soundCategory` 传 `null` 交给现有的 `toCategory(null)` → `MASTER` |

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
| `ctx.getSoundManager()` | ✅ | 1 包 / 1 处 | 琼岭；**HKR Split-Flap Display 也用（1 处）**——它调的是 `playSound(id, pos, v, p)` / `playLocalSound(id, v, p)`，2.5 补了这两个少参数重载（见 §1.3b） |
| `ctx.setDebugInfo(key, value)` | ❌ **未实现** | **1 包 / 1 处（HKR Split-Flap Display）** | **不适用，而不是缺口**：全 jar 扫描参考实现的字符串 `setDebugInfo` = **0 命中**，即它不是本移植所依据的那个 v2 构建里的 API，没有名字/签名/语义可依，实现它就是硬造。可观测影响为**零**：HKR 只在自身的 `DEBUG_INFO` 常量（**出厂为 `false`**）为真时才调用它，而且整段包在 `try{}catch(e){}` 里。记为「包的 JCM 版本与本参照 jar 的版本差」，不是 MTR 4 专有物 |
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

> **2.5 追加的一个包，不在上面这 112 里**：用户报障的 `HKR-Split-Flap-Display.zip`
> （预设 `next_train`，1 个脚本 `next_train.js`，81 KB，除缩略图外无任何贴图）此前**加载期就抛**
> `ReferenceError: "importPackage" is not defined`，面板全透明。修好 importer 与
> `Resources.getSystemFont` 之后，它在 **0 / 1 / 2 / 4** 四种班次数下都是 `RESULT: SCRIPT OK`、
> 1 条绘制调用（`Texture(... size=128.0x72.0 texture=jsblock:dynamic/graphics/<uuid> ...)`），
> 且 `state.err == null`——画的是它自己的内容，不是脚本的错误屏。
> 复现命令见 §5。为了不改变 112/108 这个基线的口径，它**单独列出**，不并入下表。

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
| 5 | `london_bus.js` | `World_PIDS-Pack-200` | 第 130 行 | `TypeError: Cannot call method "routeNumber" of null` | **包的脚本自身缺陷**：它按**墙上时钟**把行下标从 1/2/3 顶到 4/5/6 甚至 7/8/9，之后整块板都取到 `null`。**与 2.5 的任何改动无关**（该脚本只用到 `Text` / `pids.arrivals()` / `TextUtil.cycleString` / `Date`）。见 §2.2b | 强制复现 |

#### 2.2b `london_bus.js`：一个**按墙上时钟发作**的包脚本缺陷（2.5 发现，先于本次改动存在）

这一条不在 2.4 的 4 条里，因为它**不是每次都失败**。机制完全在脚本自己身上：

```js
let varA = 1, varB = 2, varC = 3;            // 行下标
function animation_arrive() {
    let sec = (new Date).getSeconds();
    let phase = sec % 10;
    if (phase === 9) {                       // 只在「秒数末位是 9」的那一秒动手，且同一秒只动一次
        if (!hasTriggered) {
            if (varA < 5 && varB < 6 && varC < 7) { varA += 3; varB += 3; varC += 3; }
            else                                  { varA = 1; varB = 2; varC = 3; }
            hasTriggered = true;
        }
    }
    ...
    if (phase === 1) { hasTriggered = false; }   // 只复位标志位，**不复位下标**
}
...
for (let i = 0; i < 4; i++) {
    let arrival = pids.arrivals().get(i);
    if (arrival != null) {
        if (i === 0) { ... }                     // 第 0 行用的是 get(i)，安全
        else {
            let x = varA; if (i === 2) x = varB; if (i === 3) x = varC;
            arrival = pids.arrivals().get(x);    // <-- 这里**没有**判空
            Text.create("arrival routeNumber").text(arrival.routeNumber())   // <-- 第 130 行
```

**为什么它会坏，而且坏多久**：下标每 10 秒被顶一次（`1/2/3 → 4/5/6 → 7/8/9 → 1/2/3`，第三次因为
`7 < 5` 不成立才复位），而 `phase === 1` 只复位 `hasTriggered`、**不复位下标**。所以在**一个长会话里**
下标处在「坏」状态的时间是 **3 个 10 秒周期里的 2 个（约 2/3）**。坏状态下：

| fixture / 面板行数 | `1/2/3` | `4/5/6` | `7/8/9` |
|---|---|---|---|
| 0 班车 | 安全（`get(i)` 全 null，走不到第 130 行） | 安全 | 安全 |
| 2 班车 | 安全（第 1 行 `x=varA=1` 有效；第 2、3 行 `arrival==null` 被跳过） | **第 1 行 `get(4)` → null → 抛** | **抛** |
| 4 班车 | 安全 | **第 1 行 `get(4)` → null → 抛** | **抛** |

**实测频率**：每次 `ScriptApiCheck` 是一个**新进程**，下标从 `1/2/3` 开始，而一次调用只渲染 3 帧、
总时长不到一秒，所以「撞上 `:09` 那一秒」的机会不大——`--arrivals=2` 连跑 **20 次失败 2 次（10%）**，
失败的两次正是渲染跨过 `:09` 的那两次。一轮完整回归含 0/2/4 三个班次数（0 安全），因此
**单轮回归约 10–19% 会掉这一分**。这在游戏里是另一回事：会话是长命的，所以**这块板大部分时间是坏的**，
只是引擎在第一次抛出后 `adoptLenientArrivals()` 换成占位到达重试并保持，玩家看到的是一行占位
而不是黑屏，因此此前没人发现。

**强制复现（决定性证据，与墙上时钟无关）**：把包脚本复制到临时目录（**没有改动任何资源包**），
只把 `let sec = now.getSeconds()` 改成 `let sec = 9;`：

```
FAIL render() threw on iteration 0: ... TypeError: Cannot call method "routeNumber" of null (london_bus.js#130)
FAIL render() threw on iteration 1: ... TypeError: Cannot call method "routeNumber" of null (london_bus.js#130)
RESULT: 2 FAILURE(S)
```

即失败**完全由那一秒决定**，与 2.5 的任何改动无关（该脚本只用到 `Text` / `pids.arrivals()` /
`TextUtil.cycleString` / `Date`；2.5 碰的三个面——importer、`Resources.getSystemFont`、
`SoundManager` 参数——它一个都没用）。

**对「108/112」这个基线的影响**：基线本身带约 10–19% 的抖动，**不是** 2.5 引入的。
整轮 `run-all.ps1` 在 2.5 之后跑了 4 次：**108 / 107 / 107 / 107**（第一次 108 就是没撞上那一秒）。
把 `World_PIDS-Pack-200` **单独重跑一次**（`run-pack.ps1`，同一个 `build\pids-check`，
结果落在 `result-World_PIDS-Pack-200-rerun.txt`）拿到 **66/66**，与其余 8 个包的既有结果合计即
**112 个脚本、108 通过、4 失败**。

因此本表的结论应读作：**108/112；撞上 `london_bus.js` 那一秒的整轮是 107**，
而 2.5 的改动**没有让任何脚本掉出通过**——失败集始终是
`{sound_transit, pids_ql, tools, pids_util}`（2.4 的 4 条），加上 `london_bus.js` 这个
**先于本次改动的、按秒表发作的**第 5 条。

**因此换算口径**：112 个 `.js` 里，**110 个是可执行的预设脚本**（扣掉 2 个工具模块），
其中 **108 个通过**，2 个因无头环境缺世界数据而无法在检查里成立 —— 这 2 个记为
**「不适用（无头环境）」**，不记为缺陷。另有第 5 条（`london_bus.js`）是**包的脚本自身按秒表发作**的
偶发失败，见 §2.2b：撞上「秒数末位是 9」的那一秒时整轮总数是 **107**，其余时候是 **108**。

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
| `Resources.read` / `readString` / `readFont` / `idr` / `exist` / `manager` / `getNTEVersion*` / `hasSystemFont` / `ensureStrFonts` / `getFontRenderContext` | `ScriptResourceUtil` | **1 个包（无锡）**，且**全部在 `assets/mtr/**`** | 见 §3.2：那是**另一套脚本宿主**（MTR 的 map / LCD 脚本），不是 `assets/jsblock/scripts/**` 的 PIDS 预设。PIDS 脚本一个都没用过。**`getSystemFont` 已从这一行移出：2.5 已实现**（§1.3b），因为 HKR Split-Flap Display 这个真 PIDS 预设用了它 3 次 |
| `TextUtil.getNonCjkAndExtraParts` | `jcm.mod.scripting.mtr.util.TextUtil` | 0 | 已实现（我们甚至有），只是 v2 对照表里的一行 |
| `VanillaText`（11 条） | `jcm.mod.scripting.mtr.util` 一族 | 0 | 只服务 `displayMessage(VanillaText, ...)` 的富文本重载 |
| `Siding` / `PathData` / `Vector` / `Position` / `Rail`（43 条） | TSC 数据类 | 0（PIDS 内） | 实际由 **车辆脚本** 使用，不属于 PIDS |
| `Station` 的几何成员（`getMinX/Y/Z`、`getMaxX/Y/Z`、`getExits`、`inArea`、`getCenter`、`isTransportMode`） | `mtr.data.Station`（MTR 4） | 0 | MTR 3 的 `Station` **没有**这些成员（javap 确认：只有 `zone` / `exits` / `getGeneratedExits` 等），要做得自己算站台包围盒 |
| `PIDSScriptContext.getRenderManager()` / `setDebugInfo()` | `jcm.mod.scripting.jcm.pids.PIDSScriptContext` | **`setDebugInfo` 1 包 / 1 处（HKR Split-Flap Display，2.5）**；`getRenderManager` 0 | 3D 模型渲染，与 PIDS 面板无关。`setDebugInfo` **在参照 jar 里不存在**（全 jar 字符串扫描 0 命中），所以没有可移植的语义；且包只在自身 `DEBUG_INFO = false` 时才会调用、外面还套着 `try{}catch{}` → **不适用**，见 §1.5 |
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

### 3.4 2.5 新增的「未验证 / 存疑」清单

- **`GraphicsTexture` 的真 GL 上传路径仍未在实机跑过**。这是 2.3 就留下的那条，2.5 只把它**缩小**了：
  `javap -p -c` 逐行对照确认了 `TYPE_INT_ARGB` / 双重循环 / `toAbgr` 字节序 /
  `new NativeImage(w,h,false)` / `DynamicTexture` 的 `setFilter(false,false)`（最近邻）；
  `ScriptCanvasCheck.checkAbgrSwap` 把字节序那一步变成了无头断言；`ScriptImporterCheck` 把
  「AWT 画到 1088×608 画布 → 像素读回 → 变成一条贴图绘制调用」整条跑通。
  **仍然只能实机确认的是最后一段**：`new NativeImage` → `setPixelRGBA` → `DynamicTexture.upload()`
  → 屏幕上出现图像。实机自检方法写在 `MTR3-PIDS-PORT.md` §4.18：2.5 起第一次成功上传会打一行
  `PIDS script canvas WxH uploaded as texture <id>`；**有这行**说明像素已进显存（问题在采样/混合/像素化），
  **没有这行**则会有那条 `cannot be uploaded to the GPU: ...` 警告并带原因。
- **`Resources.getSystemFont("Noto Sans")` 在无头检查里读的是 classpath 上的
  `assets/mtr/font/noto-sans-semibold.ttf`**（`ScriptFonts.headlessSource`，包私有，只在
  `Minecraft.getInstance() == null` 时才被问到）。**游戏里走的是资源管理器**那条路——即
  `ResourceManager.getResource(mtr:font/noto-sans-semibold.ttf)`——这一条**未实测**（同一份字节，
  不同的取法）。HKR 实际用的三个名字都落在 `new Font(name, PLAIN, 1)` 分支，**不经过文件读取**，
  所以这一点不影响它的画面。
- **`SoundManager` 的两个少参数重载只验证了「解析成功」**。无头没有声音引擎，也没有客户端，
  `play()` 在 `Minecraft.getInstance() == null` 时直接返回。**「声音真的响了」未实测**。
- **`ctx.setDebugInfo` 未实现**，依据见 §1.5（参照 jar 里不存在）。如果将来拿到与该包匹配的 JCM 版本，
  这一条应当重查——它是**版本差**，不是「MTR 4 专有物」。
- **新增的 `ScriptApiCheck` 的 `state.err` 断言是启发式的**：它只认「键名含 `err`（不区分大小写）
  且值是非空**字符串**」。一个把错误信息放在别的键名里的预设不会被它抓到；反过来，
  一个合法的、值恰好是非空字符串的 `*err*` 键会误报。112 个脚本上**零误报**，但这是经验事实而不是保证。

---

## 4. 检查用例

`tools/run-pids-check.ps1` 最终输出 `RESULT: ALL CHECKS PASSED`。2.4 新增两个检查，2.5 新增一个，
字体回退又新增一个：

| 检查 | 盯住什么 | 关键断言 |
|---|---|---|
| `ScriptFontFallbackCheck`（**新增**） | `Resources.getSystemFont` 第三支：系统里没有的名字按**脚本标记 + 已安装族名**选面（CJK → 无衬线 CJK 族优先，内置衬线兜底；拉丁/`serif` → 内置面），系统里有的原样返回 | `"Noto Sans SemiBold"` 拿到自带拉丁面而不是 `Dialog`（family/fontName + `deriveFont(BOLD,40)` 可缩放）；`"Noto Sans CJK TC Medium"` 拿到**本机真实安装的无衬线 CJK 族** `Noto Sans SC Medium`（而不是替换字体 `Dialog.plain`），且它画 `下` 与 `班` 是**两个不同字形**（378 vs 738 px，防豆腐块连像素一起断言）；**判定函数 `substituteFor(替换字体, 请求名)` 被直接调用**，断言"有 CJK 族时用它、没有时用自带 CJK 面"两条路（含拉丁字体做替换字体时选到自带 CJK 面、`canDisplay('下')` 为真、原因里点名缺哪个字），于是**没有 CJK 的机器也能验有 CJK 的那条路、反之亦然**；`"Noto Sans"`/`"Noto Serif"` 两个文件分支逐条不变（仍是 `createFont`、size 1、拉丁面无 CJK）；本机真实存在的族（`"Noto Sans SC"`）与族名表里没有的 **face 拼写**（`"Noto Sans SC Bold"`）都原样返回系统字体；逻辑族 `Dialog`/`Monospaced` 不被劫持；按名缓存命中同一实例、`resetFonts()` 清掉；每条裁决**只打一行 INFO**（用 log4j appender 现场抓，含请求名 / AWT 替换成什么 / 最终哪张面 / 原因）；无资源管理器时软失败不抛异常（CJK 名字走已安装族，一个文件都不读）。另含**偏离依据的像素断言**：自带拉丁文件画 `下`/`班` 是同一个 246 像素缺字方框，自带 CJK 面是两个不同字形（293 vs 611 px） |
| `ScriptImporterCheck`（**2.5 新增**） | Rhino importer 全局、经 importer 的沙箱、AWT 画布路径 | 9 个全局的 `typeof`；AWT/Java2D 类经 `importPackage` 与全限定两条路都解析；`Resources.getSystemFont` 三个分支（含真读 `mtr:font/noto-sans-semibold.ttf` 与 `deriveFont(int,float)`）；**12 条**拒绝断言（每条都**使用**类：`importClass(java.lang.System)`、`Packages.java.lang.System.nanoTime()`、`importPackage(java.lang)` 后 `System.nanoTime()` / `System.exit(0)`、`importPackage(java.io)` 后 `new File(".")`、`importPackage(java.lang.reflect)` 后 `Method.class`、`java.nio.file.Files.readString` …）＋ 允许侧正对照 ＋ `typeof System/Class/Runtime == undefined`；AWT 画布脚本：1088×608、背景/色块/文字像素读回、1 条贴图绘制调用、两次 upload 只报 1 次、释放 |
| `FilesCheck` | `Files` 全局（唯一写盘的那一个） | 存档-读回一致；缺文件返回 `null` 不抛；UTF-8 多行往返；`saveData` 自动建目录；`deleteData` 幂等；**7 种逃逸形态**（`..`、`..`×2、`../..`、`a/../../..`、深逃逸、绝对路径、Windows 路径）× 4 个方法全部拒绝且文案为 v2 原文；从脚本侧（Rhino + 真作用域）跑 `hasData/readData/saveData`，含 `met_running_board` 的真实调用形状；`rootOverride` 对脚本不可达 |
| `ScriptCaseCheck` | 引用拼写的宽容 | 精确引用**不被折叠**；大写折叠成可用 `ResourceLocation`；命名空间大小写也折叠；**6 种逃逸形态在两条路径上都仍被拒绝**；`caseFoldedCandidates` 的候选顺序；磁盘查找：小写引用找到大写文件、精确拼写优先于近似、多文件互不串味、拒绝向上爬出根；**以及 `ScriptPackFiles.readFromDirectoryRoot` 本身**——用 met transit 的真实资产路径（`assets/jsblock/Scripts/Digital_Rail.js`）断言精确命中、小写拼写同样命中、不存在的名字返回 `null` |

`ScriptApiCheck` 未改动其断言，只是（1）资源根可以给多个、（2）`include` 的磁盘解析加了同样的大小写宽容。
2.5 给它加了 `checkOptionalArgumentForms`（声音的两种参数拼写都要能解析）与
`checkCreateReportedNoError`（预设自己把错误抓进 `state.err` 并画错误屏时**按名字判失败**，
不再在错误屏上盖一个绿色 RESULT）。

---

## 5. 复现命令

```powershell
# 无头检查（全部用例，含 ScriptImporterCheck）
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
cd work\yjcm-mtr3
.\tools\run-pids-check.ps1

# 用出问题那个真包跑（HKR Split-Flap Display / next_train.js，0/1/2/4 四种班次数）
.\tools\run-pids-check.ps1 -SkipBuild `
  -ResourceRoot ..\..\_hkr_split `
  -Script       ..\..\_hkr_split\assets\jsblock\scripts\next_train.js

# 单个真实包脚本
.\tools\run-pids-check.ps1 -SkipBuild `
  -ResourceRoot ..\..\_v2api\packs\met transit\lower `
  -ResourceRoot ..\..\_v2api\packs\met transit\raw `
  -Script       ..\..\_v2api\packs\met transit\lower\assets\jsblock\scripts\met_running_board.js

# 9 个真实包、112 个脚本的整轮回归
cd ..\..\_v2api
.\run-all.ps1        # 末行 GRAND TOTAL: 112 scripts run, 108 passed, 4 failed
```

真实包的提取与批量运行脚本（本次一次性工具，留在 `_v2api/` 供复算）：
`scan_api.py`、`members.py`、`diff_globals.py`、`presets.py`、`prepare_packs.py`、`prepare_all.py`、
`run-pack.ps1`、`run-all.ps1`；
原始产物：`usage-table.md`、`members-by-root.md`、`preset-scriptfiles.txt`、`ref-check.txt`、
`result-*.txt`、`run-all.log`。

---

## 附：本表涉及的行数统计

- §1.1 全局对照 **18 行**；§1.1b importer 全局 **9 行**；§1.2 `Files` 方法对照 **7 行**；
  §1.3 成员对照 **19 行**；§1.3b 逐字节码对照 **3 行**；§1.5 上下文对照 **15 行**
- §2.1 重点包逐脚本 **15 行**；§2.2 失败归因 **4 行**
- §3.1 已知缺口 **7 组**；§3.3 未验证清单 **5 条**（2.4）；§3.4 **5 条**（2.5）
- §4 检查用例 **3 个新增**（`FilesCheck` / `ScriptCaseCheck` / `ScriptImporterCheck`）
