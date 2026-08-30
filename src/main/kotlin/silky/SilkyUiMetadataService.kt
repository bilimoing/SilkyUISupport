package com.moling.silky

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.openapi.fileTypes.FileTypeManager
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.PROJECT)
class SilkyUiMetadataService(private val project: Project) {
    private data class ClassInfo(
        val name: String,
        val fullName: String,
        val baseType: String?,
        val aliases: List<String>,
        val properties: List<SilkyUiProperty>,
        val sourceFilePath: String,
        val sourceOffset: Int
    )

    private data class Snapshot(
        val mappingClasses: List<XmlMappingClass> = emptyList(),
        val groupClasses: List<SilkyUiElementGroupClass> = emptyList()
    )

    private val cached = AtomicReference(Snapshot())
    @Volatile private var lastStamp: Long = -1

    fun getAllClasses(): List<XmlMappingClass> {
        refreshIfNeeded()
        return cached.get().mappingClasses
    }

    fun getAllGroupClasses(): List<SilkyUiElementGroupClass> {
        refreshIfNeeded()
        return cached.get().groupClasses
    }

    fun getClassByName(className: String?): XmlMappingClass? {
        if (className.isNullOrBlank()) return null
        return getAllClasses().firstOrNull { it.alias == className }
    }

    fun getPropertyByName(className: String?, propertyName: String?): SilkyUiProperty? {
        if (className.isNullOrBlank() || propertyName.isNullOrBlank()) return null
        return getClassByName(className)?.properties?.firstOrNull { it.name == propertyName }
    }

    fun findSourceVirtualFile(path: String): VirtualFile? = LocalFileSystem.getInstance().findFileByPath(path.replace('\\', '/'))

    fun refresh() {
        cached.set(scanProject())
        lastStamp = System.nanoTime()
    }

    private fun refreshIfNeeded() {
        if (lastStamp < 0) refresh()
    }

    private fun scanProject(): Snapshot {
        val csType = FileTypeManager.getInstance().getFileTypeByExtension("cs")
        val files = FileTypeIndex.getFiles(csType, GlobalSearchScope.projectScope(project))
        val classes = files.flatMap { parseCSharpFile(it) }
        val byName = classes.associateBy { it.name }
        val byFullName = classes.associateBy { it.fullName }

        fun collectProperties(info: ClassInfo, visited: MutableSet<String> = linkedSetOf()): List<SilkyUiProperty> {
            if (!visited.add(info.fullName)) return info.properties
            val result = linkedMapOf<String, SilkyUiProperty>()
            info.properties.forEach { result[it.name] = it }
            val base = info.baseType?.let { byFullName[it] ?: byName[it.substringAfterLast('.')] }
            if (base != null) {
                for (prop in collectProperties(base, visited)) result.putIfAbsent(prop.name, prop)
            }
            return result.values.toList()
        }

        fun inheritsUiElementGroup(info: ClassInfo): Boolean {
            var current: ClassInfo? = info
            val visited = hashSetOf<String>()
            while (current != null && visited.add(current.fullName)) {
                val base = current.baseType ?: return false
                if (base == "UIElementGroup" || base.endsWith(".UIElementGroup") || base == "SilkyUIFramework.Elements.UIElementGroup") return true
                current = byFullName[base] ?: byName[base.substringAfterLast('.')]
            }
            return false
        }

        val mapping = classes.flatMap { info ->
            val props = collectProperties(info)
            info.aliases.map { alias ->
                XmlMappingClass(info.name, info.fullName, alias, props, info.sourceFilePath, info.sourceOffset)
            }
        }.distinctBy { it.alias }

        val groups = classes.filter(::inheritsUiElementGroup).map { info ->
            SilkyUiElementGroupClass(info.name, info.fullName, collectProperties(info), info.sourceFilePath, info.sourceOffset)
        }.distinctBy { it.fullName }

        return Snapshot(mapping, groups)
    }

    private fun parseCSharpFile(file: VirtualFile): List<ClassInfo> {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return emptyList()
        val text = psiFile.text ?: return emptyList()
        val namespace = parseNamespace(text)
        val enumValues = parseEnums(text)
        val classRegex = Regex("(?s)((?:\\s*\\[[^\\]]*]\\s*)*)public\\s+(?:partial\\s+|sealed\\s+|abstract\\s+|static\\s+)*class\\s+([A-Za-z_][A-Za-z0-9_]*)(?:\\s*:\\s*([^\\{\\n]+))?\\s*\\{")
        return classRegex.findAll(text).map { match ->
            val attrs = match.groupValues[1]
            val name = match.groupValues[2]
            val baseType = match.groupValues.getOrNull(3)?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
            val bodyStart = match.range.last
            val bodyEnd = findMatchingBrace(text, bodyStart) ?: text.length
            val body = text.substring(bodyStart + 1, bodyEnd)
            val fullName = if (namespace.isBlank()) name else "$namespace.$name"
            val props = parseProperties(body, bodyStart + 1, enumValues, fullName, file.path)
            ClassInfo(name, fullName, baseType, parseAliases(attrs), props, file.path, match.range.first)
        }.toList()
    }

    private fun parseNamespace(text: String): String {
        Regex("(?m)^\\s*namespace\\s+([A-Za-z_][A-Za-z0-9_.]*)\\s*;").find(text)?.let { return it.groupValues[1] }
        Regex("(?m)^\\s*namespace\\s+([A-Za-z_][A-Za-z0-9_.]*)\\s*\\{").find(text)?.let { return it.groupValues[1] }
        return ""
    }

    private fun parseAliases(attrs: String): List<String> {
        val result = mutableListOf<String>()
        Regex("XmlElementMapping(?:Attribute)?\\s*\\(\\s*\"([^\"]+)\"").findAll(attrs).forEach { result += it.groupValues[1] }
        return result.distinct()
    }

    private fun parseEnums(text: String): Map<String, List<String>> {
        val result = linkedMapOf<String, List<String>>()
        val enumRegex = Regex("(?s)(?:public\\s+)?enum\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\{(.*?)\\}")
        enumRegex.findAll(text).forEach { match ->
            val name = match.groupValues[1]
            val body = match.groupValues[2]
            val values = body.split(',').mapNotNull { raw ->
                val token = raw.substringBefore('=').trim().lineSequence().lastOrNull()?.trim().orEmpty()
                Regex("[A-Za-z_][A-Za-z0-9_]*").find(token)?.value
            }.filter { it.isNotBlank() }
            result[name] = values
        }
        return result
    }

    private fun parseProperties(body: String, offsetBase: Int, enumValues: Map<String, List<String>>, declaringClass: String, filePath: String): List<SilkyUiProperty> {
        val propRegex = Regex("(?m)public\\s+([A-Za-z_][A-Za-z0-9_<>.,?\\[\\] ]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\{\\s*get\\s*;\\s*set\\s*;")
        return propRegex.findAll(body).map { match ->
            val typeName = match.groupValues[1].trim()
            val propName = match.groupValues[2]
            val enumName = typeName.removeSuffix("?").substringAfterLast('.')
            SilkyUiProperty(propName, typeName, enumValues[enumName].orEmpty(), filePath, offsetBase + match.range.first, declaringClass)
        }.distinctBy { it.name }.toList()
    }

    private fun findMatchingBrace(text: String, openBraceOffset: Int): Int? {
        var depth = 0
        for (i in openBraceOffset until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return null
    }

    companion object {
        fun getInstance(project: Project): SilkyUiMetadataService = project.service()
    }
}
