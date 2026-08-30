package com.moling.silky

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

object SilkyUiXmlUtil {
    private val xmlNameChars = setOf('.', '_', '-', ':')

    fun isSilkyUiXml(file: PsiFile?): Boolean = file?.name?.endsWith(".sui.xml", ignoreCase = true) == true

    fun isXmlNameChar(c: Char): Boolean = c.isLetterOrDigit() || c in xmlNameChars

    fun analyze(text: String, offset: Int): XmlContext {
        if (offset <= 0 || offset > text.length) return XmlContext()

        val scanEnd = offset.coerceAtMost(text.length)
        var tagStart = -1
        var i = scanEnd - 1
        var scannedLines = 0
        while (i >= 0) {
            val c = text[i]
            if (c == '\n') scannedLines++
            if (scannedLines > 10) break
            if (c == '>') return XmlContext()
            if (c == '<') {
                tagStart = i
                break
            }
            i--
        }
        if (tagStart < 0 || tagStart + 1 < text.length && text[tagStart + 1] == '/') return XmlContext()

        val tagContent = text.substring(tagStart, scanEnd).replace('\r', ' ').replace('\n', ' ')
        val quoteCount = tagContent.count { it == '"' }
        if (quoteCount % 2 == 1) {
            val currentTag = extractTagName(tagContent)
            val eqPos = tagContent.lastIndexOf('=')
            val attr = if (eqPos > 0) {
                var attrStart = eqPos - 1
                while (attrStart >= 0 && (tagContent[attrStart].isLetterOrDigit() || tagContent[attrStart] == '.' || tagContent[attrStart] == '_' || tagContent[attrStart] == '-')) attrStart--
                tagContent.substring(attrStart + 1, eqPos).trim()
            } else ""
            return XmlContext(XmlContextType.AttributeValue, currentTag, attr)
        }

        if (tagContent.contains(' ')) {
            val afterSpace = tagContent.substringAfterLast(' ')
            if (afterSpace.isNotEmpty() && (!afterSpace.contains('=') || afterSpace.all { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' })) {
                return XmlContext(XmlContextType.AttributeName, extractTagName(tagContent), afterSpace.trim())
            }
        }

        return XmlContext(XmlContextType.TagName, extractTagName(tagContent), "")
    }

    fun resolveSymbol(text: String, offset: Int, metadata: SilkyUiMetadataService): SilkyUiSymbolResolution? {
        if (text.isEmpty() || offset < 0 || offset >= text.length || !isXmlNameChar(text[offset])) return null
        var start = offset
        while (start > 0 && isXmlNameChar(text[start - 1])) start--
        var end = offset + 1
        while (end < text.length && isXmlNameChar(text[end])) end++
        val name = text.substring(start, end)
        if (name.isBlank()) return null
        val range = TextRange(start, end)

        val isStartTagName = start > 0 && text[start - 1] == '<'
        val isEndTagName = start > 1 && text[start - 2] == '<' && text[start - 1] == '/'
        if (isStartTagName || isEndTagName) {
            val cls = metadata.getClassByName(name) ?: return null
            return SilkyUiSymbolResolution(SilkyUiSymbolKind.Element, range, name, name, mappingClass = cls)
        }

        val tagStart = findTagStart(text, start) ?: return null
        if (tagStart + 1 < text.length && text[tagStart + 1] == '/') return null
        if (isTagNameToken(text, tagStart, start)) return null

        val nextNonWhitespace = skipWhitespaceForward(text, end)
        if (nextNonWhitespace < text.length && text[nextNonWhitespace] == '=') {
            val currentTag = getTagName(text, tagStart)
            if (currentTag.isBlank()) return null
            val prop = if (currentTag == "Body") {
                resolveBodyClass(text, tagStart, findTagEnd(text, tagStart + 1) ?: text.length, metadata)?.properties?.firstOrNull { it.name == name }
            } else {
                metadata.getPropertyByName(currentTag, name)
            }
            if (prop != null) {
                return SilkyUiSymbolResolution(SilkyUiSymbolKind.Attribute, range, name, currentTag, metadata.getClassByName(currentTag), prop)
            }
        }

        val context = analyze(text, offset + 1)
        if (context.type == XmlContextType.AttributeValue && context.currentTag == "Body" && context.currentAttribute == "Class") {
            val bodyClass = metadata.getAllGroupClasses().firstOrNull { it.fullName == name || it.name == name }
            if (bodyClass != null) return SilkyUiSymbolResolution(SilkyUiSymbolKind.BodyClass, range, name, "Body", bodyClass = bodyClass)
        }

        return null
    }

    fun resolveBodyClass(text: String, tagStart: Int, tagEnd: Int, metadata: SilkyUiMetadataService): SilkyUiElementGroupClass? {
        val end = tagEnd.coerceAtMost(text.length)
        if (tagStart < 0 || tagStart >= end) return null
        val section = text.substring(tagStart, end)
        val classIndex = section.indexOf("Class=", ignoreCase = true)
        if (classIndex < 0) return null
        var valueStart = classIndex + "Class=".length
        if (valueStart >= section.length || section[valueStart] != '"' && section[valueStart] != '\'') return null
        val quote = section[valueStart++]
        val valueEnd = section.indexOf(quote, valueStart)
        if (valueEnd <= valueStart) return null
        val className = section.substring(valueStart, valueEnd)
        return metadata.getAllGroupClasses().firstOrNull { it.fullName == className || it.name == className }
    }

    fun findTagEnd(text: String, start: Int): Int? {
        var quote = '\u0000'
        for (i in start until text.length) {
            val c = text[i]
            if (quote != '\u0000') {
                if (c == quote) quote = '\u0000'
                continue
            }
            if (c == '"' || c == '\'') quote = c else if (c == '>') return i
        }
        return null
    }

    fun readNameEnd(text: String, start: Int, end: Int): Int {
        var current = start
        while (current < end && !text[current].isWhitespace() && text[current] != '/') current++
        return current
    }

    fun isSpecialElement(name: String): Boolean = name == "Style" || name.startsWith("M.") || name.startsWith("Style.")

    fun isSpecialAttribute(name: String): Boolean = name == "Name" || name == "Class" || name == "Style" || name.startsWith("Bind.")

    private fun extractTagName(tagContent: String): String {
        var end = tagContent.indexOfAny(charArrayOf(' ', '/', '>'))
        if (end == -1) end = tagContent.length
        return if (end > 1) tagContent.substring(1, end).trim() else ""
    }

    private fun findTagStart(text: String, position: Int): Int? {
        var quote = '\u0000'
        for (i in position - 1 downTo 0) {
            val c = text[i]
            if (quote != '\u0000') {
                if (c == quote) quote = '\u0000'
                continue
            }
            if (c == '"' || c == '\'') quote = c else if (c == '>') return null else if (c == '<') return i
        }
        return null
    }

    private fun isTagNameToken(text: String, tagStart: Int, tokenStart: Int): Boolean {
        var nameStart = tagStart + 1
        if (nameStart < text.length && text[nameStart] == '/') nameStart++
        return nameStart == tokenStart
    }

    private fun skipWhitespaceForward(text: String, position: Int): Int {
        var current = position
        while (current < text.length && text[current].isWhitespace()) current++
        return current
    }

    private fun getTagName(text: String, tagStart: Int): String {
        var current = tagStart + 1
        if (current < text.length && text[current] == '/') return ""
        while (current < text.length && text[current].isWhitespace()) current++
        val start = current
        while (current < text.length && isXmlNameChar(text[current])) current++
        return if (current > start) text.substring(start, current) else ""
    }
}
