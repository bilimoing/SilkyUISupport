package com.moling.silky

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

class SilkyUiAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val file = element.containingFile ?: return
        if (element != file || !SilkyUiXmlUtil.isSilkyUiXml(file)) return
        val text = file.text
        val metadata = SilkyUiMetadataService.getInstance(file.project)
        var pos = 0
        while (pos < text.length) {
            val tagStart = text.indexOf('<', pos)
            if (tagStart < 0 || tagStart + 1 >= text.length) break
            val next = text[tagStart + 1]
            if (next == '?' || next == '!') {
                val terminator = if (next == '?') "?>" else "-->"
                val end = text.indexOf(terminator, tagStart + 2)
                if (end < 0) break
                pos = end + terminator.length
                continue
            }
            if (next == '/') {
                pos = (text.indexOf('>', tagStart + 2).takeIf { it >= 0 } ?: break) + 1
                continue
            }
            val tagEnd = SilkyUiXmlUtil.findTagEnd(text, tagStart + 1) ?: break
            var innerEnd = tagEnd
            while (innerEnd > tagStart + 1 && text[innerEnd - 1].isWhitespace()) innerEnd--
            if (innerEnd > tagStart + 1 && text[innerEnd - 1] == '/') innerEnd--
            val tagNameStart = tagStart + 1
            val tagNameEnd = SilkyUiXmlUtil.readNameEnd(text, tagNameStart, innerEnd)
            val tagName = text.substring(tagNameStart, tagNameEnd)
            val mappedClass = if (tagName == "Body") null else metadata.getClassByName(tagName)
            val bodyClass = if (tagName == "Body") SilkyUiXmlUtil.resolveBodyClass(text, tagStart, tagEnd, metadata) else null

            if (tagName != "Body" && !SilkyUiXmlUtil.isSpecialElement(tagName) && mappedClass == null) {
                error(holder, tagNameStart, tagNameEnd, "未知元素 '$tagName'")
            }

            validateAttributes(text, tagNameEnd, innerEnd, holder, tagName, mappedClass, bodyClass, metadata)
            pos = tagEnd + 1
        }
    }

    private fun validateAttributes(
        text: String,
        start: Int,
        end: Int,
        holder: AnnotationHolder,
        tagName: String,
        mappedClass: XmlMappingClass?,
        bodyClass: SilkyUiElementGroupClass?,
        metadata: SilkyUiMetadataService
    ) {
        val seen = hashSetOf<String>()
        var i = start
        while (i < end) {
            while (i < end && text[i].isWhitespace()) i++
            if (i >= end) break
            val nameStart = i
            while (i < end && (text[i].isLetterOrDigit() || text[i] == '.' || text[i] == '_' || text[i] == '-')) i++
            if (nameStart == i) {
                i++
                continue
            }
            val attrName = text.substring(nameStart, i)
            while (i < end && text[i].isWhitespace()) i++
            var valueStart = -1
            var valueEnd = -1
            var value = ""
            if (i < end && text[i] == '=') {
                i++
                while (i < end && text[i].isWhitespace()) i++
                if (i < end && (text[i] == '"' || text[i] == '\'')) {
                    val quote = text[i++]
                    valueStart = i
                    while (i < end && text[i] != quote) i++
                    valueEnd = i
                    value = text.substring(valueStart, valueEnd)
                    if (i < end) i++
                }
            }

            if (tagName == "Body" && attrName == "Class" && value.isNotEmpty()) {
                if (metadata.getAllGroupClasses().none { it.fullName == value || it.name == value }) {
                    error(holder, valueStart, valueEnd, "未知类 '$value'")
                }
                continue
            }

            if (SilkyUiXmlUtil.isSpecialAttribute(attrName)) continue
            if (!seen.add(attrName)) {
                error(holder, nameStart, nameStart + attrName.length, "重复属性 '$attrName'")
                continue
            }

            val prop = if (tagName == "Body") bodyClass?.properties?.firstOrNull { it.name == attrName } else metadata.getPropertyByName(tagName, attrName)
            if ((tagName == "Body" && bodyClass != null || mappedClass != null) && prop == null) {
                error(holder, nameStart, nameStart + attrName.length, "'$tagName' 上没有 '$attrName' 属性")
            }
            if (prop != null && value.isNotEmpty() && prop.enumValues.isNotEmpty() && value !in prop.enumValues) {
                error(holder, valueStart, valueEnd, "'$attrName' 可选值: ${prop.enumValues.joinToString()}")
            }
        }
    }

    private fun error(holder: AnnotationHolder, start: Int, end: Int, message: String) {
        if (start >= 0 && end > start) {
            holder.newAnnotation(HighlightSeverity.ERROR, message).range(TextRange(start, end)).create()
        }
    }
}
