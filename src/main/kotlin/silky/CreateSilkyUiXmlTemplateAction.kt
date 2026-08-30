package com.moling.silky

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil

class CreateSilkyUiXmlTemplateAction : AnAction("创建 SilkyUI XML 初始模板") {
    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = file?.extension.equals("cs", ignoreCase = true)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val editor = e.getData(CommonDataKeys.EDITOR)
        val text = String(file.contentsToByteArray(), file.charset)
        val offset = editor?.caretModel?.offset ?: 0
        val className = resolveClassNameAt(text, offset) ?: run {
            Messages.showWarningDialog(project, "当前光标不在 C# 类名上。", "SilkyUI Support")
            return
        }
        val fullName = resolveFullClassName(text, className)
        val parent = file.parent ?: return
        val targetName = "$className.sui.xml"
        WriteCommandAction.runWriteCommandAction(project, "创建 SilkyUI XML 初始模板", null, Runnable {
            val target = parent.findChild(targetName) ?: parent.createChildData(this, targetName).also {
                VfsUtil.saveText(it, buildXmlContent(fullName))
            }
            LocalFileSystem.getInstance().refreshAndFindFileByPath(target.path)
            FileEditorManager.getInstance(project).openFile(target, true)
        })
    }

    private fun resolveClassNameAt(text: String, offset: Int): String? {
        val safeOffset = offset.coerceIn(0, text.length)
        val classRegex = Regex("public\\s+(?:partial\\s+|sealed\\s+|abstract\\s+|static\\s+)*class\\s+([A-Za-z_][A-Za-z0-9_]*)")
        return classRegex.findAll(text).firstOrNull { match ->
            val nameRange = match.groups[1]?.range ?: return@firstOrNull false
            safeOffset in nameRange || safeOffset - 1 in nameRange || safeOffset in match.range
        }?.groups?.get(1)?.value ?: classRegex.findAll(text).lastOrNull { it.range.first <= safeOffset }?.groups?.get(1)?.value
    }

    private fun resolveFullClassName(text: String, className: String): String {
        val namespace = Regex("(?m)^\\s*namespace\\s+([A-Za-z_][A-Za-z0-9_.]*)\\s*[;{]").find(text)?.groupValues?.get(1).orEmpty()
        return if (namespace.isBlank()) className else "$namespace.$className"
    }

    private fun buildXmlContent(fullClassName: String): String =
        "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\r\n" +
            "<!-- Class 填写对应类名 -->\r\n" +
            "<Body Class=\"$fullClassName\">\r\n" +
            "</Body>\r\n"
}
