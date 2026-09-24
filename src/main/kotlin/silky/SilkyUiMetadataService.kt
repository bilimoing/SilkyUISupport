package com.moling.silky

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.PROJECT)
class SilkyUiMetadataService(private val project: Project) {
    private data class ClassInfo(
        val name: String,
        val fullName: String,
        val namespace: String,
        val baseType: String?,
        val aliases: List<String>,
        val properties: List<SilkyUiProperty>,
        val styles: List<SilkyUiStyleMetadata>,
        val sourceFilePath: String,
        val sourceOffset: Int
    )

    private val cached = AtomicReference(SilkyUiMetadataSnapshot())
    @Volatile private var lastStamp: Long = -1

    fun getSnapshot(): SilkyUiMetadataSnapshot {
        refreshIfNeeded()
        return cached.get()
    }

    fun getAllClasses(): List<XmlMappingClass> = getSnapshot().mappingClasses
    fun getAllGroupClasses(): List<SilkyUiElementGroupClass> = getSnapshot().groupClasses
    fun getAllTypes(): List<SilkyUiClrType> = getSnapshot().types
    fun getAllStyles(): List<SilkyUiStyleMetadata> = getSnapshot().styles

    fun getClassByName(className: String?): XmlMappingClass? {
        if (className.isNullOrBlank()) return null
        return getAllClasses().firstOrNull { it.alias == className || it.fullName == className || it.name == className }
    }

    fun getTypeByName(typeName: String?): SilkyUiClrType? {
        if (typeName.isNullOrBlank()) return null
        return getAllTypes().firstOrNull { it.fullName == typeName || it.name == typeName }
    }

    fun getPropertyByName(className: String?, propertyName: String?): SilkyUiProperty? {
        if (className.isNullOrBlank() || propertyName.isNullOrBlank()) return null
        return (getClassByName(className)?.properties ?: getTypeByName(className)?.properties)
            ?.firstOrNull { it.name == propertyName }
    }

    fun findClrTypes(namespace: String): List<SilkyUiClrType> = getAllTypes().filter { it.namespace == namespace }
    fun findSourceVirtualFile(path: String): VirtualFile? = LocalFileSystem.getInstance().findFileByPath(path.replace('\\', '/'))

    fun refresh() {
        cached.set(scanProject())
        lastStamp = System.nanoTime()
    }

    private fun refreshIfNeeded() {
        if (lastStamp < 0) refresh()
    }

    private fun scanProject(): SilkyUiMetadataSnapshot {
        val csType = FileTypeManager.getInstance().getFileTypeByExtension("cs")
        val classes = FileTypeIndex.getFiles(csType, GlobalSearchScope.projectScope(project)).flatMap(::parseCSharpFile)
        val byName = classes.associateBy { it.name }
        val byFullName = classes.associateBy { it.fullName }

        fun baseOf(info: ClassInfo): ClassInfo? = info.baseType?.let { byFullName[it] ?: byName[it.substringAfterLast('.')] }
        fun collectProperties(info: ClassInfo, visited: MutableSet<String> = linkedSetOf()): List<SilkyUiProperty> {
            if (!visited.add(info.fullName)) return emptyList()
            val result = linkedMapOf<String, SilkyUiProperty>()
            info.properties.forEach { result[it.name] = it }
            baseOf(info)?.let { collectProperties(it, visited).forEach { p -> result.putIfAbsent(p.name, p) } }
            return result.values.toList()
        }
        fun isGroup(info: ClassInfo): Boolean {
            var current: ClassInfo? = info
            val visited = hashSetOf<String>()
            while (current != null && visited.add(current.fullName)) {
                val base = current.baseType ?: return false
                if (base == "UIElementGroup" || base.endsWith(".UIElementGroup") || base == "SilkyUIFramework.Elements.UIElementGroup") return true
                current = byFullName[base] ?: byName[base.substringAfterLast('.')]
            }
            return false
        }

        val mapping = classes.flatMap { info -> info.aliases.map { alias ->
            XmlMappingClass(info.name, info.fullName, alias, collectProperties(info), info.sourceFilePath, info.sourceOffset, info.namespace)
        } }.distinctBy { it.alias }
        val groups = classes.filter(::isGroup).map { info ->
            SilkyUiElementGroupClass(info.name, info.fullName, collectProperties(info), info.sourceFilePath, info.sourceOffset, info.namespace)
        }.distinctBy { it.fullName }
        val types = classes.map { info ->
            SilkyUiClrType(info.name, info.fullName, info.namespace, info.baseType, collectProperties(info), info.styles, info.sourceFilePath, info.sourceOffset)
        }.distinctBy { it.fullName }
        return SilkyUiMetadataSnapshot(types, mapping, groups, classes.flatMap { it.styles }.distinctBy { it.name })
    }

    private fun parseCSharpFile(file: VirtualFile): List<ClassInfo> {
        val text = PsiManager.getInstance(project).findFile(file)?.text ?: return emptyList()
        val namespace = parseNamespace(text)
        val enumValues = parseEnums(text)
        val classRegex = Regex("(?s)((?:\\s*\\[[^\\]]*]\\s*)*)public\\s+(?:partial\\s+|sealed\\s+|abstract\\s+|static\\s+)*class\\s+([A-Za-z_][A-Za-z0-9_]*)(?:\\s*:\\s*([^\\{\\n]+))?\\s*\\{")
        return classRegex.findAll(text).map { match ->
            val attrs = match.groupValues[1]
            val name = match.groupValues[2]
            val baseType = match.groupValues.getOrNull(3)?.split(',')?.firstOrNull()?.trim()?.takeIf(String::isNotBlank)
            val bodyStart = match.range.last
            val bodyEnd = findMatchingBrace(text, bodyStart) ?: text.length
            val fullName = if (namespace.isBlank()) name else "$namespace.$name"
            ClassInfo(name, fullName, namespace, baseType, parseAliases(attrs), parseProperties(text.substring(bodyStart + 1, bodyEnd), bodyStart + 1, enumValues, fullName, file.path), parseStyles(attrs, file.path, match.range.first), file.path, match.range.first)
        }.toList()
    }

    private fun parseNamespace(text: String): String = Regex("(?m)^\\s*namespace\\s+([A-Za-z_][A-Za-z0-9_.]*)").find(text)?.groupValues?.get(1).orEmpty()

    private fun parseAliases(attrs: String): List<String> = Regex("(?:XmlElementMapping|XmlElementMappingAttribute)\\s*\\(\\s*[\\\"']([^\\\"']+)").findAll(attrs).map { it.groupValues[1] }.distinct().toList()

    private fun parseStyles(attrs: String, path: String, offset: Int): List<SilkyUiStyleMetadata> = Regex("sui:Style\\s*\\(\\s*[\\\"']([^\\\"']+)[\\\"']([^)]*)\\)").findAll(attrs).map { match ->
        val target = Regex("Target\\s*=\\s*typeof\\s*\\(\\s*([A-Za-z0-9_.]+)").find(match.groupValues[2])?.groupValues?.get(1)
        SilkyUiStyleMetadata(match.groupValues[1], target, path, offset + match.range.first)
    }.toList()

    private fun parseEnums(text: String): Map<String, List<String>> = Regex("(?s)(?:public\\s+)?enum\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\{(.*?)\\}").findAll(text).associate { match ->
        match.groupValues[1] to match.groupValues[2].split(',').mapNotNull { Regex("[A-Za-z_][A-Za-z0-9_]*").find(it.substringBefore('='))?.value }.filter(String::isNotBlank)
    }

    private fun parseProperties(body: String, offsetBase: Int, enumValues: Map<String, List<String>>, declaringClass: String, path: String): List<SilkyUiProperty> {
        val regex = Regex("(?m)public\\s+(?:static\\s+)?([A-Za-z_][A-Za-z0-9_<>.,?\\[\\] ]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\{([^}]*)}")
        return regex.findAll(body).map { match ->
            val accessors = match.groupValues[3]
            val typeName = match.groupValues[1].trim()
            val name = match.groupValues[2]
            val enumName = typeName.removeSuffix("?").substringAfterLast('.')
            val nested = typeName.takeIf { it[0].isUpperCase() && it !in enumValues.keys }
            SilkyUiProperty(name, typeName, enumValues[enumName].orEmpty(), path, offsetBase + match.range.first, declaringClass, "get" in accessors, "set" in accessors, match.value.contains("static"), nestedTypeName = nested)
        }.filter { it.canRead }.distinctBy { it.name }.toList()
    }

    private fun findMatchingBrace(text: String, openBraceOffset: Int): Int? {
        var depth = 0
        for (i in openBraceOffset until text.length) when (text[i]) {
            '{' -> depth++
            '}' -> if (--depth == 0) return i
        }
        return null
    }

    companion object { fun getInstance(project: Project): SilkyUiMetadataService = project.service() }
}
