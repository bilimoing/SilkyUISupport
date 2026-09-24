package com.moling.silky

/**
 * Stable semantic classification shared by future highlighting providers.
 * It intentionally avoids platform-specific color APIs so the plugin remains compatible
 * with Rider platform versions while XML annotator/completion consume the same model.
 */
object SilkyUiSyntaxClassification {
    fun classify(name: String, isAttribute: Boolean = false): SilkyUiSemanticKind = when {
        name.startsWith("bind:") -> SilkyUiSemanticKind.Binding
        name.startsWith("prop:") -> SilkyUiSemanticKind.PropertyObject
        name == "Style" -> SilkyUiSemanticKind.Style
        name == "Target" -> SilkyUiSemanticKind.StyleTarget
        name.startsWith("xmlns") -> SilkyUiSemanticKind.Namespace
        isAttribute -> SilkyUiSemanticKind.Attribute
        else -> SilkyUiSemanticKind.Element
    }
}
