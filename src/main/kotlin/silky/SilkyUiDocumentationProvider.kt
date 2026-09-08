package com.moling.silky

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement

class SilkyUiDocumentationProvider : AbstractDocumentationProvider() {
    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val target = originalElement ?: element ?: return null
        val file = target.containingFile ?: return null
        if (!SilkyUiXmlUtil.isSilkyUiXml(file)) return null
        val resolution = SilkyUiXmlUtil.resolveSymbol(file.text, target.textOffset.coerceAtLeast(0), SilkyUiMetadataService.getInstance(file.project)) ?: return null
        return when (resolution.kind) {
            SilkyUiSymbolKind.Element -> buildElementDoc(resolution.mappingClass ?: return null)
            SilkyUiSymbolKind.Attribute -> buildAttributeDoc(resolution.currentTag, resolution.property ?: return null)
            SilkyUiSymbolKind.BodyClass -> buildBodyClassDoc(resolution.bodyClass ?: return null)
        }
    }

    private fun buildElementDoc(cls: XmlMappingClass): String = buildString {
        append("<b>class ${escape(cls.alias)}</b><br/>")
        append(escape(cls.fullName)).append("<br/>")
        append("属性数: ${cls.properties.size}<br/>")
        if (cls.properties.isNotEmpty()) append("可用属性: ${escape(cls.properties.take(8).joinToString { it.name })}${if (cls.properties.size > 8) ", ..." else ""}")
    }

    private fun buildAttributeDoc(tag: String, prop: SilkyUiProperty): String = buildString {
        append("<b>${escape(prop.typeName)} ${escape(tag)}.${escape(prop.name)}</b><br/>")
        append("声明类型: ${escape(prop.declaringClass)}<br/>")
        append("属性类型: ${escape(prop.typeName)}")
        if (prop.enumValues.isNotEmpty()) append("<br/>枚举值: ${escape(prop.enumValues.take(10).joinToString())}${if (prop.enumValues.size > 10) ", ..." else ""}")
    }

    private fun buildBodyClassDoc(cls: SilkyUiElementGroupClass): String = buildString {
        append("<b>class ${escape(cls.name)}</b><br/>")
        append(escape(cls.fullName)).append("<br/>")
        append("sui:Class / UIElementGroup")
    }

    private fun escape(value: String): String = value
        .replace("&", "&")
        .replace("<", "<")
        .replace(">", ">")
}
