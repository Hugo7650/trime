# Trime 内置可视化主题编辑器方案

## Summary
在 Trime 设置页内新增“主题工作台”，用于可视化生成、编辑和部署 `*.trime.yaml`。首版做成内置 Android 功能，不做独立网页；保存时从当前主题派生用户目录副本，不直接修改内置主题或原主题。

核心原则：编辑器以 Trime 部署后的真实主题模型为准，预览复用或抽取现有键盘布局计算逻辑，保证编辑结果和实际键盘尽量一致。界面采用逐层进入的资源编辑器结构，一个层级对应一个页面，不在主题工作台或单个模块页面中展开全部配置。复杂 YAML 语法、注释、锚点、`__include`、`__patch` 不作为可视化主模型保留，原文件只读保留，派生主题输出为规范化完整 YAML。

## Key Changes
- 新增入口：在 `ThemeSettingsFragment` 增加“主题工作台”入口，并在 `NavigationRoute` 注册新页面。
- 新增主题编辑模块：
  - `ThemeDesignerFragment`：工作台首页，只显示主题状态、保存/部署操作和顶层模块入口。
  - 独立列表与详情 Fragment：键盘、按键、预设按键、液态键盘等资源按层级进入，不在同一页面全部展开。
  - `ThemeDesignerViewModel`：在各层级页面间共享，加载当前主题、维护草稿、校验、保存、部署和局部列表状态。
  - `ThemeDraftRepository`：负责从当前主题派生用户主题、读写草稿、备份、回滚。
- 保存策略：
  - 首次编辑当前主题时，复制部署后的完整配置为用户目录新文件，例如 `当前主题名.designer.trime.yaml`。
  - 后续编辑只修改这个派生文件。
  - 保存前生成 `.bak`，部署失败自动回滚到上一个有效版本。
- 配置覆盖范围：
  - 可视化覆盖 `Theme` 当前模型中的全部模块：`style`、`preedit`、`window`、`tool_bar`、`fallback_colors`、`preset_color_schemes`、`liquid_keyboard`、`preset_keys`、`preset_keyboards`。
  - 同时参考 `doc/trime-schema.json` 补齐字段说明、默认值、枚举、范围。
  - 未被当前模型识别的 YAML 字段进入“高级/源码”页，可查看和手动编辑，但不参与普通表单生成。
- 键盘布局编辑：
  - 键盘与预设按键使用两个独立入口和列表页面。
  - 支持键盘列表搜索、排序、复制/新建/删除布局、编辑 `style/keyboards` 声明。
  - 支持 `preset_keyboards/*` 的基础属性、行列宽高、横屏键盘、分割比例、偏移、圆角、边框。
  - 单个键盘进入独立详情页，其按键列表再进入下一层；按键列表支持拖拽排序、插入空白键、复制键、删除键、批量改宽高。
  - 点击单个按键后进入独立设置页，不在按键列表中展开字段。
  - 支持每个键的 `click`、`long_click`、`swipe_*`、`ascii`、`composing`、`has_menu`、`paging`、`combo`、`double_click`、`popup`、颜色、字号、偏移等字段。
- 液态键盘编辑：
  - 液态键盘列表、单个液态键盘、按键列表和单个按键设置分别使用独立页面。
  - 液态键盘列表支持搜索、新建、复制、删除和排序。
  - 单个液态键盘页面编辑基础属性，并提供按键列表和实时预览入口。
  - 液态键盘按键列表支持拖拽、批量操作和插入空白键；具体动作、标签和预设键引用在单键设置页编辑。
- 预览实现：
  - 从 `Keyboard.kt` 抽取纯布局计算为 `KeyboardLayoutCalculator`，IME 和编辑器共同使用。
  - 编辑器预览提供竖屏/横屏、中文/英文、候选中/翻页中/有菜单等状态切换。
  - 预览显示按键文本、长按符号、hint、颜色、圆角、间距、键盘高度和横屏分割效果。
- YAML 输出：
  - 新增 `ThemeYamlWriter`，从草稿模型生成稳定排序、可读的完整 YAML。
  - 输出使用 Trime 当前支持字段名，不生成 `__include`/`__patch`。
  - 保留 `config_version`、`name`、`author`，派生主题默认改名为“原主题名 / 可视化副本”。

## Interfaces / Types
- `ThemeDraft`
  - 包含 `Theme` 可编辑模型、源主题 id、派生主题 id、文件路径、dirty 状态、校验结果。
- `ThemeFieldRegistry`
  - 定义所有可视化字段的标题、说明、类型、默认值、范围、枚举、所属模块。
  - 字段来源优先级：当前 Kotlin 模型 > `doc/trime-schema.json` > wiki 文档说明。
- `ThemeDraftRepository`
  - `loadActiveThemeDraft()`
  - `createDerivedTheme(baseThemeId)`
  - `saveDraft(draft)`
  - `deployDraft(draft)`
  - `restoreBackup(themeId)`
- `KeyboardLayoutCalculator`
  - 输入：`GeneralStyle`、`TextKeyboard`、屏幕宽度、方向、横屏分割设置。
  - 输出：每个可点击键的 x/y/width/height/row/column 和整体键盘尺寸。
- `ThemeValidationResult`
  - 区分 error、warning、info。
  - error 阻止部署，warning 允许保存但提示风险。

## UI Structure
- 导航原则：
  - 一个层级对应一个 Fragment 页面，通过 Android 返回键严格返回上一级。
  - 页面标题展示当前位置和资源名称，不使用星号表示选中模块。
  - 顶层页面只负责模块入口；列表页面只负责浏览和批量操作；详情页面只负责当前资源。
  - 列表统一使用 `RecyclerView`，字段详情沿用 Preference/Splitties DSL；实时预览只在单个键盘详情中创建。
  - 导航参数只传主题 id、资源 id 和编辑器内部稳定 id，不在 Fragment 间复制完整主题模型。
- 顶层工作台：
  - 概览：主题名、作者、当前文件、保存/部署状态。
  - 键盘布局：进入键盘列表和预设按键两个子模块。
  - 外观尺寸：`style` 中字体、字号、高度、间距、圆角、padding。
  - 配色：`preset_color_schemes`、`fallback_colors`、按键颜色。
  - 候选与预编辑：`preedit`、`window`、候选栏相关 `style` 字段。
  - 工具栏：`tool_bar`。
  - 液态键盘：`liquid_keyboard`。
  - 高级源码：规范化 YAML 预览、未知字段、手动编辑。
- 键盘布局层级：
  - 键盘列表 -> 单个键盘 -> 基础设置、按键列表、布局设置、实时预览。
  - 按键列表 -> 单个按键设置。
  - 预设按键列表 -> 单个预设按键设置。
- 液态键盘层级：
  - 液态键盘首页 -> 全局设置、液态键盘列表。
  - 液态键盘列表 -> 单个液态键盘 -> 基础设置、按键列表、实时预览。
  - 按键列表 -> 单个按键设置。
- 页面类型：
  - `KeyboardListFragment`：键盘搜索、排序、新建、复制和删除。
  - `KeyboardEditorFragment`：单个键盘基础属性及下级入口。
  - `KeyboardKeyListFragment`：单个键盘的按键排序和批量操作。
  - `KeyboardKeyEditorFragment`：单个普通按键的全部字段。
  - `PresetKeyListFragment` / `PresetKeyEditorFragment`：预设按键列表与单项设置。
  - `LiquidKeyboardListFragment` / `LiquidKeyboardEditorFragment`：液态键盘列表与单项设置。
  - `LiquidKeyboardKeyListFragment` / `LiquidKeyboardKeyEditorFragment`：液态键盘按键列表与单项设置。
- 控件规则：
  - 数字字段用输入框/滑杆。
  - 布尔字段用开关。
  - 枚举字段用单选或下拉。
  - 颜色字段用现有 `ColorPickerDialog`。
  - 字体字段从 `DataManager.userDataDir/fonts` 和主题字段中选择，也允许手输。
  - 键动作字段提供 `preset_keys` 搜索选择，并允许自由文本。

## Navigation / State
- 所有主题设计页面共享 Activity 范围的 `ThemeDesignerViewModel`，页面切换不重新读取或解析 YAML。
- 建议路由参数：
  - `KeyboardEditor(themeId, keyboardId)`
  - `KeyboardKeyList(themeId, keyboardId)`
  - `KeyboardKeyEditor(themeId, keyboardId, draftKeyId)`
  - `PresetKeyEditor(themeId, presetKeyId)`
  - `LiquidKeyboardEditor(themeId, liquidKeyboardId)`
  - `LiquidKeyboardKeyList(themeId, liquidKeyboardId)`
  - `LiquidKeyboardKeyEditor(themeId, liquidKeyboardId, draftKeyId)`
- 数组下标不能作为按键的长期标识。普通键盘和液态键盘中的按键在草稿模型内增加稳定的 `draftKeyId`，拖拽、插入或删除后保持不变，写出 YAML 时不输出该字段。
- Fragment 只订阅当前页面所需的局部状态；搜索、过滤和排序在 ViewModel 中完成。
- YAML 仅在首次加载、保存、部署和显式源码校验时处理，不因页面导航重新生成或解析。

## Validation
- YAML 语法校验：保存前用现有 `Yaml.parseToYamlNode` 解析生成结果。
- 模型校验：生成后用 `Theme.decode` 再解码一遍。
- Rime 部署校验：调用 `Rime.deployRimeConfigFile(themeId, "config_version")`。
- 主题完整性校验：
  - `style/keyboards` 引用必须存在于 `preset_keyboards`，允许 `.default`、`.next`、`.last` 等特殊项。
  - 键盘切换键 `select` 指向不存在布局时给 warning。
  - `keys` 为空、整行宽度明显异常、键盘高度为 0、颜色引用不存在时提示。
  - 删除正在被引用的 `preset_key` 或键盘布局前二次确认。

## Test Plan
- 单元测试：
  - `ThemeYamlWriter` 能输出可被 `Yaml.parseToYamlNode` 和 `Theme.decode` 读取的 YAML。
  - `KeyboardLayoutCalculator` 对默认 `trime.yaml` 的 qwerty、number、symbols 输出与原 `Keyboard.kt` 行列/尺寸一致。
  - 派生主题命名冲突时自动生成唯一文件名。
  - 删除/重命名键盘布局时引用校验能给出正确 warning/error。
- 集成测试：
  - 从默认主题派生、修改一个键宽、保存、部署、重新加载后变更仍存在。
  - 修改颜色方案后 `ThemeManager.selectTheme` 可正常切换。
  - 部署失败时恢复 `.bak`，当前主题不损坏。
- UI 验收：
  - 竖屏和横屏预览无重叠。
  - 拖拽按键、编辑动作、修改宽高、切换预览状态后界面立即更新。
  - 所有当前模型字段都能在对应模块找到；未知字段可在高级源码页看到。
  - 含 20 个键盘和 100 个以上预设按键的主题进入工作台时不创建全部详情控件，模块和列表页面可快速打开并滚动流畅。
  - 键盘、普通按键、预设按键、液态键盘及液态键盘按键均可逐层进入和返回，列表页不混入单项字段设置。

## Assumptions
- 首版不新增 Jetpack Compose，列表页面沿用 Fragment/RecyclerView，单项字段页面沿用 Preference/Splitties DSL 风格。
- 首版不承诺保留原 YAML 注释、锚点、`__include`、`__patch` 的源码结构；通过派生完整 YAML 规避破坏原文件。
- 独立网页暂不开发；后续如需要，可复用 `ThemeFieldRegistry`、YAML writer 和布局计算规则做轻量生成器。
- “所有字段全可视化”定义为当前 Trime 代码模型和 `doc/trime-schema.json` 已知字段全部有表单入口；未知扩展字段通过高级源码模式处理。
