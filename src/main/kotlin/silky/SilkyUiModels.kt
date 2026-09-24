package com.moling.silky

import com.intellij.openapi.util.TextRange

enum class SilkyUiSemanticKind {
    Element,
    Attribute,
    Binding,
    Style,
    StyleTarget,
    Namespace,
    Type,
    Property,
    PropertyObject,
    Unknown
}

data class SilkyUiProperty(
    val name: String,
    val typeName: String,
    val enumValues: List<String> = emptyList(),
    val sourceFilePath: String = "",
    val sourceOffset: Int = 0,
    val declaringClass: String = "",
    val canRead: Boolean = true,
    val canWrite: Boolean = true,
    val isStatic: Boolean = false,
    val semanticKind: SilkyUiSemanticKind = SilkyUiSemanticKind.Property,
    val nestedTypeName: String? = null
)

data class SilkyUiStyleMetadata(
    val name: String,
    val targetTypeName: String? = null,
    val sourceFilePath: String = "",
    val sourceOffset: Int = 0
)

data class SilkyUiClrType(
    val name: String,
    val fullName: String,
    val namespace: String,
    val baseType: String? = null,
    val properties: List<SilkyUiProperty> = emptyList(),
    val styles: List<SilkyUiStyleMetadata> = emptyList(),
    val sourceFilePath: String = "",
    val sourceOffset: Int = 0
)

data class SilkyUiMetadataSnapshot(
    val types: List<SilkyUiClrType> = emptyList(),
    val mappingClasses: List<XmlMappingClass> = emptyList(),
    val groupClasses: List<SilkyUiElementGroupClass> = emptyList(),
    val styles: List<SilkyUiStyleMetadata> = emptyList()
)

data class XmlMappingClass(
    val name: String,
    val fullName: String,
    val alias: String,
    val properties: List<SilkyUiProperty>,
    val sourceFilePath: String,
    val sourceOffset: Int,
    val namespace: String = fullName.substringBeforeLast('.', ""),
    val semanticKind: SilkyUiSemanticKind = SilkyUiSemanticKind.Element
)

data class SilkyUiElementGroupClass(
    val name: String,
    val fullName: String,
    val properties: List<SilkyUiProperty>,
    val sourceFilePath: String,
    val sourceOffset: Int,
    val namespace: String = fullName.substringBeforeLast('.', ""),
    val semanticKind: SilkyUiSemanticKind = SilkyUiSemanticKind.Element
)

enum class XmlContextType {
    Unknown,
    TagName,
    AttributeName,
    AttributeValue,
    PropertyObject
}

data class XmlContext(
    val type: XmlContextType = XmlContextType.Unknown,
    val currentTag: String = "",
    val currentAttribute: String = "",
    val namespaceUri: String? = null,
    val attributePrefix: String? = null
)

enum class SilkyUiSymbolKind {
    Element,
    Attribute,
    BodyClass,
    Namespace,
    Style,
    PropertyObject
}

data class SilkyUiSymbolResolution(
    val kind: SilkyUiSymbolKind,
    val range: TextRange,
    val symbolName: String,
    val currentTag: String,
    val mappingClass: XmlMappingClass? = null,
    val property: SilkyUiProperty? = null,
    val bodyClass: SilkyUiElementGroupClass? = null,
    val style: SilkyUiStyleMetadata? = null,
    val semanticKind: SilkyUiSemanticKind = SilkyUiSemanticKind.Unknown
)
