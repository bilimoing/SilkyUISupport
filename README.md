# SilkyUI Support for Rider

为 Rider / IntelliJ 平台提供 SilkyUI `.sui.xml` 文件的开发支持。

## 功能

- XML 元素、属性和枚举值智能补全
- 根据当前 C# 解决方案动态读取 SilkyUI 类型和属性
- 为 `Body` 的 `sui:Class` 属性补全 `UIElementGroup` 子类
- 鼠标悬停显示 C# 类型、属性和枚举值信息
- 从 XML 元素或属性跳转到对应的 C# 定义
- 检查未知元素、未知属性、重复属性和非法枚举值
- 检查无效的 Body 类名
- 在 C# 编辑器右键菜单中创建 `.sui.xml` 初始模板
- 自动生成与 C# 类同名的 XML 文件

## 使用方法

1. 使用 Rider 打开包含 SilkyUI 类型的 C# 解决方案。
2. 打开或创建 `.sui.xml` 文件，使用 XML 智能补全。
3. 在 C# 类上右键，选择“创建 SilkyUI XML 初始模板”。

## 要求

- Rider 2025.3 或更高版本
- 64 位 Rider

## 说明

感谢原 Visual Studio 版本插件仓库：<https://github.com/487666123/SilkyUISupport>

Rider 的 C# 语义分析运行在后端进程中，本插件改为通过扫描项目内 C# 源码实现元数据读取，因此不依赖 Visual Studio SDK 或 Roslyn 工作区服务。`.sui.xml` 文件仍由 IntelliJ XML 编辑器解析，插件扩展点只对该文件名后缀生效。
