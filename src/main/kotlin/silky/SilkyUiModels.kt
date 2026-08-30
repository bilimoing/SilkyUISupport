package com.moling.silky

import com.intellij.openapi.util.TextRange

data class SilkyUiProperty(
    val name: String,
    val typeName: String,
    val enumValues: List<String>,
    val sourceFilePath: String,
    val sourceOffset: Int,
    val declaringClass: String
)

data class XmlMappingClass(
    val name: String,
    val fullName: String,
    val alias: String,
    val properties: List<SilkyUiProperty>,
    val sourceFilePath: String,
    val sourceOffset: Int
)

data class SilkyUiElementGroupClass(
    val name: String,
    val fullName: String,
    val properties: List<SilkyUiProperty>,
    val sourceFilePath: String,
    val sourceOffset: Int
)

enum class XmlContextType {
    Unknown,
    TagName,
    AttributeName,
    AttributeValue
}

data class XmlContext(
    val type: XmlContextType = XmlContextType.Unknown,
    val currentTag: String = "",
    val currentAttribute: String = ""
)

enum class SilkyUiSymbolKind {
    Element,
    Attribute,
    BodyClass
}

data class SilkyUiSymbolResolution(
    val kind: SilkyUiSymbolKind,
    val range: TextRange,
    val symbolName: String,
    val currentTag: String,
    val mappingClass: XmlMappingClass? = null,
    val property: SilkyUiProperty? = null,
    val bodyClass: SilkyUiElementGroupClass? = null
)
