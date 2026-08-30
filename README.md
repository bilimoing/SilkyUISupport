# SilkyUI Support for Rider

Rider 插件版 SilkyUI 支持，面向 `.sui.xml` 文件提供与 Visual Studio 扩展对应的编辑能力：

- 根据项目 C# 源码中的 `XmlElementMappingAttribute` 动态提供元素和属性补全
- 为 `Body Class` 提供 `UIElementGroup` 子类及其属性补全
- 提供枚举属性值补全
- 检查未知元素、未知属性、重复属性、非法枚举值和无效的 `Body Class`
- 鼠标悬停显示元素、属性和枚举元数据
- 从 XML 元素、属性和 `Body Class` 跳转到 C# 源码位置
- 在 C# 文件编辑器或项目视图菜单中创建同名 `.sui.xml` 初始模板

## 构建

使用 JDK 21 或更高版本执行：

```text
gradlew.bat buildPlugin
```

生成的插件包位于 `build/distributions/`。

## 说明

Rider 的 C# 语义分析运行在后端进程中，本插件使用项目内 C# 源码扫描实现跨平台元数据读取，因此不依赖 Visual Studio SDK 或 Roslyn 工作区服务。`.sui.xml` 文件仍由 IntelliJ XML 编辑器解析，插件扩展点只对该文件名后缀生效。
