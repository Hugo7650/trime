/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.designer

import com.osfans.trime.core.Rime
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.util.yaml.Node
import com.osfans.trime.util.yaml.Yaml
import com.osfans.trime.util.yaml.mapping
import com.osfans.trime.util.yaml.sequence
import com.osfans.trime.util.yaml.string
import java.io.File

private const val DERIVED_THEME_NAME_SUFFIX = "/ 可视化副本"

class ThemeDraftRepository {
    fun loadActiveThemeDraft(): ThemeDraft {
        val sourceThemeId = ThemeManager.prefs.selectedTheme.getValue()
        if (ThemeDraftFileStrategy.isDerivedThemeId(sourceThemeId)) {
            val file = DataManager.userDataDir.resolve("$sourceThemeId.yaml")
            return loadDraft(
                ThemeDraftFileStrategy.sourceThemeId(sourceThemeId),
                sourceThemeId,
                file,
                dirty = false,
            )
        }
        val derivedThemeId = ThemeDraftFileStrategy.derivedThemeId(sourceThemeId)
        val derivedFile = DataManager.userDataDir.resolve("$derivedThemeId.yaml")
        if (!derivedFile.exists()) {
            createDerivedTheme(sourceThemeId)
        }
        return loadDraft(sourceThemeId, derivedThemeId, derivedFile, dirty = false)
    }

    fun createDerivedTheme(baseThemeId: String): ThemeDraft {
        check(Rime.deployRimeConfigFile(baseThemeId, "config_version")) {
            "Failed to deploy base theme '$baseThemeId'"
        }
        val baseFile = File(DataManager.resolveDeployedResourcePath(baseThemeId))
        val baseNode = RimeDeployedThemeYaml.parse(baseFile.readText())
        val derivedThemeId = ThemeDraftFileStrategy.uniqueDerivedThemeId(baseThemeId, DataManager.userDataDir)
        val derivedFile = DataManager.userDataDir.resolve("$derivedThemeId.yaml")
        val derivedName = "${baseNode["name"]?.string ?: baseThemeId} $DERIVED_THEME_NAME_SUFFIX"
        val derivedNode = baseNode.withScalars(
            "name" to derivedName,
        )
        ThemeDraftFileTransaction.writeWithBackup(derivedFile, ThemeYamlWriter.write(derivedNode))
        return loadDraft(baseThemeId, derivedThemeId, derivedFile, dirty = false)
    }

    fun saveDraft(draft: ThemeDraft): ThemeDraft {
        val node = parseDraftYaml(draft.yamlText)
        val yamlText = ThemeYamlWriter.write(node)
        val normalizedNode = parseDraftYaml(yamlText)
        Theme.decode(normalizedNode)
        ThemeDraftFileTransaction.writeWithBackup(
            draft.file,
            yamlText,
            updateBackup = currentFileIsValid(draft.file),
        )
        return loadDraft(draft.sourceThemeId, draft.derivedThemeId, draft.file, dirty = false)
    }

    fun deployDraft(draft: ThemeDraft): ThemeDraft {
        requireDeployable(draft)
        val saved = saveDraft(draft)
        requireDeployable(saved)
        if (!ThemeDraftDeploymentTransaction.deployWithRollback(
                saved.derivedThemeId,
                deploy = { Rime.deployRimeConfigFile(it, "config_version") },
                restoreBackup = ::restoreBackup,
                activate = ThemeManager::selectTheme,
            )
        ) {
            error("Failed to deploy theme '${saved.derivedThemeId}'")
        }
        return loadDraft(saved.sourceThemeId, saved.derivedThemeId, saved.file, dirty = false)
    }

    private fun requireDeployable(draft: ThemeDraft) {
        require(!draft.validation.hasErrors) {
            "Theme draft has validation errors: " +
                draft.validation.messages
                    .filter { it.level == ThemeValidationResult.Level.ERROR }
                    .joinToString("; ") { it.message }
        }
    }

    fun updateField(
        draft: ThemeDraft,
        field: ThemeFieldRegistry.Field,
        rawValue: String,
    ): ThemeDraft {
        val node = updateFieldNode(draft.yamlNode, field.path, field, rawValue)
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun createKeyboard(
        draft: ThemeDraft,
        requestedId: String,
    ): ThemeDraft {
        val id = uniqueKeyboardId(draft.yamlNode, requestedId.ifBlank { "keyboard" })
        val keyboard = Node.Mapping(
            Node.Scalar("name") to Node.Scalar(id),
            Node.Scalar("author") to Node.Scalar(""),
            Node.Scalar("width") to Node.Scalar("10"),
            Node.Scalar("height") to Node.Scalar("50"),
            Node.Scalar("keys") to Node.Sequence(
                listOf(
                    keyNode("q", "q", 10f),
                    keyNode("w", "w", 10f),
                    keyNode("e", "e", 10f),
                    keyNode("space", "space", 30f),
                    keyNode("Return", "Return", 20f),
                ),
            ),
        )
        val node = ThemeYamlEditor.updateNode(draft.yamlNode, listOf("preset_keyboards", id), keyboard)
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun copyKeyboard(
        draft: ThemeDraft,
        sourceId: String,
    ): ThemeDraft {
        val source = ThemeYamlEditor.valueAt(draft.yamlNode, listOf("preset_keyboards", sourceId))
            ?: error("Keyboard '$sourceId' does not exist")
        val id = uniqueKeyboardId(draft.yamlNode, "$sourceId.copy")
        val node = ThemeYamlEditor.updateNode(draft.yamlNode, listOf("preset_keyboards", id), source)
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun renameKeyboard(
        draft: ThemeDraft,
        sourceId: String,
        requestedId: String,
    ): ThemeDraft {
        val id = normalizedId(requestedId.ifBlank { sourceId }, "keyboard")
        val node = ThemeKeyboardRenamer.rename(draft.yamlNode, sourceId, id)
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun deleteKeyboard(
        draft: ThemeDraft,
        keyboardId: String,
    ): ThemeDraft {
        val withoutKeyboard = ThemeYamlEditor.remove(draft.yamlNode, listOf("preset_keyboards", keyboardId))
        val styleKeyboardPath = listOf("style", "keyboards")
        val styleKeyboards = ThemeYamlEditor.valueAt(withoutKeyboard, styleKeyboardPath)
            ?.withoutKeyboardRef(keyboardId)
        val node = if (styleKeyboards == null) {
            withoutKeyboard
        } else {
            ThemeYamlEditor.updateNode(withoutKeyboard, styleKeyboardPath, styleKeyboards)
        }
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun createPresetKey(
        draft: ThemeDraft,
        requestedId: String,
    ): ThemeDraft {
        val id = uniqueMappingId(draft.yamlNode, listOf("preset_keys"), requestedId.ifBlank { "preset_key" })
        val presetKey = Node.Mapping(
            Node.Scalar("label") to Node.Scalar(id),
            Node.Scalar("send") to Node.Scalar(id),
        )
        val node = ThemeYamlEditor.updateNode(draft.yamlNode, listOf("preset_keys", id), presetKey)
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun copyPresetKey(
        draft: ThemeDraft,
        sourceId: String,
    ): ThemeDraft {
        val source = ThemeYamlEditor.valueAt(draft.yamlNode, listOf("preset_keys", sourceId))
            ?: error("Preset key '$sourceId' does not exist")
        val id = uniqueMappingId(draft.yamlNode, listOf("preset_keys"), "$sourceId.copy")
        val node = ThemeYamlEditor.updateNode(draft.yamlNode, listOf("preset_keys", id), source)
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun deletePresetKey(
        draft: ThemeDraft,
        presetKeyId: String,
    ): ThemeDraft {
        val node = ThemeYamlEditor.remove(draft.yamlNode, listOf("preset_keys", presetKeyId))
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun updatePresetKeyField(
        draft: ThemeDraft,
        presetKeyId: String,
        field: ThemeFieldRegistry.Field,
        rawValue: String,
    ): ThemeDraft {
        val path = listOf("preset_keys", presetKeyId) + field.path
        val node = updateFieldNode(draft.yamlNode, path, field, rawValue)
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun setPrimaryToolbarButton(
        draft: ThemeDraft,
    ): ThemeDraft {
        val node = ThemeYamlEditor.updateNode(draft.yamlNode, listOf("tool_bar", "primary_button"), toolbarButtonNode())
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun updatePrimaryToolbarButtonField(
        draft: ThemeDraft,
        field: ThemeFieldRegistry.Field,
        rawValue: String,
    ): ThemeDraft {
        val path = listOf("tool_bar", "primary_button") + field.path
        val node = updateFieldNode(draft.yamlNode, path, field, rawValue)
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun insertToolbarButton(
        draft: ThemeDraft,
        index: Int,
    ): ThemeDraft {
        val node = ThemeYamlEditor.insertSequenceItem(
            draft.yamlNode,
            toolbarButtonsPath,
            index,
            toolbarButtonNode(),
        )
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun copyToolbarButton(
        draft: ThemeDraft,
        index: Int,
    ): ThemeDraft {
        val node = ThemeYamlEditor.copySequenceItem(draft.yamlNode, toolbarButtonsPath, index)
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun deleteToolbarButton(
        draft: ThemeDraft,
        index: Int,
    ): ThemeDraft {
        val node = ThemeYamlEditor.removeSequenceItem(draft.yamlNode, toolbarButtonsPath, index)
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun moveToolbarButton(
        draft: ThemeDraft,
        fromIndex: Int,
        toIndex: Int,
    ): ThemeDraft {
        val node = ThemeYamlEditor.moveSequenceItem(draft.yamlNode, toolbarButtonsPath, fromIndex, toIndex)
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun updateToolbarButtonField(
        draft: ThemeDraft,
        index: Int,
        field: ThemeFieldRegistry.Field,
        rawValue: String,
    ): ThemeDraft {
        val value = fieldNode(field, rawValue)
        val node = ThemeYamlEditor.updateSequenceItemMappingPath(
            draft.yamlNode,
            toolbarButtonsPath,
            index,
            field.path,
            value,
        )
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun createLiquidKeyboard(
        draft: ThemeDraft,
        requestedId: String,
    ): ThemeDraft {
        val id = uniqueMappingId(draft.yamlNode, listOf("liquid_keyboard"), requestedId.ifBlank { "liquid" })
        val withKeyboard = ThemeYamlEditor.updateNode(
            draft.yamlNode,
            listOf("liquid_keyboard", id),
            liquidKeyboardNode(id),
        )
        val node = updateLiquidKeyboardIds(withKeyboard, currentLiquidKeyboardIds(withKeyboard) + id)
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun copyLiquidKeyboard(
        draft: ThemeDraft,
        sourceId: String,
    ): ThemeDraft {
        val source = ThemeYamlEditor.valueAt(draft.yamlNode, listOf("liquid_keyboard", sourceId))
            ?: error("Liquid keyboard '$sourceId' does not exist")
        val id = uniqueMappingId(draft.yamlNode, listOf("liquid_keyboard"), "$sourceId.copy")
        val withKeyboard = ThemeYamlEditor.updateNode(draft.yamlNode, listOf("liquid_keyboard", id), source)
        val node = updateLiquidKeyboardIds(withKeyboard, currentLiquidKeyboardIds(withKeyboard) + id)
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun deleteLiquidKeyboard(
        draft: ThemeDraft,
        keyboardId: String,
    ): ThemeDraft {
        val withoutKeyboard = ThemeYamlEditor.remove(draft.yamlNode, listOf("liquid_keyboard", keyboardId))
        val node = updateLiquidKeyboardIds(
            withoutKeyboard,
            currentLiquidKeyboardIds(withoutKeyboard).filterNot { it == keyboardId },
        )
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun updateLiquidKeyboardField(
        draft: ThemeDraft,
        keyboardId: String,
        field: ThemeFieldRegistry.Field,
        rawValue: String,
    ): ThemeDraft {
        val path = listOf("liquid_keyboard", keyboardId) + field.path
        val node = updateFieldNode(draft.yamlNode, path, field, rawValue)
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun insertLiquidKeyboardKey(
        draft: ThemeDraft,
        keyboardId: String,
        index: Int,
    ): ThemeDraft {
        val normalized = normalizeLiquidKeyboardKeys(draft, keyboardId)
        val node = ThemeYamlEditor.insertSequenceItem(
            normalized,
            liquidKeyboardKeysPath(keyboardId),
            index,
            liquidKeyNode("", ""),
        )
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun copyLiquidKeyboardKey(
        draft: ThemeDraft,
        keyboardId: String,
        index: Int,
    ): ThemeDraft {
        val normalized = normalizeLiquidKeyboardKeys(draft, keyboardId)
        val node = ThemeYamlEditor.copySequenceItem(normalized, liquidKeyboardKeysPath(keyboardId), index)
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun deleteLiquidKeyboardKey(
        draft: ThemeDraft,
        keyboardId: String,
        index: Int,
    ): ThemeDraft {
        val normalized = normalizeLiquidKeyboardKeys(draft, keyboardId)
        val node = ThemeYamlEditor.removeSequenceItem(normalized, liquidKeyboardKeysPath(keyboardId), index)
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun moveLiquidKeyboardKey(
        draft: ThemeDraft,
        keyboardId: String,
        fromIndex: Int,
        toIndex: Int,
    ): ThemeDraft {
        val normalized = normalizeLiquidKeyboardKeys(draft, keyboardId)
        val node = ThemeYamlEditor.moveSequenceItem(normalized, liquidKeyboardKeysPath(keyboardId), fromIndex, toIndex)
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun reorderLiquidKeyboardKeys(
        draft: ThemeDraft,
        keyboardId: String,
        order: List<Int>,
    ): ThemeDraft {
        val normalized = normalizeLiquidKeyboardKeys(draft, keyboardId)
        val node = ThemeYamlEditor.reorderSequenceItems(normalized, liquidKeyboardKeysPath(keyboardId), order)
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun updateLiquidKeyboardKey(
        draft: ThemeDraft,
        keyboardId: String,
        index: Int,
        action: String,
        label: String,
    ): ThemeDraft {
        val normalized = normalizeLiquidKeyboardKeys(draft, keyboardId)
        val path = liquidKeyboardKeysPath(keyboardId)
        val withAction = ThemeYamlEditor.updateSequenceItemMappingScalar(normalized, path, index, "click", action.trim())
        val node = ThemeYamlEditor.updateSequenceItemMappingScalar(withAction, path, index, "label", label.trim())
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun updateKeyboardKeyField(
        draft: ThemeDraft,
        keyboardId: String,
        keyIndex: Int,
        field: String,
        value: String,
    ): ThemeDraft {
        val normalizedValue = ThemeFieldRegistry.keyFields
            .firstOrNull { it.key == field }
            ?.let { validateFieldValue(it, value) }
            ?: value.trim()
        val node = ThemeYamlEditor.updateSequenceItemMappingScalar(
            draft.yamlNode,
            listOf("preset_keyboards", keyboardId, "keys"),
            keyIndex,
            field,
            normalizedValue,
        )
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun updateKeyboardField(
        draft: ThemeDraft,
        keyboardId: String,
        field: ThemeFieldRegistry.Field,
        rawValue: String,
    ): ThemeDraft {
        val path = listOf("preset_keyboards", keyboardId) + field.path
        val node = updateFieldNode(draft.yamlNode, path, field, rawValue)
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun updateKeyboardKeyPopup(
        draft: ThemeDraft,
        keyboardId: String,
        keyIndex: Int,
        popup: List<String>,
    ): ThemeDraft {
        val node = ThemeYamlEditor.updateSequenceItemMappingNode(
            draft.yamlNode,
            listOf("preset_keyboards", keyboardId, "keys"),
            keyIndex,
            "popup",
            Node.Sequence(popup.filter { it.isNotEmpty() }.map { Node.Scalar(it) }),
        )
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun insertKeyboardKey(
        draft: ThemeDraft,
        keyboardId: String,
        index: Int,
    ): ThemeDraft {
        val node = ThemeYamlEditor.insertSequenceItem(
            draft.yamlNode,
            keyboardKeysPath(keyboardId),
            index,
            keyNode("", "", 10f),
        )
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun copyKeyboardKey(
        draft: ThemeDraft,
        keyboardId: String,
        index: Int,
    ): ThemeDraft {
        val node = ThemeYamlEditor.copySequenceItem(draft.yamlNode, keyboardKeysPath(keyboardId), index)
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun deleteKeyboardKey(
        draft: ThemeDraft,
        keyboardId: String,
        index: Int,
    ): ThemeDraft {
        val node = ThemeYamlEditor.removeSequenceItem(draft.yamlNode, keyboardKeysPath(keyboardId), index)
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun moveKeyboardKey(
        draft: ThemeDraft,
        keyboardId: String,
        fromIndex: Int,
        toIndex: Int,
    ): ThemeDraft {
        val node = ThemeYamlEditor.moveSequenceItem(draft.yamlNode, keyboardKeysPath(keyboardId), fromIndex, toIndex)
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun reorderKeyboardKeys(
        draft: ThemeDraft,
        keyboardId: String,
        orderText: String,
    ): ThemeDraft {
        val keyCount = (ThemeYamlEditor.valueAt(draft.yamlNode, keyboardKeysPath(keyboardId)) as? Node.Sequence)?.size
            ?: error("Keyboard '$keyboardId' has no keys")
        val order = orderText.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.toIntOrNull()?.minus(1) ?: error("Invalid key index '$it'") }
        require(order.size == keyCount) { "Order must contain $keyCount key index(es)" }
        val node = ThemeYamlEditor.reorderSequenceItems(draft.yamlNode, keyboardKeysPath(keyboardId), order)
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun resizeKeyboardKeys(
        draft: ThemeDraft,
        keyboardId: String,
        width: String,
        height: String,
    ): ThemeDraft {
        val widthField = ThemeFieldRegistry.keyFields.first { it.key == "width" }
        val heightField = ThemeFieldRegistry.keyFields.first { it.key == "height" }
        val values = buildMap {
            if (width.isNotBlank()) put("width", validateFieldValue(widthField, width))
            if (height.isNotBlank()) put("height", validateFieldValue(heightField, height))
        }
        val node = ThemeYamlEditor.updateAllSequenceMappingScalars(draft.yamlNode, keyboardKeysPath(keyboardId), values)
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun updateColor(
        draft: ThemeDraft,
        schemeId: String,
        colorKey: String,
        value: String,
    ): ThemeDraft {
        val path = listOf("preset_color_schemes", schemeId, colorKey)
        val trimmedValue = value.trim()
        val node = if (trimmedValue.isEmpty()) {
            ThemeYamlEditor.remove(draft.yamlNode, path)
        } else {
            ThemeYamlEditor.updateScalar(draft.yamlNode, path, trimmedValue)
        }
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun createColorScheme(
        draft: ThemeDraft,
        requestedId: String,
    ): ThemeDraft {
        val id = uniqueMappingId(draft.yamlNode, listOf("preset_color_schemes"), requestedId.ifBlank { "color_scheme" })
        val colorScheme = Node.Mapping(
            Node.Scalar("name") to Node.Scalar(id),
            Node.Scalar("author") to Node.Scalar(""),
            Node.Scalar("back_color") to Node.Scalar("0xffffff"),
            Node.Scalar("text_color") to Node.Scalar("0x000000"),
            Node.Scalar("key_back_color") to Node.Scalar("back_color"),
            Node.Scalar("key_text_color") to Node.Scalar("text_color"),
            Node.Scalar("keyboard_back_color") to Node.Scalar("back_color"),
        )
        val node = ThemeYamlEditor.updateNode(draft.yamlNode, listOf("preset_color_schemes", id), colorScheme)
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun copyColorScheme(
        draft: ThemeDraft,
        sourceId: String,
    ): ThemeDraft {
        val source = ThemeYamlEditor.valueAt(draft.yamlNode, listOf("preset_color_schemes", sourceId))
            ?: error("Color scheme '$sourceId' does not exist")
        val id = uniqueMappingId(draft.yamlNode, listOf("preset_color_schemes"), "$sourceId.copy")
        val node = ThemeYamlEditor.updateNode(draft.yamlNode, listOf("preset_color_schemes", id), source)
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun deleteColorScheme(
        draft: ThemeDraft,
        schemeId: String,
    ): ThemeDraft {
        val colorSchemes = draft.yamlNode["preset_color_schemes"]?.mapping
            ?: error("Theme has no color schemes")
        require(colorSchemes.keys.any { it.string == schemeId }) { "Color scheme '$schemeId' does not exist" }
        require(colorSchemes.size > 1) { "A theme must contain at least one color scheme" }
        val node = ThemeYamlEditor.remove(draft.yamlNode, listOf("preset_color_schemes", schemeId))
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun updateFallbackColor(
        draft: ThemeDraft,
        colorKey: String,
        fallbackKey: String,
    ): ThemeDraft {
        val path = listOf("fallback_colors", colorKey)
        val trimmedValue = fallbackKey.trim()
        val node = if (trimmedValue.isEmpty()) {
            ThemeYamlEditor.remove(draft.yamlNode, path)
        } else {
            ThemeYamlEditor.updateScalar(draft.yamlNode, path, trimmedValue)
        }
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun updateSource(
        draft: ThemeDraft,
        yamlText: String,
    ): ThemeDraft {
        val node = parseDraftYaml(yamlText)
        return draftFromNode(draft.sourceThemeId, draft.derivedThemeId, draft.file, node, dirty = true)
    }

    fun restoreBackup(themeId: String): Boolean {
        val file = DataManager.userDataDir.resolve("$themeId.yaml")
        return ThemeDraftFileTransaction.restoreBackup(file)
    }

    fun hasActiveThemeBackup(): Boolean {
        val themeId = ThemeManager.prefs.selectedTheme.getValue()
        if (!ThemeDraftFileStrategy.isDerivedThemeId(themeId)) return false
        val file = DataManager.userDataDir.resolve("$themeId.yaml")
        return ThemeDraftFileStrategy.backupFile(file).exists()
    }

    fun restoreActiveThemeBackup(): ThemeDraft {
        val themeId = ThemeManager.prefs.selectedTheme.getValue()
        require(ThemeDraftFileStrategy.isDerivedThemeId(themeId)) {
            "Only designer themes can be restored from a draft backup"
        }
        val file = DataManager.userDataDir.resolve("$themeId.yaml")
        val backup = ThemeDraftFileStrategy.backupFile(file)
        check(backup.exists()) { "No backup exists for theme '$themeId'" }
        val backupNode = readMapping(backup)
        Theme.decode(backupNode)
        val validation = ThemeDraftValidator.validate(backupNode)
        check(!validation.hasErrors) {
            "Theme backup has validation errors: " +
                validation.messages
                    .filter { it.level == ThemeValidationResult.Level.ERROR }
                    .joinToString("; ") { it.message }
        }
        check(restoreBackup(themeId)) { "No backup exists for theme '$themeId'" }
        check(Rime.deployRimeConfigFile(themeId, "config_version")) {
            "Failed to deploy restored theme '$themeId'"
        }
        ThemeManager.selectTheme(themeId)
        return loadDraft(
            ThemeDraftFileStrategy.sourceThemeId(themeId),
            themeId,
            file,
            dirty = false,
        )
    }

    private fun loadDraft(
        sourceThemeId: String,
        derivedThemeId: String,
        file: File,
        dirty: Boolean,
    ): ThemeDraft {
        val yamlText = file.readText()
        val node = RimeDeployedThemeYaml.parse(yamlText)
        return draftFromNode(sourceThemeId, derivedThemeId, file, node, dirty)
    }

    private fun draftFromNode(
        sourceThemeId: String,
        derivedThemeId: String,
        file: File,
        node: Node.Mapping,
        dirty: Boolean,
    ): ThemeDraft {
        val yamlText = ThemeYamlWriter.write(node)
        val normalizedNode = parseDraftYaml(yamlText)
        val theme = Theme.decode(normalizedNode)
        return ThemeDraft(
            theme = theme,
            sourceThemeId = sourceThemeId,
            derivedThemeId = derivedThemeId,
            file = file,
            yamlNode = normalizedNode,
            yamlText = yamlText,
            dirty = dirty,
            validation = ThemeDraftValidator.validate(normalizedNode),
        )
    }

    private fun readMapping(file: File): Node.Mapping = parseDraftYaml(file.readText())

    private fun currentFileIsValid(file: File): Boolean {
        if (!file.exists()) return false
        return runCatching {
            val node = readMapping(file)
            Theme.decode(node)
            !ThemeDraftValidator.validate(node).hasErrors
        }.getOrDefault(false)
    }

    private fun parseDraftYaml(yamlText: String): Node.Mapping = Yaml.parseToYamlNode(yamlText).mapping ?: error("Theme YAML root must be a mapping")

    private fun updateFieldNode(
        node: Node.Mapping,
        path: List<String>,
        field: ThemeFieldRegistry.Field,
        rawValue: String,
    ): Node.Mapping = ThemeYamlEditor.updateNode(node, path, fieldNode(field, rawValue))

    private fun fieldNode(
        field: ThemeFieldRegistry.Field,
        rawValue: String,
    ): Node = when (field.type) {
        ThemeFieldRegistry.Type.STRING_LIST -> {
            val values = rawValue.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (field.key == "size") {
                values.forEach { value ->
                    val size = value.toIntOrNull()
                        ?: throw IllegalArgumentException("${field.title} values must be integers")
                    require(size >= 0) { "${field.title} values must be at least 0" }
                }
            }
            Node.Sequence(values.map { Node.Scalar(it) })
        }
        else -> Node.Scalar(validateFieldValue(field, rawValue))
    }

    private fun validateFieldValue(
        field: ThemeFieldRegistry.Field,
        rawValue: String,
    ): String {
        val value = rawValue.trim()
        if (value.isEmpty()) return value
        val numericValue = when (field.type) {
            ThemeFieldRegistry.Type.INT -> value.toIntOrNull()?.toDouble()
                ?: throw IllegalArgumentException("${field.title} must be an integer")
            ThemeFieldRegistry.Type.FLOAT -> value.toDoubleOrNull()?.takeIf { it.isFinite() }
                ?: throw IllegalArgumentException("${field.title} must be a number")
            else -> null
        }
        if (field.key == "columns") {
            val columns = value.toInt()
            require(columns == -1 || columns > 0) { "${field.title} must be -1 or greater than 0" }
        }
        require(field.type != ThemeFieldRegistry.Type.BOOLEAN || value.toBooleanStrictOrNull() != null) {
            "${field.title} must be true or false"
        }
        require(field.type != ThemeFieldRegistry.Type.ENUM || value in field.enumValues) {
            "${field.title} must be one of: ${field.enumValues.joinToString()}"
        }
        field.minValue?.let { minimum ->
            require(numericValue == null || numericValue >= minimum) {
                "${field.title} must be at least ${minimum.formatFieldLimit()}"
            }
        }
        field.maxValue?.let { maximum ->
            require(numericValue == null || numericValue <= maximum) {
                "${field.title} must be at most ${maximum.formatFieldLimit()}"
            }
        }
        return value
    }

    private fun Double.formatFieldLimit(): String = if (rem(1.0) == 0.0) toInt().toString() else toString()

    private fun uniqueKeyboardId(
        node: Node.Mapping,
        requestedId: String,
    ): String {
        val existing = node["preset_keyboards"]?.mapping?.keys?.mapNotNull { it.string }.orEmpty().toSet()
        val base = normalizedId(requestedId, "keyboard")
        if (base !in existing) return base
        return generateSequence(2) { it + 1 }
            .map { "$base$it" }
            .first { it !in existing }
    }

    private fun uniqueMappingId(
        node: Node.Mapping,
        path: List<String>,
        requestedId: String,
    ): String {
        val existing = ThemeYamlEditor.valueAt(node, path)?.mapping?.keys?.mapNotNull { it.string }.orEmpty().toSet()
        val base = normalizedId(requestedId, "item")
        if (base !in existing) return base
        return generateSequence(2) { it + 1 }
            .map { "$base$it" }
            .first { it !in existing }
    }

    private fun normalizedId(
        requestedId: String,
        fallback: String,
    ): String = requestedId.replace(Regex("""[^\w.-]"""), "_").ifBlank { fallback }

    private fun currentLiquidKeyboardIds(node: Node.Mapping): List<String> = ThemeYamlEditor.valueAt(node, listOf("liquid_keyboard", "keyboards"))
        ?.sequence
        ?.mapNotNull { it.string }
        .orEmpty()

    private fun updateLiquidKeyboardIds(
        node: Node.Mapping,
        ids: List<String>,
    ): Node.Mapping = ThemeYamlEditor.updateSequence(node, listOf("liquid_keyboard", "keyboards"), ids.distinct())

    private fun Node.withoutKeyboardRef(keyboardId: String): Node? = when (this) {
        is Node.Sequence -> Node.Sequence(
            filterNot { it.string == keyboardId },
            anchor,
        )
        is Node.Scalar -> Node.Scalar(
            string.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != keyboardId }
                .joinToString(", "),
            anchor,
        )
        is Node.Mapping -> Node.Mapping(
            pairs - Node.Scalar(keyboardId),
            anchor,
        )
        is Node.Alias -> null
    }

    private fun keyNode(
        label: String,
        click: String,
        width: Float,
    ): Node.Mapping = Node.Mapping(
        Node.Scalar("label") to Node.Scalar(label),
        Node.Scalar("click") to Node.Scalar(click),
        Node.Scalar("width") to Node.Scalar(width.toString()),
    )

    private fun liquidKeyboardNode(id: String): Node.Mapping = Node.Mapping(
        Node.Scalar("name") to Node.Scalar(id),
        Node.Scalar("type") to Node.Scalar("SINGLE"),
        Node.Scalar("keys") to Node.Sequence(emptyList()),
    )

    private fun normalizeLiquidKeyboardKeys(
        draft: ThemeDraft,
        keyboardId: String,
    ): Node.Mapping {
        val keyboard = draft.theme.liquidKeyboard.keyboards.firstOrNull { it.id == keyboardId }
            ?: error("Liquid keyboard '$keyboardId' does not exist")
        val keys = Node.Sequence(keyboard.keys.map { liquidKeyNode(it.text, it.altText) })
        return ThemeYamlEditor.updateNode(draft.yamlNode, liquidKeyboardKeysPath(keyboardId), keys)
    }

    private fun liquidKeyNode(
        action: String,
        label: String,
    ): Node.Mapping = Node.Mapping(
        Node.Scalar("click") to Node.Scalar(action),
        Node.Scalar("label") to Node.Scalar(label),
    )

    private fun toolbarButtonNode(): Node.Mapping = Node.Mapping(
        Node.Scalar("background") to Node.Mapping(
            Node.Scalar("type") to Node.Scalar("rectangle"),
            Node.Scalar("corner_radius") to Node.Scalar("10"),
        ),
        Node.Scalar("foreground") to Node.Mapping(
            Node.Scalar("style") to Node.Scalar("text"),
            Node.Scalar("normal") to Node.Scalar(""),
        ),
        Node.Scalar("action") to Node.Scalar(""),
        Node.Scalar("long_press_action") to Node.Scalar(""),
    )

    private fun keyboardKeysPath(keyboardId: String): List<String> = listOf("preset_keyboards", keyboardId, "keys")

    private fun liquidKeyboardKeysPath(keyboardId: String): List<String> = listOf("liquid_keyboard", keyboardId, "keys")

    private val toolbarButtonsPath: List<String> = listOf("tool_bar", "buttons")

    private fun Node.Mapping.withScalars(vararg values: Pair<String, String>): Node.Mapping {
        val replacements = values.associate { Node.Scalar(it.first) to Node.Scalar(it.second) }
        return Node.Mapping(pairs + replacements)
    }
}
