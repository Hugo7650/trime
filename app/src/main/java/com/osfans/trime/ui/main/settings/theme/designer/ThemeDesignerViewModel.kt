/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.theme.designer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osfans.trime.data.theme.designer.ThemeDraft
import com.osfans.trime.data.theme.designer.ThemeDraftRepository
import com.osfans.trime.data.theme.designer.ThemeFieldRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class ThemeDesignerViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    private val repository = ThemeDraftRepository()
    private val keyboardKeyIds = restoreKeyIds(KEYBOARD_KEY_IDS)
    private val liquidKeyboardKeyIds = restoreKeyIds(LIQUID_KEY_IDS)

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = State.Loading
            _state.value = runCatching {
                withContext(Dispatchers.IO) { repository.loadActiveThemeDraft() }
            }.fold(
                onSuccess = State::Loaded,
                onFailure = {
                    State.Error(
                        it.message ?: "Failed to load theme draft",
                        canRestoreBackup = repository.hasActiveThemeBackup(),
                    )
                },
            )
        }
    }

    fun restoreBackup() {
        viewModelScope.launch {
            _state.value = State.Loading
            _state.value = runCatching {
                withContext(Dispatchers.IO) { repository.restoreActiveThemeBackup() }
            }.fold(
                onSuccess = State::Loaded,
                onFailure = {
                    State.Error(
                        it.message ?: "Failed to restore theme backup",
                        canRestoreBackup = repository.hasActiveThemeBackup(),
                    )
                },
            )
        }
    }

    fun save() {
        val draft = currentDraft() ?: return
        viewModelScope.launch {
            _state.value = State.Working(draft, "Saving draft")
            _state.value = runCatching {
                withContext(Dispatchers.IO) { repository.saveDraft(draft) }
            }.fold(
                onSuccess = State::Loaded,
                onFailure = { State.Error(it.message ?: "Failed to save theme draft", draft) },
            )
        }
    }

    fun deploy() {
        val draft = currentDraft() ?: return
        if (draft.validation.hasErrors) return
        viewModelScope.launch {
            _state.value = State.Working(draft, "Deploying draft")
            _state.value = runCatching {
                withContext(Dispatchers.IO) { repository.deployDraft(draft) }
            }.fold(
                onSuccess = State::Loaded,
                onFailure = { State.Error(it.message ?: "Failed to deploy theme draft", draft) },
            )
        }
    }

    fun updateField(
        fieldKey: String,
        value: String,
    ) {
        val draft = currentDraft() ?: return
        val field = ThemeFieldRegistry.field(fieldKey) ?: return
        _state.value = runCatching {
            repository.updateField(draft, field, value)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to update theme draft", draft) },
        )
    }

    fun createKeyboard(id: String) {
        val draft = currentDraft() ?: return
        _state.value = runCatching {
            repository.createKeyboard(draft, id)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to create keyboard", draft) },
        )
    }

    fun copyKeyboard(id: String) {
        val draft = currentDraft() ?: return
        _state.value = runCatching {
            repository.copyKeyboard(draft, id)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to copy keyboard", draft) },
        )
    }

    fun renameKeyboard(
        id: String,
        newId: String,
    ) {
        val draft = currentDraft() ?: return
        keyboardKeyIds.remove(id)?.let { keyboardKeyIds[newId] = it }
        _state.value = runCatching {
            repository.renameKeyboard(draft, id, newId)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to rename keyboard", draft) },
        )
    }

    fun deleteKeyboard(id: String) {
        val draft = currentDraft() ?: return
        keyboardKeyIds.remove(id)
        _state.value = runCatching {
            repository.deleteKeyboard(draft, id)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to delete keyboard", draft) },
        )
    }

    fun createPresetKey(id: String) {
        val draft = currentDraft() ?: return
        _state.value = runCatching {
            repository.createPresetKey(draft, id)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to create preset key", draft) },
        )
    }

    fun copyPresetKey(id: String) {
        val draft = currentDraft() ?: return
        _state.value = runCatching {
            repository.copyPresetKey(draft, id)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to copy preset key", draft) },
        )
    }

    fun deletePresetKey(id: String) {
        val draft = currentDraft() ?: return
        _state.value = runCatching {
            repository.deletePresetKey(draft, id)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to delete preset key", draft) },
        )
    }

    fun updatePresetKeyField(
        presetKeyId: String,
        fieldKey: String,
        value: String,
    ) {
        val draft = currentDraft() ?: return
        val field = ThemeFieldRegistry.presetKeyFields.firstOrNull { it.key == fieldKey } ?: return
        _state.value = runCatching {
            repository.updatePresetKeyField(draft, presetKeyId, field, value)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to update preset key", draft) },
        )
    }

    fun setPrimaryToolbarButton() {
        val draft = currentDraft() ?: return
        _state.value = runCatching {
            repository.setPrimaryToolbarButton(draft)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to create primary toolbar button", draft) },
        )
    }

    fun updatePrimaryToolbarButtonField(
        fieldKey: String,
        value: String,
    ) {
        val draft = currentDraft() ?: return
        val field = ThemeFieldRegistry.toolbarButtonFields.firstOrNull { it.key == fieldKey } ?: return
        _state.value = runCatching {
            repository.updatePrimaryToolbarButtonField(draft, field, value)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to update primary toolbar button", draft) },
        )
    }

    fun insertToolbarButton(index: Int) {
        val draft = currentDraft() ?: return
        _state.value = runCatching {
            repository.insertToolbarButton(draft, index)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to insert toolbar button", draft) },
        )
    }

    fun copyToolbarButton(index: Int) {
        val draft = currentDraft() ?: return
        _state.value = runCatching {
            repository.copyToolbarButton(draft, index)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to copy toolbar button", draft) },
        )
    }

    fun deleteToolbarButton(index: Int) {
        val draft = currentDraft() ?: return
        _state.value = runCatching {
            repository.deleteToolbarButton(draft, index)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to delete toolbar button", draft) },
        )
    }

    fun moveToolbarButton(
        fromIndex: Int,
        toIndex: Int,
    ) {
        val draft = currentDraft() ?: return
        _state.value = runCatching {
            repository.moveToolbarButton(draft, fromIndex, toIndex)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to move toolbar button", draft) },
        )
    }

    fun updateToolbarButtonField(
        index: Int,
        fieldKey: String,
        value: String,
    ) {
        val draft = currentDraft() ?: return
        val field = ThemeFieldRegistry.toolbarButtonFields.firstOrNull { it.key == fieldKey } ?: return
        _state.value = runCatching {
            repository.updateToolbarButtonField(draft, index, field, value)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to update toolbar button", draft) },
        )
    }

    fun createLiquidKeyboard(id: String) {
        val draft = currentDraft() ?: return
        _state.value = runCatching {
            repository.createLiquidKeyboard(draft, id)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to create liquid keyboard", draft) },
        )
    }

    fun copyLiquidKeyboard(id: String) {
        val draft = currentDraft() ?: return
        _state.value = runCatching {
            repository.copyLiquidKeyboard(draft, id)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to copy liquid keyboard", draft) },
        )
    }

    fun deleteLiquidKeyboard(id: String) {
        val draft = currentDraft() ?: return
        liquidKeyboardKeyIds.remove(id)
        _state.value = runCatching {
            repository.deleteLiquidKeyboard(draft, id)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to delete liquid keyboard", draft) },
        )
    }

    fun updateLiquidKeyboardField(
        id: String,
        fieldKey: String,
        value: String,
    ) {
        val draft = currentDraft() ?: return
        val field = ThemeFieldRegistry.liquidKeyboardFields.firstOrNull { it.key == fieldKey } ?: return
        _state.value = runCatching {
            repository.updateLiquidKeyboardField(draft, id, field, value)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to update liquid keyboard", draft) },
        )
    }

    fun insertLiquidKeyboardKey(
        id: String,
        index: Int,
    ) {
        val draft = currentDraft() ?: return
        ensureLiquidKeyIds(draft, id).add(index.coerceIn(0, ensureLiquidKeyIds(draft, id).size), newKeyId())
        updateDraft("Failed to insert liquid keyboard key") { repository.insertLiquidKeyboardKey(it, id, index) }
    }

    fun copyLiquidKeyboardKey(
        id: String,
        index: Int,
    ) {
        val draft = currentDraft() ?: return
        ensureLiquidKeyIds(draft, id).add(index + 1, newKeyId())
        updateDraft("Failed to copy liquid keyboard key") { repository.copyLiquidKeyboardKey(it, id, index) }
    }

    fun deleteLiquidKeyboardKey(
        id: String,
        index: Int,
    ) {
        val draft = currentDraft() ?: return
        ensureLiquidKeyIds(draft, id).removeAt(index)
        updateDraft("Failed to delete liquid keyboard key") { repository.deleteLiquidKeyboardKey(it, id, index) }
    }

    fun moveLiquidKeyboardKey(
        id: String,
        fromIndex: Int,
        toIndex: Int,
    ) {
        val draft = currentDraft() ?: return
        ensureLiquidKeyIds(draft, id).move(fromIndex, toIndex)
        updateDraft("Failed to move liquid keyboard key") { repository.moveLiquidKeyboardKey(it, id, fromIndex, toIndex) }
    }

    fun reorderLiquidKeyboardKeys(id: String, order: List<Int>) {
        val draft = currentDraft() ?: return
        val ids = ensureLiquidKeyIds(draft, id)
        require(order.size == ids.size && order.toSet().size == ids.size && order.all { it in ids.indices })
        liquidKeyboardKeyIds[id] = order.map { ids[it] }.toMutableList()
        persistKeyIds()
        updateDraft("Failed to reorder liquid keyboard keys") {
            repository.reorderLiquidKeyboardKeys(it, id, order)
        }
    }

    fun updateLiquidKeyboardKey(
        id: String,
        index: Int,
        action: String,
        label: String,
    ) = updateDraft("Failed to update liquid keyboard key") {
        repository.updateLiquidKeyboardKey(it, id, index, action, label)
    }

    fun updateKeyboardKeyField(
        keyboardId: String,
        keyIndex: Int,
        field: String,
        value: String,
    ) {
        val draft = currentDraft() ?: return
        _state.value = runCatching {
            repository.updateKeyboardKeyField(draft, keyboardId, keyIndex, field, value)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to update key", draft) },
        )
    }

    fun updateKeyboardField(
        keyboardId: String,
        fieldKey: String,
        value: String,
    ) {
        val draft = currentDraft() ?: return
        val field = ThemeFieldRegistry.keyboardFields.firstOrNull { it.key == fieldKey } ?: return
        _state.value = runCatching {
            repository.updateKeyboardField(draft, keyboardId, field, value)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to update keyboard", draft) },
        )
    }

    fun updateKeyboardKeyPopup(
        keyboardId: String,
        keyIndex: Int,
        popup: List<String>,
    ) {
        val draft = currentDraft() ?: return
        _state.value = runCatching {
            repository.updateKeyboardKeyPopup(draft, keyboardId, keyIndex, popup)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to update key popup", draft) },
        )
    }

    fun insertKeyboardKey(
        keyboardId: String,
        index: Int,
    ) {
        val draft = currentDraft() ?: return
        ensureKeyboardKeyIds(draft, keyboardId).add(
            index.coerceIn(0, ensureKeyboardKeyIds(draft, keyboardId).size),
            newKeyId(),
        )
        _state.value = runCatching {
            repository.insertKeyboardKey(draft, keyboardId, index)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to insert key", draft) },
        )
    }

    fun copyKeyboardKey(
        keyboardId: String,
        index: Int,
    ) {
        val draft = currentDraft() ?: return
        ensureKeyboardKeyIds(draft, keyboardId).add(index + 1, newKeyId())
        _state.value = runCatching {
            repository.copyKeyboardKey(draft, keyboardId, index)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to copy key", draft) },
        )
    }

    fun deleteKeyboardKey(
        keyboardId: String,
        index: Int,
    ) {
        val draft = currentDraft() ?: return
        ensureKeyboardKeyIds(draft, keyboardId).removeAt(index)
        _state.value = runCatching {
            repository.deleteKeyboardKey(draft, keyboardId, index)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to delete key", draft) },
        )
    }

    fun moveKeyboardKey(
        keyboardId: String,
        fromIndex: Int,
        toIndex: Int,
    ) {
        val draft = currentDraft() ?: return
        ensureKeyboardKeyIds(draft, keyboardId).move(fromIndex, toIndex)
        _state.value = runCatching {
            repository.moveKeyboardKey(draft, keyboardId, fromIndex, toIndex)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to move key", draft) },
        )
    }

    fun reorderKeyboardKeys(
        keyboardId: String,
        orderText: String,
    ) {
        val draft = currentDraft() ?: return
        val order = orderText.split(',')
            .mapNotNull { it.trim().toIntOrNull()?.minus(1) }
        val ids = ensureKeyboardKeyIds(draft, keyboardId)
        if (order.size == ids.size && order.toSet().size == ids.size && order.all { it in ids.indices }) {
            keyboardKeyIds[keyboardId] = order.map { ids[it] }.toMutableList()
        }
        _state.value = runCatching {
            repository.reorderKeyboardKeys(draft, keyboardId, orderText)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to reorder keys", draft) },
        )
    }

    fun resizeKeyboardKeys(
        keyboardId: String,
        width: String,
        height: String,
    ) {
        val draft = currentDraft() ?: return
        _state.value = runCatching {
            repository.resizeKeyboardKeys(draft, keyboardId, width, height)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to resize keys", draft) },
        )
    }

    fun updateColor(
        schemeId: String,
        colorKey: String,
        value: String,
    ) {
        val draft = currentDraft() ?: return
        _state.value = runCatching {
            repository.updateColor(draft, schemeId, colorKey, value)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to update color", draft) },
        )
    }

    fun createColorScheme(id: String) {
        val draft = currentDraft() ?: return
        _state.value = runCatching {
            repository.createColorScheme(draft, id)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to create color scheme", draft) },
        )
    }

    fun copyColorScheme(id: String) {
        val draft = currentDraft() ?: return
        _state.value = runCatching {
            repository.copyColorScheme(draft, id)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to copy color scheme", draft) },
        )
    }

    fun deleteColorScheme(id: String) {
        val draft = currentDraft() ?: return
        _state.value = runCatching {
            repository.deleteColorScheme(draft, id)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to delete color scheme", draft) },
        )
    }

    fun updateFallbackColor(
        colorKey: String,
        fallbackKey: String,
    ) {
        val draft = currentDraft() ?: return
        _state.value = runCatching {
            repository.updateFallbackColor(draft, colorKey, fallbackKey)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to update fallback color", draft) },
        )
    }

    fun updateSource(yamlText: String) {
        val draft = currentDraft() ?: return
        _state.value = runCatching {
            repository.updateSource(draft, yamlText)
        }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: "Failed to update source", draft) },
        )
    }

    fun keyboardKeyId(
        draft: ThemeDraft,
        keyboardId: String,
        index: Int,
    ): String = ensureKeyboardKeyIds(draft, keyboardId)[index]

    fun keyboardKeyIndex(
        draft: ThemeDraft,
        keyboardId: String,
        draftKeyId: String,
    ): Int = ensureKeyboardKeyIds(draft, keyboardId).indexOf(draftKeyId)

    fun liquidKeyboardKeyId(
        draft: ThemeDraft,
        keyboardId: String,
        index: Int,
    ): String = ensureLiquidKeyIds(draft, keyboardId)[index]

    fun liquidKeyboardKeyIndex(
        draft: ThemeDraft,
        keyboardId: String,
        draftKeyId: String,
    ): Int = ensureLiquidKeyIds(draft, keyboardId).indexOf(draftKeyId)

    private fun ensureKeyboardKeyIds(draft: ThemeDraft, keyboardId: String): MutableList<String> {
        val count = draft.theme.presetKeyboards[keyboardId]?.keys?.size ?: 0
        return keyboardKeyIds.getOrPut(keyboardId) { MutableList(count) { newKeyId() } }
            .also {
                it.resizeIds(count)
                persistKeyIds()
            }
    }

    private fun ensureLiquidKeyIds(draft: ThemeDraft, keyboardId: String): MutableList<String> {
        val count = draft.theme.liquidKeyboard.keyboards.firstOrNull { it.id == keyboardId }?.keys?.size ?: 0
        return liquidKeyboardKeyIds.getOrPut(keyboardId) { MutableList(count) { newKeyId() } }
            .also {
                it.resizeIds(count)
                persistKeyIds()
            }
    }

    private fun MutableList<String>.resizeIds(count: Int) {
        while (size < count) add(newKeyId())
        while (size > count) removeAt(lastIndex)
    }

    private fun MutableList<String>.move(fromIndex: Int, toIndex: Int) {
        add(toIndex, removeAt(fromIndex))
    }

    private fun newKeyId(): String = UUID.randomUUID().toString()

    private fun restoreKeyIds(key: String): MutableMap<String, MutableList<String>> = savedStateHandle.get<HashMap<String, ArrayList<String>>>(key)
        ?.mapValuesTo(mutableMapOf()) { it.value.toMutableList() }
        ?: mutableMapOf()

    private fun persistKeyIds() {
        savedStateHandle[KEYBOARD_KEY_IDS] = HashMap(
            keyboardKeyIds.mapValues { ArrayList(it.value) },
        )
        savedStateHandle[LIQUID_KEY_IDS] = HashMap(
            liquidKeyboardKeyIds.mapValues { ArrayList(it.value) },
        )
    }

    private fun currentDraft(): ThemeDraft? = when (val current = _state.value) {
        is State.Loaded -> current.draft
        is State.Error -> current.draft
        else -> null
    }

    private inline fun updateDraft(
        failureMessage: String,
        update: (ThemeDraft) -> ThemeDraft,
    ) {
        val draft = currentDraft() ?: return
        _state.value = runCatching { update(draft) }.fold(
            onSuccess = State::Loaded,
            onFailure = { State.Error(it.message ?: failureMessage, draft) },
        )
    }

    sealed interface State {
        data object Loading : State
        data class Loaded(val draft: ThemeDraft) : State
        data class Working(val draft: ThemeDraft, val message: String) : State
        data class Error(
            val message: String,
            val draft: ThemeDraft? = null,
            val canRestoreBackup: Boolean = false,
        ) : State
    }

    private companion object {
        const val KEYBOARD_KEY_IDS = "theme_designer_keyboard_key_ids"
        const val LIQUID_KEY_IDS = "theme_designer_liquid_key_ids"
    }
}
