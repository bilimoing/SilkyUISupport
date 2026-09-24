package com.moling.silky

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.util.ProcessingContext

class SilkyUiCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, psiElement(), object : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
                val file = parameters.originalFile
                if (!SilkyUiXmlUtil.isSilkyUiXml(file)) return
                val metadata = SilkyUiMetadataService.getInstance(file.project)
                val text = file.text
                val xmlContext = SilkyUiXmlUtil.analyze(text, parameters.offset)
                when (xmlContext.type) {
                    XmlContextType.TagName -> {
                        result.addElement(LookupElementBuilder.create("Body").withTypeText("根元素", true))
                        metadata.getAllClasses().forEach { cls -> result.addElement(LookupElementBuilder.create(cls.alias).withTypeText(cls.fullName, true)) }
                        val prefix = text.substringBeforeLast('<', "").takeIf { it.contains("clr-namespace:") }
                        prefix?.let { declaration ->
                            val namespace = Regex("clr-namespace:([A-Za-z_][\\w.]*)").find(declaration)?.groupValues?.get(1)
                            metadata.findClrTypes(namespace.orEmpty()).forEach { type -> result.addElement(LookupElementBuilder.create(type.name).withTypeText(type.fullName, true)) }
                        }
                    }
                    XmlContextType.AttributeName -> {
                        result.addElement(LookupElementBuilder.create("bind:").withTypeText("数据绑定", true))
                        result.addElement(LookupElementBuilder.create("prop:").withTypeText("成员对象", true))
                        if (xmlContext.currentTag == "Body") {
                            result.addElement(LookupElementBuilder.create("sui:Class").withTypeText("UIElementGroup 类型", true))
                            result.addElement(LookupElementBuilder.create("Class").withTypeText("旧版兼容写法", true))
                            resolveBodyClassForOffset(text, parameters.offset, metadata)?.properties?.forEach { addProperty(result, it) }
                        } else metadata.getClassByName(xmlContext.currentTag)?.properties?.forEach { addProperty(result, it) }
                        if (xmlContext.currentTag == "Style" || xmlContext.currentTag.startsWith("Style.")) {
                            result.addElement(LookupElementBuilder.create("Target").withTypeText("Style 目标类型", true))
                            result.addElement(LookupElementBuilder.create("Name").withTypeText("Style 名称", true))
                        }
                    }
                    XmlContextType.AttributeValue -> {
                        if (xmlContext.currentTag == "Body" && SilkyUiXmlUtil.isBodyClassAttribute(xmlContext.currentAttribute)) {
                            metadata.getAllGroupClasses().forEach { cls -> result.addElement(LookupElementBuilder.create(cls.fullName).withLookupString(cls.name).withTypeText(cls.fullName, true)) }
                        } else {
                            val propertyName = xmlContext.currentAttribute.substringAfter(':').substringAfterLast('.')
                            val prop = if (xmlContext.currentTag == "Body") resolveBodyClassForOffset(text, parameters.offset, metadata)?.properties?.firstOrNull { it.name == propertyName } else metadata.getPropertyByName(xmlContext.currentTag, propertyName)
                            prop?.enumValues?.forEach { value -> result.addElement(LookupElementBuilder.create(value).withTypeText("enum", true)) }
                            if (xmlContext.currentAttribute == "Style" || xmlContext.currentAttribute == "Target") metadata.getAllStyles().forEach { style -> result.addElement(LookupElementBuilder.create(style.name).withTypeText(style.targetTypeName ?: "Style", true)) }
                        }
                    }
                    else -> Unit
                }
            }
        })
    }

    private fun addProperty(result: CompletionResultSet, property: SilkyUiProperty) {
        val prefix = if (property.nestedTypeName != null) "prop:" else ""
        result.addElement(LookupElementBuilder.create(prefix + property.name).withTypeText(property.typeName, true))
    }

    private fun resolveBodyClassForOffset(text: String, offset: Int, metadata: SilkyUiMetadataService): SilkyUiElementGroupClass? {
        val tagStart = text.lastIndexOf("<Body", offset.coerceAtMost(text.length), ignoreCase = true)
        if (tagStart < 0) return null
        return SilkyUiXmlUtil.resolveBodyClass(text, tagStart, SilkyUiXmlUtil.findTagEnd(text, tagStart + 1) ?: offset, metadata)
    }
}
