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
                        metadata.getAllClasses().forEach { cls ->
                            result.addElement(LookupElementBuilder.create(cls.alias).withTypeText(cls.fullName, true))
                        }
                    }
                    XmlContextType.AttributeName -> {
                        if (xmlContext.currentTag == "Body") {
                            result.addElement(LookupElementBuilder.create("sui:Class").withTypeText("指定 UIElementGroup 子类全名", true))
                            result.addElement(LookupElementBuilder.create("Class").withTypeText("旧版兼容写法", true))
                            resolveBodyClassForOffset(text, parameters.offset, metadata)?.properties?.forEach { prop ->
                                result.addElement(LookupElementBuilder.create(prop.name).withTypeText(prop.typeName, true))
                            }
                        } else {
                            metadata.getClassByName(xmlContext.currentTag)?.properties?.forEach { prop ->
                                result.addElement(LookupElementBuilder.create(prop.name).withTypeText(prop.typeName, true))
                            }
                        }
                    }
                    XmlContextType.AttributeValue -> {
                        if (xmlContext.currentTag == "Body" && SilkyUiXmlUtil.isBodyClassAttribute(xmlContext.currentAttribute)) {
                            metadata.getAllGroupClasses().forEach { cls ->
                                result.addElement(LookupElementBuilder.create(cls.fullName).withLookupString(cls.name).withTypeText(cls.fullName, true))
                            }
                        } else {
                            val prop = if (xmlContext.currentTag == "Body") {
                                resolveBodyClassForOffset(text, parameters.offset, metadata)?.properties?.firstOrNull { it.name == xmlContext.currentAttribute }
                            } else {
                                metadata.getPropertyByName(xmlContext.currentTag, xmlContext.currentAttribute)
                            }
                            prop?.enumValues?.forEach { value -> result.addElement(LookupElementBuilder.create(value).withTypeText("enum", true)) }
                        }
                    }
                    XmlContextType.Unknown -> Unit
                }
            }
        })
    }

    private fun resolveBodyClassForOffset(text: String, offset: Int, metadata: SilkyUiMetadataService): SilkyUiElementGroupClass? {
        val tagStart = text.lastIndexOf("<Body", offset.coerceAtMost(text.length), ignoreCase = true)
        if (tagStart < 0) return null
        val tagEnd = SilkyUiXmlUtil.findTagEnd(text, tagStart + 1) ?: offset
        return SilkyUiXmlUtil.resolveBodyClass(text, tagStart, tagEnd, metadata)
    }
}
