package com.moling.silky

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

class SilkyUiGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor): Array<PsiElement>? {
        val file = sourceElement?.containingFile ?: return null
        if (!SilkyUiXmlUtil.isSilkyUiXml(file)) return null
        val metadata = SilkyUiMetadataService.getInstance(file.project)
        val resolution = SilkyUiXmlUtil.resolveSymbol(file.text, offset.coerceAtMost(file.textLength - 1), metadata) ?: return null
        val path = when (resolution.kind) {
            SilkyUiSymbolKind.Element -> resolution.mappingClass?.sourceFilePath
            SilkyUiSymbolKind.Attribute, SilkyUiSymbolKind.PropertyObject -> resolution.property?.sourceFilePath
            SilkyUiSymbolKind.BodyClass -> resolution.bodyClass?.sourceFilePath
            SilkyUiSymbolKind.Style -> resolution.style?.sourceFilePath
            SilkyUiSymbolKind.Namespace -> null
        } ?: return null
        val targetOffset = when (resolution.kind) {
            SilkyUiSymbolKind.Element -> resolution.mappingClass?.sourceOffset
            SilkyUiSymbolKind.Attribute, SilkyUiSymbolKind.PropertyObject -> resolution.property?.sourceOffset
            SilkyUiSymbolKind.BodyClass -> resolution.bodyClass?.sourceOffset
            SilkyUiSymbolKind.Style -> resolution.style?.sourceOffset
            SilkyUiSymbolKind.Namespace -> 0
        } ?: 0
        val vFile = metadata.findSourceVirtualFile(path) ?: return null
        OpenFileDescriptor(file.project, vFile, targetOffset).navigate(true)
        return PsiElement.EMPTY_ARRAY
    }

}
