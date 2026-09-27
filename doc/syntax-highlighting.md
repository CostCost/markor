# Markor 编辑器语法高亮实现原理

本文分析 Markor 编辑模式下语法高亮（Syntax Highlighting）的完整实现机制，包括核心控件 `HighlightingEditor`、高亮器基类 `SyntaxHighlighterBase`、各格式的具体高亮器、格式注册中心 `FormatRegistry`，以及搜索高亮、配置化代码高亮等周边机制。

> 分析基准代码路径：`app/src/main/java/net/gsantner/markor/frontend/textview/HighlightingEditor.java`

---

## 1. 总体思路

Markor **没有使用任何第三方编辑器/词法分析库**，而是完全基于 Android 原生的 **Spannable 文本 + Span（样式区间）机制** 自研了一套高亮系统。

Android 的 `EditText` 内部用 `SpannableStringBuilder` 管理文本、用 `DynamicLayout` 管理排版，二者在 **Span 数量很大时性能很差**。一篇长文档可能产生成千上万个高亮 Span，如果一次性全部挂到文本上，滚动和输入都会卡顿。

Markor 的核心应对策略是：

1. **全量计算**：为整篇文档计算出所有高亮 Span，但只保存在高亮器自己的列表里，不立即挂到文本上；
2. **视口挂载**：只把与当前可见区域（及其缓冲带）相交的 Span 真正 `setSpan` 到文本上；
3. **滚动时换挂**：滚动时把离开区域的 Span 摘掉，把进入区域的 Span 挂上；
4. **静态 / 动态分离**：影响排版的 Span（如放大标题）全局只挂一次，不随滚动更新，避免文字跳动；
5. **异步 + 防抖**：文本变化后，重算在后台线程执行，并用防抖合并连续输入；
6. **输入时位移（fixup）**：用户打字时先把已有 Span 的位置整体平移，使滚动时挂载的区间始终正确。

这套机制在基类的 Javadoc 中有集中说明，见 [SyntaxHighlighterBase.java#L50-L92](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/frontend/textview/SyntaxHighlighterBase.java#L50-L92)。

---

## 2. 涉及的类及其职责

### 2.1 核心类

| 类 | 位置 | 职责 |
|---|---|---|
| `HighlightingEditor` | `frontend/textview/` | 编辑器控件，继承 `AppCompatEditText`；持有高亮器，监听文本变化/滚动/布局变化，驱动“重算—挂载—滚动更新”全流程 |
| `SyntaxHighlighterBase` | `frontend/textview/` | 高亮器抽象基类；管理 Span 的计算缓冲、当前集合、动态挂载状态、输入位移（fixup）、重排（reflow），并提供大量正则→Span 的辅助方法 |
| `SyntaxHighlighterBase.SpanGroup` | 内部类 | 一个 Span 与其区间 `[start, end)`、挂载标志的封装；按 `start` 可排序 |
| `SyntaxHighlighterBase.HighlightSpan` | 内部类 | 多属性字符样式 Span（粗体/斜体/下划线/删除线/前景色/背景色/字号缩放），同时充当“每个匹配项生成一个独立 Span”的工厂回调 |
| `SyntaxHighlighterBase.StaticSpan` | 内部接口 | 标记接口：实现它的 Span 被视为静态 Span，挂载后统一触发一次重排 |

### 2.2 各格式的具体高亮器（均在 `format/<格式>/` 下）

| 高亮器 | 支持格式 | 说明 |
|---|---|---|
| `MarkdownSyntaxHighlighter` | Markdown | 用一组预编译正则匹配粗体、斜体、标题、链接、列表、引用、删除线、行内代码等 |
| `CsvSyntaxHighlighter` | CSV | 继承 Markdown 高亮器，额外按列循环着色 |
| `TodoTxtBasicSyntaxHighlighter` | todo.txt 基础规则 | 上下文 `@ctx`、项目 `+project`、优先级 A–Z、日期、已完成任务等 |
| `TodoTxtSyntaxHighlighter` | todo.txt 完整版 | 继承 Basic，额外挂一个全局 `ParagraphDividerSpan`（静态 Span），在任务之间加分隔线与段间距 |
| `OrgmodeSyntaxHighlighter` | Org mode | 标题、强调、代码块、列表、链接、注释等 |
| `AsciidocSyntaxHighlighter` | AsciiDoc | 标题、各类块（listing/quote/example…）、角色、属性、链接等 |
| `WikitextSyntaxHighlighter` | WikiText / Zim | 标题、强调、预格式化、清单复选框、链接、Zim 文件头 |
| `KeyValueSyntaxHighlighter` | 键值格式（INI/properties/vCard 等） | 键名、INI 头、注释等着色 |
| `PlaintextSyntaxHighlighter` | 纯文本 / 代码 | 通用高亮器；可按文件扩展名从 assets 加载 JSON 语法规则（见 §8） |

### 2.3 周边支撑类

| 类 | 职责 |
|---|---|
| `FormatRegistry`（`format/`） | 格式注册中心。按格式 ID 创建一整套配套对象：高亮器、转换器（预览用）、操作按钮、自动格式化 InputFilter/TextWatcher |
| `TextSearchHandler`（`frontend/textsearch/`） | 搜索逻辑；把搜索匹配项包装成背景色 SpanGroup，通过 `HighlightingEditor.setSearchMatches()` 注入 |
| `HighlightConfigLoader`（`format/plaintext/highlight/`） | 从 assets 读取代码语法定义与配色主题，带 LRU 缓存 |
| `Syntax` / `CodeTheme` | JSON 映射对象：语法规则列表（type + regex）、主题（type → 颜色） |
| `ColorUnderlineSpan`（`format/general/`） | 彩色下划线 Span；通过反射调用隐藏 API `TextPaint.setUnderlineText(int, float)`，并识别 `#RRGGBB` 十六进制颜色文字 |
| `WrMarkdownHeaderSpanCreator` / `WrProportionalHeaderSpanCreator`（`app/thirdparty/.../writeily/`） | 来自 Writeily 项目的标题 Span 工厂：根据 `#` 数量或 Setext 下划决定放大比例，生成影响排版的 `MetricAffectingSpan` |
| `WrWikitextHeaderSpanCreator`、`WrAsciidocHeaderSpanCreator` | 同上，分别用于 WikiText / AsciiDoc |
| `TextViewUtils.makeDebounced()` | 生成防抖 Runnable（基于 Handler），用于合并连续输入后的重算 |
| `GsTextWatcherAdapter`（opoc 库） | `TextWatcher` 空实现适配器，只需重写关心的回调 |

---

## 3. HighlightingEditor：高亮流程的驱动器

源码：[HighlightingEditor.java](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/frontend/textview/HighlightingEditor.java)

### 3.1 关键字段

- `_hl`（`SyntaxHighlighterBase`）：当前高亮器；
- `_hlEnabled`：高亮总开关；
- `_hlDebounced`：**防抖后的重算任务**，文本停止变化一段时间后才真正重算；
- `_hlRect` / `_oldHlRect`：当前可见矩形 / 上次挂载高亮时的矩形，用二者差异判断滚动是否“足够大”；
- `_hlShiftThreshold`：滚动阈值（像素），超过它才重新换挂 Span，初始为 `字号 × 8`，见 [HighlightingEditor.java#L254-L260](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/frontend/textview/HighlightingEditor.java#L254-L260)；
- `executor`：`ThreadPoolExecutor(0, 3, …)`，最多 3 个线程执行后台重算；
- `_textUnchangedWhileHighlighting`：标记后台计算期间文本是否被改动，用于丢弃过期结果；
- `_matches`：搜索匹配项的 SpanGroup 列表（附加高亮）。

常量（[HighlightingEditor.java#L59-L60](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/frontend/textview/HighlightingEditor.java#L59-L60)）：

- `HIGHLIGHT_SHIFT_LINES = 8`：滚动约 8 行才更新一次高亮；
- `HIGHLIGHT_REGION_SIZE = 0.75f`：实际高亮区域比一屏多 75%，保证滚出前已完成挂载。

### 3.2 构造时挂的三类监听

见 [HighlightingEditor.java#L102-L131](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/frontend/textview/HighlightingEditor.java#L102-L131)：

1. **TextWatcher**
   - `onTextChanged`：立即调用 `_hl.fixup(start, before, count)` —— 把已有 Span 的位置平移，适应当前编辑；同时把 `_textUnchangedWhileHighlighting` 置 false，使任何在途的后台计算结果作废；
   - `afterTextChanged`：运行 `_hlDebounced`，延迟触发后台全量重算；
2. **ViewTreeObserver.OnScrollChangedListener**：滚动时调用 `updateHighlighting()`；
3. **ViewTreeObserver.OnGlobalLayoutListener**：布局变化（如软键盘弹出、换行变化）时同样调用 `updateHighlighting()`。

### 3.3 两条更新路径

编辑器把“滚动时换挂”和“全量重算”严格分开：

**（A）滚动 / 布局变化 —— 轻量路径**，[HighlightingEditor.java#L183-L189](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/frontend/textview/HighlightingEditor.java#L183-L189)：

```java
_hl.clearDynamic().applyDynamic(hlRegion());
```

- 摘掉所有动态 Span，再把视口区域内的动态 Span 挂上；
- **不使用 batch**，因此不触发重排，光标焦点与滚动位置不会跳动。

**（B）文本变化后的全量重算 —— 异步路径**，[HighlightingEditor.java#L212-L236](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/frontend/textview/HighlightingEditor.java#L212-L236)：

1. 防抖到期后，把 `_recomputeHighlightingWorker` 提交到线程池；
2. 后台线程：置“文本未变”标志，调用 `_hl.compute()` 生成 Span 到缓冲区；
3. `post()` 回主线程：若期间文本没有变化，则在 `beginBatchEdit/endBatchEdit` 之间执行
   `clearStatic → clearDynamic → setComputed → addAdditional(搜索项) → applyStatic → applyDynamic(视口)`；
4. 若期间又打字了，本次结果直接丢弃（下一次防抖会再触发）。

`recomputeHighlighting()` 是同步版本（如切换格式、重新变为可见时调用），同样在 batch 中完成并保存/恢复滚动位置，见 [HighlightingEditor.java#L191-L204](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/frontend/textview/HighlightingEditor.java#L191-L204)。

### 3.4 视口区域的计算

`hlRegion()`（[HighlightingEditor.java#L288-L303](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/frontend/textview/HighlightingEditor.java#L288-L303)）以可见矩形中心为中点，向上下各扩展 `0.75 × 屏高 + 滚动阈值`，再通过 `Layout.getLineForVertical()` → `getLineStart()/getLineEnd()` 把像素坐标换算成字符偏移 `[行起始, 行结束)`。

### 3.5 何时真正执行：runHighlight

`runHighlight(recompute)`（[HighlightingEditor.java#L176-L181](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/frontend/textview/HighlightingEditor.java#L176-L181)）按顺序判断：高亮器与 Layout 存在、视图可见（或强制重算）、已有计算结果、滚动量超过阈值。判断顺序经过刻意安排，避免无谓计算。

### 3.6 设置/切换高亮器

`setHighlighter(newHighlighter)`（[HighlightingEditor.java#L238-L252](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/frontend/textview/HighlightingEditor.java#L238-L252)）：先清掉旧高亮器的全部 Span，再绑定新高亮器，并用其 `getHighlightingDelay()` 重新生成防抖任务，最后立即做一次全量重算。

---

## 4. SyntaxHighlighterBase：Span 生命周期管理

源码：[SyntaxHighlighterBase.java](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/frontend/textview/SyntaxHighlighterBase.java)

### 4.1 静态 Span 与动态 Span 的判定

所有 Span 都被包装成 `SpanGroup`，构造时按 Span 实现的接口自动分类（[SyntaxHighlighterBase.java#L160-L168](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/frontend/textview/SyntaxHighlighterBase.java#L160-L168)）：

- 实现 `android.text.style.UpdateLayout`（如 `MetricAffectingSpan`、`RelativeSizeSpan`、`TypefaceSpan`）→ **静态**（`isStatic = true`）。这类 Span 一旦变化就会触发布局更新，所以全局只挂一次、不随滚动更新；
- 实现自定义的 `StaticSpan` 接口（继承 `UpdateAppearance`）→ 静态且 `needsReflow = true`：挂完之后由框架**统一触发一次重排**，而不是每个 Span 各触发一次；
- 其余（仅 `UpdateAppearance`，如前景色/背景色/粗体绘制态）→ **动态**，可随滚动安全地反复挂摘。

这是性能设计的关键：动态 Span 只改变绘制、不改变排版；静态 Span 只在重算时整体处理一次。

### 4.2 两组集合与缓冲区

- `_groupBuffer`：正在计算的 Span 集合。派生类在 `generateSpans()` 中通过 `addSpanGroup()` 写到这里；
- `_groups`：当前生效的 Span 集合。`setComputed()` 把 buffer 交换进来；
- `_appliedDynamic`（`TreeSet<Integer>`）：`_groups` 中当前已真正 `setSpan` 的动态组下标，用于只挂尚未挂载的组、倒序摘除已挂载的组。

双缓冲保证后台计算不破坏当前正在显示的 Span。

### 4.3 计算流水线

```
compute()                          // 清空 buffer → 调 generateSpans() → 按 start 排序（排序显著提升性能）
   ↓
setComputed()                      // buffer 转正：_groups = buffer，清空挂载记录
   ↓
applyStatic()                      // 挂载全部静态组；若有 StaticSpan 则统一 reflow 一次
   ↓
applyDynamic(int[]{start, end})    // 只挂载与区域相交、且尚未挂载的非静态组
```

对应代码：`compute()` 见 [#L434-L452](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/frontend/textview/SyntaxHighlighterBase.java#L434-L452)；`setComputed()` 见 [#L418-L426](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/frontend/textview/SyntaxHighlighterBase.java#L418-L426)；`applyStatic()` 见 [#L373-L393](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/frontend/textview/SyntaxHighlighterBase.java#L373-L393)；`applyDynamic(range)` 见 [#L347-L371](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/frontend/textview/SyntaxHighlighterBase.java#L347-L371)。

`compute()` 会捕获所有 `Exception`/`Error` —— **高亮逻辑不允许把 app 搞崩**，即使某条正则写错也只是放弃本次高亮。

### 4.4 fixup：输入时平移 Span

用户每敲一个字就全量重算太贵，于是输入瞬间先做位移：

- `fixup(start, before, count)` 转换为 `fixup(start + before, count - before)`，即“在某位置之后，区间长度变化了 delta”；
- 所有 `start` 在该位置之后的 SpanGroup，`start/end` 都加上 `delta`；
- 连续的 fixup 会被**缓冲合并**（重叠区间累加 delta），在下次挂载前由 `applyFixup()` 一次性落地，见 [#L296-L331](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/frontend/textview/SyntaxHighlighterBase.java#L296-L331)。

这样，即使全量重算还没完成，滚动时挂载的动态 Span 也在正确位置上。

### 4.5 reflow：用标记 Span 强制重排

`reflow(range)`（[#L400-L406](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/frontend/textview/SyntaxHighlighterBase.java#L400-L406)）在指定区间挂一个空的、实现了 `UpdateLayout` 的 `ForceUpdateLayout` 标记 Span，然后立刻移除。挂载动作会让 `DynamicLayout` 对该区间重新排版一次——既更新了行高，又不留下永久 Span。

### 4.6 提供给派生类的 Span 生成辅助方法

基类把“正则匹配 → 每个匹配生成 Span → 加入 buffer”封装成一组 `createXxxSpanForMatches()` 方法（[#L470-L559](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/frontend/textview/SyntaxHighlighterBase.java#L470-L559)）：

- `createSpanForMatches(pattern, creator, groups...)`：核心方法，遍历所有匹配，支持只对指定捕获组（而非整个匹配）生成 Span；
- `createStyleSpanForMatches` / `createColorSpanForMatches` / `createColorBackgroundSpan` / `createStrikeThroughSpanForMatches`；
- `createTypefaceSpanForMatches` / `createMonospaceSpanForMatches` / `createRelativeSizeSpanForMatches`；
- `createReplacementSpanForMatches`：生成空白 ReplacementSpan，用于把 Tab 替换成固定宽度（`createTabSpans`）；
- `createColoredUnderlineSpanForMatches`：彩色下划线；
- `createSuperscriptStyleSpanForMatches` / `createSubscriptStyleSpanForMatches`；
- `createSmallBlueLinkSpans()`：URL 蓝色斜体 85% 字号；
- `createUnderlineHexColorsSpans()`：文字中出现 `#RRGGBB(AA)` 时，用该颜色画下划线；
- `createTabSpans(tabWidth)`：按配置的 Tab 宽度对齐。

### 4.7 HighlightSpan：多属性合一的 Span

`HighlightSpan`（[#L562-L658](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/frontend/textview/SyntaxHighlighterBase.java#L562-L658)）继承 `CharacterStyle`、只实现 `UpdateAppearance`，因此**不触发布局**，可以安全地作为动态 Span 反复挂摘。它把粗体、斜体、下划线、删除线、前/背景色、字号缩放合并到**同一个 Span**，以减少 Span 总数。

它同时实现 `GsCallback.r1<Object, Matcher>`：`callback(matcher)` 返回一个属性相同的**新实例**。因此可以把一个 `HighlightSpan` 原型直接传给 `createSpanForMatches()`，每个匹配项都会得到独立 Span（Span 对象不能跨区间复用）。

### 4.8 附加 Span：搜索高亮的挂载点

`addAdditional()` / `clearAdditional()`（[#L667-L693](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/frontend/textview/SyntaxHighlighterBase.java#L667-L693)）允许把高亮器之外的 SpanGroup（搜索结果、选区标记）直接加入 `_groups`。因此附加 Span 同样受 fixup 位移管理；但由于它们不在 buffer 中，每次 `setComputed()` 后需要由调用方重新 `addAdditional()`——`HighlightingEditor` 在重算流水线中固定执行了这一步。

静态工厂 `createBackgroundHighlight(start, end, color)` 用于快速构造一个背景色 SpanGroup。

---

## 5. 具体高亮器如何工作

派生类只需做两件事：在 `configure()` 中读取本格式的个性化设置；在 `generateSpans()` 中调用基类辅助方法生成 Span。

### 5.1 Markdown（典型范例）

[MarkdownSyntaxHighlighter.java](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/format/markdown/MarkdownSyntaxHighlighter.java)

- 类静态字段是一组预编译 `Pattern`：`BOLD`、`ITALICS`、`HEADING`、`LINK`、`LIST_UNORDERED/ORDERED`、`QUOTATION`、`STRIKETHROUGH`、`CODE` 等（[#L22-L33](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/format/markdown/MarkdownSyntaxHighlighter.java#L22-L33)）；
- `configure()` 从 `AppSettings` 读取“是否放大标题/代码等宽字体/代码块底色/行尾双空格提示”等开关及重算延迟；
- `generateSpans()` 顺序调用：Tab/十六进制颜色/URL 等通用处理 → 标题（放大时用 `WrMarkdownHeaderSpanCreator`，否则只上橙色）→ 链接、列表、引用 → 粗体、斜体、删除线 → 行内代码等宽字体/底色。

标题放大 Span 是 `MetricAffectingSpan`（影响度量），自动归入**静态** Span，只挂一次。

### 5.2 CSV：继承 + 手工切列

[CsvSyntaxHighlighter.java](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/format/csv/CsvSyntaxHighlighter.java) 先调用 `super.generateSpans()`，再从首个候选分隔符推断分隔符/引号，逐列扫描，用 5 色循环为每列内容挂前景色 `HighlightSpan`。

### 5.3 todo.txt：基础规则 + 段落分隔

- [TodoTxtBasicSyntaxHighlighter.java](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/format/todotxt/TodoTxtBasicSyntaxHighlighter.java) 对 `TodoTxtTask` 中定义的各条正则（上下文、项目、键值对、A–F 与 G–Z 优先级、创建/截止日期、已完成任务）分别着色；
- [TodoTxtSyntaxHighlighter.java](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/format/todotxt/TodoTxtSyntaxHighlighter.java) 在全文挂**一个** `ParagraphDividerSpan`（同时实现 `LineBackgroundSpan`、`LineHeightSpan`、`StaticSpan`），在每行任务下方画线、加大段落间距。“一个 Span 管全文”是刻意的性能优化；它是 `StaticSpan`，挂载后统一 reflow 一次。

### 5.4 其他格式

`OrgmodeSyntaxHighlighter`、`AsciidocSyntaxHighlighter`、`WikitextSyntaxHighlighter`、`KeyValueSyntaxHighlighter` 结构完全一致：一组格式相关 Pattern + 一个 `generateSpans()`，区别只在匹配规则、配色与标题 Span 工厂。

---

## 6. FormatRegistry：高亮器是怎么被选中的

源码：[FormatRegistry.java](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/format/FormatRegistry.java)

每种格式由一组配套对象构成：`TextConverterBase`（Markdown→HTML 预览）、`SyntaxHighlighterBase`（编辑高亮）、`ActionButtonBase`（底部工具栏）、自动格式化的 `InputFilter`/`TextWatcher`。

- `FORMATS` 列表按优先级声明全部格式及其默认扩展名（[#L96-L107](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/format/FormatRegistry.java#L96-L107)）；格式识别由各 converter 的 `isFileOutOfThisFormat()` 按扩展名/内容头完成；
- `getFormat(formatId, context, document)` 是工厂方法，在 `switch` 中为每个格式 ID new 出对应的高亮器等对象（[#L128-L208](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/format/FormatRegistry.java#L128-L208)）。例如纯文本/代码用 `new PlaintextSyntaxHighlighter(appSettings, document.extension)`。

调用方在 [DocumentEditAndViewFragment.java#L768-L786](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/activity/DocumentEditAndViewFragment.java#L768-L786)：

```java
_format = FormatRegistry.getFormat(textFormatId, activity, _document);
_hlEditor.setHighlighter(_format.getHighlighter());
_hlEditor.setAutoFormatters(_format.getAutoFormatInputFilter(), _format.getAutoFormatTextWatcher());
```

编辑器本身的字号、字体、前景/背景色、高亮开关等则在 Fragment 初始化时统一配置，见 [DocumentEditAndViewFragment.java#L191-L209](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/activity/DocumentEditAndViewFragment.java#L191-L209)。高亮开关最终调用 `HighlightingEditor.setHighlightingEnabled()`：开启时重新初始化并触发一次重算，关闭时清掉全部 Span 与计算结果（[HighlightingEditor.java#L270-L285](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/frontend/textview/HighlightingEditor.java#L270-L285)）。

---

## 7. 搜索高亮

搜索由 [TextSearchHandler.java](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/frontend/textsearch/TextSearchHandler.java) 负责：

1. 用搜索 Pattern 对全文（或选区子序列）做 `matcher.find()`，每个匹配生成一个带背景色的 `Match`（内含 SpanGroup）；
2. 当前匹配项切换为“活动色”；
3. `highlightMatches()` 调 `HighlightingEditor.setSearchMatches(spanGroups)`（[TextSearchHandler.java#L128-L142](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/frontend/textsearch/TextSearchHandler.java#L128-L142)）。

`HighlightingEditor` 侧（[HighlightingEditor.java#L309-L359](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/frontend/textview/HighlightingEditor.java#L309-L359)）先清旧匹配、再通过 `_hl.addAdditional()` 注入新匹配。因此搜索结果背景色和语法高亮可以叠加，且在打字时会随 fixup 自动跟随。“在选区中查找”的选区范围则用 `addSearchSelection()` 挂一个背景色 SpanGroup 标记。

---

## 8. 配置驱动的通用代码高亮

打开无专用高亮器的代码文件（如 `.java`、`.py`、`.c/.cpp`）时走 `PlaintextSyntaxHighlighter`，其规则不在 Java 代码里，而在 assets 中以 JSON 声明：

目录 `app/src/main/assets/highlight/`：

- `languages/map.properties`：扩展名 → 语言名映射（如 `java=java`、`py=python`、`c=cpp`）；
- `languages/<lang>.json`：`Syntax` 定义。每条规则是 `{type, regex}`，如 Java 文件中 `KEYWORDS`、`STRING`、`LINE_COMMENT`、`FUNCTION` 等类型；
- `themes/default.json`：`CodeTheme` 主题，定义每个 `type` 对应的颜色。

[HighlightConfigLoader.java](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/format/plaintext/highlight/HighlightConfigLoader.java) 用 Gson 读取这些文件，并维护一个容量为 5、按使用次数淘汰的语法缓存。`generateSpans()` 对每条规则按其 `type` 查到颜色后调用 `createColorSpanForMatches()`（[PlaintextSyntaxHighlighter.java#L48-L64](file:///Users/shouhwang/Desktop/repo/android/markor/app/src/main/java/net/gsantner/markor/format/plaintext/PlaintextSyntaxHighlighter.java#L48-L64)）。

这意味着扩展新的代码语言**无需写 Java 代码**：加一条扩展名映射和一份 JSON 规则即可。

---

## 9. 线程模型与性能设计汇总

**线程分工**

- **主线程（UI）**：TextWatcher 回调、fixup 位移、滚动时换挂动态 Span、最终 `setSpan`；
- **后台线程池**（0–3 线程）：执行正则计算 `_hl.compute()`。结果通过 `post()` 回主线程应用；
- **过期保护**：`_textUnchangedWhileHighlighting` AtomicBoolean 保证“计算期间文本又变了”就丢弃结果；
- **防抖**：每次格式自定义重算延迟（Markdown 等默认可长达 2400ms，`LONG_HIGHLIGHTING_DELAY`），连续输入只在停顿后重算一次。

**性能要点（在代码注释中反复强调）**

1. Span 总数是性能关键，尽量把多个属性合并为一个 `HighlightSpan`；用“一个 Span 管全文”实现段落分隔；
2. 尽量减少实现 `UpdateLayout` 的 Span；需要影响排版时优先用 `StaticSpan`，把 N 次布局更新合并为一次 reflow；
3. 滚动换挂不使用 `beginBatchEdit/endBatchEdit`（batch 结束会触发重排、把视图拉回光标位置），因此滚动路径只做纯 Span 挂摘；
4. SpanGroup 按 `start` 排序，使挂载循环可提前 `break`、fixup 位移可提前终止；
5. 大文本下关闭 EmojiCompat、限制无障碍事件/自动填充，避免系统侧的额外开销。

---

## 10. 典型时序

### 10.1 打开文件

```
DocumentEditAndViewFragment 初始化
  → 配置字号/字体/颜色/高亮开关
  → applyTextFormat(formatId)
      → FormatRegistry.getFormat() 创建该格式的高亮器
      → HighlightingEditor.setHighlighter()
          → 高亮器.setSpannable(文本).configure(paint)
          → 生成防抖任务（延迟 = 该格式 highlightingDelay）
          → recomputeHighlighting()（同步，batch 内）
                compute → setComputed → applyStatic（必要时 reflow）→ applyDynamic(视口)
```

### 10.2 用户输入

```
onTextChanged  → _textUnchanged=false（作废在途计算）+ _hl.fixup()（立即平移 Span 位置）
afterTextChanged → _hlDebounced.run()（重置防抖计时器）
   …停顿超过延迟…
      → 后台线程 compute()
      → 回主线程：文本未变则 batch 内清旧 Span、setComputed、重挂静态/动态 Span
```

### 10.3 滚动

```
OnScrollChangedListener → updateHighlighting()
  → 滚动量超阈值（约 8 行）
  → 不 batch：clearDynamic() + applyDynamic(hlRegion())
```

### 10.4 搜索

```
TextSearchHandler 正则找全部匹配
  → editor.setSearchMatches(spanGroups)
  → _hl.addAdditional() 叠加背景色（每次重算后由编辑器自动重新注入）
```

---

## 11. 如何为新格式增加语法高亮

1. 在 `format/<newformat>/` 下新建 `XxxSyntaxHighlighter extends SyntaxHighlighterBase`；
2. 用预编译 `Pattern` 定义该格式的语法元素；
3. 实现 `configure(Paint)`（读取设置、调用 `super.configure()`、按需设置 `_delay`）；
4. 实现 `generateSpans()`：优先复用 `createTabSpans` / `createUnderlineHexColorsSpans` / `createSmallBlueLinkSpans`，其余元素调用相应 `createXxxSpanForMatches()`；
5. 仅在确实需要改变排版（如放大标题）时使用 `MetricAffectingSpan` 或实现 `StaticSpan`，并意识到它会被当作静态 Span；
6. 在 `FormatRegistry.getFormat()` 的 switch 与 `FORMATS` 中登记新格式，使 Fragment 能通过格式 ID 拿到该高亮器。

若是通用编程语言，优先走 §8 的 JSON 配置方式，无需新建 Java 类。
