package com.moling.silky

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

object SilkyUiXmlUtil {
    private val xmlNameChars = setOf('.', '_', '-', ':', '*')
    private const val SilkyUri = "http://schemas.silkyui.com/"
    private const val BodyClassAttribute = "sui:Class"
    private const val LegacyBodyClassAttribute = "Class"

    fun isSilkyUiXml(file: PsiFile?): Boolean = file?.name?.endsWith(".sui.xml", ignoreCase = true) == true
    fun isXmlNameChar(c: Char): Boolean = c.isLetterOrDigit() || c in xmlNameChars

    fun analyze(text: String, offset: Int): XmlContext {
        if (offset <= 0 || offset > text.length) return XmlContext()
        val tagStart = findOpenTag(text, offset) ?: return XmlContext()
        val tagContent = text.substring(tagStart, offset).replace('\r', ' ').replace('\n', ' ')
        val tag = extractTagName(tagContent)
        val quoteCount = tagContent.count { it == '"' || it == '\'' }
        if (quoteCount % 2 == 1) return XmlContext(XmlContextType.AttributeValue, tag, extractAttributeNameForValue(tagContent), namespaceUri(text, tagStart))
        val partial = tagContent.substringAfterLast(' ').trim()
        if (partial.isNotEmpty() && !partial.contains('=')) return XmlContext(XmlContextType.AttributeName, tag, partial, namespaceUri(text, tagStart), partial.substringBefore(':').takeIf { ':' in partial })
        return XmlContext(XmlContextType.TagName, tag, "", namespaceUri(text, tagStart))
    }

    fun resolveSymbol(text: String, offset: Int, metadata: SilkyUiMetadataService): SilkyUiSymbolResolution? {
        if (text.isEmpty() || offset !in text.indices || !isXmlNameChar(text[offset])) return null
        var start = offset
        while (start > 0 && isXmlNameChar(text[start - 1])) start--
        var end = offset + 1
        while (end < text.length && isXmlNameChar(text[end])) end++
        val name = text.substring(start, end)
        val range = TextRange(start, end)
        val tagStart = findTagStart(text, start) ?: return null
        val tagName = getTagName(text, tagStart)
        if (start == tagStart + 1 || start == tagStart + 2 && text[tagStart + 1] == '/') {
            val cls = metadata.getClassByName(name)
            return cls?.let { SilkyUiSymbolResolution(SilkyUiSymbolKind.Element, range, name, name, mappingClass = it, semanticKind = SilkyUiSemanticKind.Element) }
        }
        if (name.startsWith("bind:") || name.startsWith("bind")) return SilkyUiSymbolResolution(SilkyUiSymbolKind.Attribute, range, name, tagName, semanticKind = SilkyUiSemanticKind.Binding)
        val next = skipWhitespaceForward(text, end)
        if (next < text.length && text[next] == '=') {
            val propName = name.substringAfter(':', name).substringAfterLast('.')
            val prop = if (tagName == "Body") {
                resolveBodyClass(text, tagStart, findTagEnd(text, tagStart + 1) ?: text.length, metadata)?.properties?.firstOrNull { it.name == propName }
            } else {
                metadata.getPropertyByName(tagName, propName)
                    ?: metadata.getTypeByName(tagName.substringAfter(':', tagName))?.properties?.firstOrNull { it.name == propName }
            }
            if (prop != null) return SilkyUiSymbolResolution(SilkyUiSymbolKind.Attribute, range, name, tagName, metadata.getClassByName(tagName), prop, semanticKind = if (name.startsWith("prop:")) SilkyUiSemanticKind.PropertyObject else SilkyUiSemanticKind.Attribute)
            if (propName == "Style" || propName == "Target") return SilkyUiSymbolResolution(SilkyUiSymbolKind.Style, range, name, tagName, style = metadata.getAllStyles().firstOrNull { it.name == name }, semanticKind = if (propName == "Target") SilkyUiSemanticKind.StyleTarget else SilkyUiSemanticKind.Style)
        }
        val context = analyze(text, end)
        if (context.type == XmlContextType.AttributeValue && tagName == "Body" && isBodyClassAttribute(context.currentAttribute)) {
            metadata.getAllGroupClasses().firstOrNull { it.fullName == name || it.name == name }?.let { return SilkyUiSymbolResolution(SilkyUiSymbolKind.BodyClass, range, name, "Body", bodyClass = it, semanticKind = SilkyUiSemanticKind.Type) }
        }
        return null
    }

    fun resolveBodyClass(text: String, tagStart: Int, tagEnd: Int, metadata: SilkyUiMetadataService): SilkyUiElementGroupClass? {
        val section = text.substring(tagStart, tagEnd.coerceAtMost(text.length))
        val match = Regex("(?:sui:[A-Za-z_][\\w-]*:)?Class\\s*=\\s*[\\\"']([^\\\"']+)", RegexOption.IGNORE_CASE).find(section)
            ?: Regex("(?:[A-Za-z_][\\w-]*:)?Class\\s*=\\s*[\\\"']([^\\\"']+)", RegexOption.IGNORE_CASE).find(section)
        val name = match?.groupValues?.getOrNull(1) ?: return null
        return metadata.getAllGroupClasses().firstOrNull { it.fullName == name || it.name == name }
    }

    fun findTagEnd(text: String, start: Int): Int? {
        var quote = '\u0000'
        for (i in start until text.length) { val c = text[i]; if (quote != '\u0000') { if (c == quote) quote = '\u0000' } else if (c == '"' || c == '\'') quote = c else if (c == '>') return i }
        return null
    }

    fun readNameEnd(text: String, start: Int, end: Int): Int { var i = start; while (i < end && !text[i].isWhitespace() && text[i] != '/') i++; return i }
    fun isSpecialElement(name: String): Boolean = name == "Style" || name.startsWith("M.") || name.startsWith("Style.")
    fun isSpecialAttribute(name: String): Boolean = isBodyClassAttribute(name) || name == "Name" || name == "Style" || name == "Target" || name.startsWith("bind:") || name.startsWith("Bind.") || name.startsWith("prop:")
    fun isBodyClassAttribute(name: String): Boolean = name == BodyClassAttribute || name == LegacyBodyClassAttribute || name.endsWith(":Class")

    fun namespaceUri(text: String, tagStart: Int): String? {
        val rootEnd = text.indexOf('>', 0).takeIf { it >= 0 } ?: return null
        val declarations = Regex("xmlns(?::([\\w.-]+))?\\s*=\\s*[\\\"']([^\\\"']+)").findAll(text.substring(0, rootEnd)).associate { it.groupValues[1] to it.groupValues[2] }
        val prefix = getTagName(text, tagStart).substringBefore(':', "")
        return declarations[prefix] ?: if (prefix == "sui") SilkyUri else declarations[""]
    }

    private fun findOpenTag(text: String, offset: Int): Int? = findTagStart(text, offset)
    private fun extractAttributeNameForValue(content: String): String = Regex("([A-Za-z0-9_.:-]+)\\s*=\\s*[\\\"'][^\\\"']*$").find(content)?.groupValues?.get(1).orEmpty()
    private fun extractTagName(content: String): String = content.substringAfter('<').trimStart('/').takeWhile { isXmlNameChar(it) }
    private fun findTagStart(text: String, position: Int): Int? { var quote = '\u0000'; for (i in position - 1 downTo 0) { val c = text[i]; if (quote != '\u0000') { if (c == quote) quote = '\u0000'; continue }; if (c == '"' || c == '\'') quote = c else if (c == '>') return null else if (c == '<') return i }; return null }
    private fun skipWhitespaceForward(text: String, position: Int): Int { var i = position; while (i < text.length && text[i].isWhitespace()) i++; return i }
    private fun getTagName(text: String, tagStart: Int): String { var i = tagStart + 1; if (i < text.length && text[i] == '/') i++; while (i < text.length && text[i].isWhitespace()) i++; val start = i; while (i < text.length && isXmlNameChar(text[i])) i++; return text.substring(start, i) }
}
