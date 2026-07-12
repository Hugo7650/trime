/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.theme.designer

import android.app.AlertDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SearchView
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.R
import com.osfans.trime.data.theme.designer.ThemeColorResolver
import com.osfans.trime.data.theme.designer.ThemeDraft
import com.osfans.trime.data.theme.designer.ThemeFieldRegistry
import com.osfans.trime.data.theme.designer.ThemeYamlEditor
import com.osfans.trime.ui.common.PaddingPreferenceFragment
import com.osfans.trime.ui.main.NavigationRoute
import com.osfans.trime.ui.main.settings.ColorPickerDialog
import com.osfans.trime.util.addCategory
import com.osfans.trime.util.addPreference
import com.osfans.trime.util.yaml.boolean
import com.osfans.trime.util.yaml.mapping
import com.osfans.trime.util.yaml.sequence
import com.osfans.trime.util.yaml.string
import kotlinx.coroutines.launch
import java.util.Collections

abstract class ThemeDraftPreferenceFragment : PaddingPreferenceFragment() {
    protected val designerViewModel: ThemeDesignerViewModel by activityViewModels()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext())
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                designerViewModel.state.collect(::renderState)
            }
        }
    }

    protected fun renderState(state: ThemeDesignerViewModel.State) {
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext()).apply {
            when (state) {
                ThemeDesignerViewModel.State.Loading -> addPreference(R.string.loading)
                is ThemeDesignerViewModel.State.Working -> with(this) { renderDraft(state.draft) }
                is ThemeDesignerViewModel.State.Loaded -> with(this) { renderDraft(state.draft) }
                is ThemeDesignerViewModel.State.Error -> {
                    state.draft?.let { with(this) { renderDraft(it) } }
                    addPreference(R.string.failure, state.message)
                }
            }
        }
    }

    protected abstract fun PreferenceScreen.renderDraft(draft: ThemeDraft)

    protected fun androidx.preference.PreferenceGroup.addRow(
        title: String,
        summary: String? = null,
        onClick: () -> Unit,
    ) {
        addPreference(
            Preference(context).apply {
                this.title = title
                this.summary = summary
                isIconSpaceReserved = false
                setOnPreferenceClickListener {
                    onClick()
                    true
                }
            },
        )
    }

    protected fun showIdDialog(titleRes: Int, onConfirm: (String) -> Unit) {
        val input = EditText(requireContext()).apply { isSingleLine = true }
        AlertDialog.Builder(requireContext())
            .setTitle(titleRes)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                input.text.toString().trim().takeIf(String::isNotEmpty)?.let(onConfirm)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    protected fun confirm(titleRes: Int, message: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(requireContext())
            .setTitle(titleRes)
            .setMessage(message)
            .setPositiveButton(R.string.ok) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    protected fun showResourceSearch(
        title: String,
        values: List<String>,
        onSelect: (String) -> Unit,
    ) {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, values.toMutableList())
        val search = SearchView(requireContext()).apply {
            isIconified = false
            queryHint = title
        }
        val list = ListView(requireContext()).apply { this.adapter = adapter }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(search)
            addView(list)
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(content)
            .setNegativeButton(R.string.cancel, null)
            .create()
        search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false

            override fun onQueryTextChange(query: String?): Boolean {
                val filtered = values.filter { it.contains(query.orEmpty(), ignoreCase = true) }
                adapter.clear()
                adapter.addAll(filtered)
                return true
            }
        })
        list.setOnItemClickListener { _, _, position, _ ->
            adapter.getItem(position)?.let(onSelect)
            dialog.dismiss()
        }
        dialog.show()
    }

    protected fun androidx.preference.PreferenceGroup.addActionField(
        title: String,
        value: String,
        presetKeys: List<String>,
        onValue: (String) -> Unit,
    ) {
        addRow(title, value) {
            AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setItems(
                    arrayOf(
                        getString(R.string.theme_designer_search_preset_keys),
                        getString(R.string.theme_designer_free_text),
                    ),
                ) { _, which ->
                    if (which == 0) {
                        showResourceSearch(title, presetKeys, onValue)
                    } else {
                        val input = EditText(requireContext()).apply {
                            setText(value)
                            selectAll()
                        }
                        AlertDialog.Builder(requireContext())
                            .setTitle(title)
                            .setView(input)
                            .setPositiveButton(R.string.ok) { _, _ -> onValue(input.text.toString()) }
                            .setNegativeButton(R.string.cancel, null)
                            .show()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    protected fun androidx.preference.PreferenceGroup.addReferenceField(
        title: String,
        value: String,
        values: List<String>,
        onValue: (String) -> Unit,
    ) {
        addRow(title, value) {
            val choices = values + getString(R.string.theme_designer_free_text)
            AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setItems(choices.toTypedArray()) { _, which ->
                    if (which < values.size) {
                        onValue(values[which])
                    } else {
                        val input = EditText(requireContext()).apply {
                            setText(value)
                            selectAll()
                        }
                        AlertDialog.Builder(requireContext())
                            .setTitle(title)
                            .setView(input)
                            .setPositiveButton(R.string.ok) { _, _ -> onValue(input.text.toString()) }
                            .setNegativeButton(R.string.cancel, null)
                            .show()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    protected fun showReorderDialog(
        labels: List<String>,
        onConfirm: (List<Int>) -> Unit,
    ) {
        val items = labels.mapIndexed { index, label -> index to label }.toMutableList()
        class Holder(val text: TextView) : RecyclerView.ViewHolder(text)
        val adapter = object : RecyclerView.Adapter<Holder>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): Holder = Holder(
                TextView(parent.context).apply {
                    setPadding(48, 32, 48, 32)
                    textSize = 16f
                    layoutParams = RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT,
                        RecyclerView.LayoutParams.WRAP_CONTENT,
                    )
                },
            )

            override fun onBindViewHolder(holder: Holder, position: Int) {
                holder.text.text = items[position].second
            }

            override fun getItemCount(): Int = items.size
        }
        val list = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }
        ItemTouchHelper(
            object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder,
                ): Boolean {
                    val from = viewHolder.bindingAdapterPosition
                    val to = target.bindingAdapterPosition
                    if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                    Collections.swap(items, from, to)
                    adapter.notifyItemMoved(from, to)
                    return true
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
            },
        ).attachToRecyclerView(list)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.theme_designer_reorder_keyboard_keys)
            .setView(list)
            .setPositiveButton(R.string.ok) { _, _ -> onConfirm(items.map { it.first }) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}

class ThemeKeyboardHubFragment : ThemeDraftPreferenceFragment() {
    override fun PreferenceScreen.renderDraft(draft: ThemeDraft) {
        addCategory(R.string.theme_designer_keyboard_layouts) {
            addRow(
                getString(R.string.theme_designer_keyboard_layouts),
                draft.theme.presetKeyboards.size.toString(),
            ) { findNavController().navigate(NavigationRoute.ThemeKeyboardList) }
            addRow(
                getString(R.string.theme_designer_preset_keys),
                draft.theme.presetKeys.size.toString(),
            ) { findNavController().navigate(NavigationRoute.ThemePresetKeyList) }
        }
    }
}

class ThemeKeyboardListFragment : ThemeDraftPreferenceFragment() {
    override fun PreferenceScreen.renderDraft(draft: ThemeDraft) {
        addCategory(R.string.theme_designer_keyboard_layouts) {
            addRow(getString(R.string.theme_designer_search_keyboards)) {
                showResourceSearch(
                    getString(R.string.theme_designer_search_keyboards),
                    draft.theme.presetKeyboards.keys.sorted(),
                ) { findNavController().navigate(NavigationRoute.ThemeKeyboardEditor(it)) }
            }
            addRow(getString(R.string.theme_designer_new_keyboard)) {
                showIdDialog(R.string.theme_designer_new_keyboard, designerViewModel::createKeyboard)
            }
            draft.theme.presetKeyboards.forEach { (id, keyboard) ->
                addRow(id, getString(R.string.theme_designer_key_count, keyboard.keys.size)) {
                    findNavController().navigate(NavigationRoute.ThemeKeyboardEditor(id))
                }
            }
        }
    }
}

class ThemePresetKeyListFragment : ThemeDraftPreferenceFragment() {
    override fun PreferenceScreen.renderDraft(draft: ThemeDraft) {
        addCategory(R.string.theme_designer_preset_keys) {
            addRow(getString(R.string.theme_designer_search_preset_keys)) {
                showResourceSearch(
                    getString(R.string.theme_designer_search_preset_keys),
                    draft.theme.presetKeys.keys.sorted(),
                ) { findNavController().navigate(NavigationRoute.ThemePresetKeyEditor(it)) }
            }
            addRow(getString(R.string.theme_designer_new_preset_key)) {
                showIdDialog(R.string.theme_designer_new_preset_key, designerViewModel::createPresetKey)
            }
            draft.theme.presetKeys.forEach { (id, key) ->
                addRow(id, key.label.ifEmpty { key.send }) {
                    findNavController().navigate(NavigationRoute.ThemePresetKeyEditor(id))
                }
            }
        }
    }
}

class ThemeKeyboardEditorFragment : ThemeDraftPreferenceFragment() {
    private val keyboardId by lazy { requireArguments().getString("keyboardId").orEmpty() }

    override fun PreferenceScreen.renderDraft(draft: ThemeDraft) {
        val keyboard = draft.theme.presetKeyboards[keyboardId] ?: return
        requireActivity().title = keyboardId
        addCategory(keyboardId) {
            addRow(getString(R.string.theme_designer_copy_keyboard)) {
                designerViewModel.copyKeyboard(keyboardId)
            }
            addRow(getString(R.string.theme_designer_delete_keyboard)) {
                confirm(
                    R.string.theme_designer_delete_keyboard,
                    getString(R.string.theme_designer_delete_keyboard_message, keyboardId),
                ) {
                    designerViewModel.deleteKeyboard(keyboardId)
                    findNavController().popBackStack()
                }
            }
            addRow(
                getString(R.string.theme_designer_keyboard_keys),
                getString(R.string.theme_designer_key_count, keyboard.keys.size),
            ) {
                findNavController().navigate(NavigationRoute.ThemeKeyboardKeyList(keyboardId))
            }
            addRow(getString(R.string.theme_designer_preview)) {
                findNavController().navigate(NavigationRoute.ThemeKeyboardPreview(keyboardId))
            }
            ThemeFieldRegistry.keyboardFields.forEach { field ->
                val value = yamlValue(
                    draft,
                    listOf("preset_keyboards", keyboardId) + field.path,
                    field.type,
                )
                if (field.key == "ascii_keyboard" || field.key == "landscape_keyboard") {
                    addReferenceField(
                        field.title,
                        value,
                        draft.theme.presetKeyboards.keys.sorted(),
                    ) {
                        designerViewModel.updateKeyboardField(keyboardId, field.key, it)
                    }
                } else {
                    addEditableField(field, value) {
                        designerViewModel.updateKeyboardField(keyboardId, field.key, it)
                    }
                }
            }
        }
    }
}

class ThemeKeyboardPreviewFragment : ThemeDraftPreferenceFragment() {
    private val keyboardId by lazy { requireArguments().getString("keyboardId").orEmpty() }
    private var previewState = KeyboardPreviewState()

    override fun PreferenceScreen.renderDraft(draft: ThemeDraft) {
        val keyboard = draft.theme.presetKeyboards[keyboardId] ?: return
        requireActivity().title = "$keyboardId / ${getString(R.string.theme_designer_preview)}"
        addRow(getString(R.string.theme_designer_preview_change_state)) {
            previewState = previewState.next()
            renderState(designerViewModel.state.value)
        }
        addPreference(
            KeyboardPreviewPreference(
                context = context,
                keyboardId = keyboardId,
                theme = draft.theme,
                keyboard = keyboard,
                colors = ThemeColorResolver(draft.theme),
                state = previewState,
                onToggleState = {
                    previewState = previewState.next()
                    renderState(designerViewModel.state.value)
                },
                onKeyClick = { index ->
                    findNavController().navigate(
                        NavigationRoute.ThemeKeyboardKeyEditor(
                            keyboardId,
                            designerViewModel.keyboardKeyId(draft, keyboardId, index),
                        ),
                    )
                },
            ),
        )
    }
}

class ThemeKeyboardKeyListFragment : ThemeDraftPreferenceFragment() {
    private val keyboardId by lazy { requireArguments().getString("keyboardId").orEmpty() }

    override fun PreferenceScreen.renderDraft(draft: ThemeDraft) {
        val keyboard = draft.theme.presetKeyboards[keyboardId] ?: return
        requireActivity().title = "$keyboardId / ${getString(R.string.theme_designer_keyboard_keys)}"
        addCategory(R.string.theme_designer_keyboard_keys) {
            addRow(getString(R.string.theme_designer_reorder_keyboard_keys)) {
                showReorderDialog(
                    keyboard.keys.mapIndexed { index, key ->
                        "${index + 1}. ${key.label.ifEmpty { key.click }}"
                    },
                ) { order ->
                    designerViewModel.reorderKeyboardKeys(
                        keyboardId,
                        order.joinToString(", ") { (it + 1).toString() },
                    )
                }
            }
            addRow(getString(R.string.theme_designer_insert_key_before)) {
                designerViewModel.insertKeyboardKey(keyboardId, keyboard.keys.size)
            }
            keyboard.keys.forEachIndexed { index, key ->
                val summary = listOf(key.label, key.click).filter(String::isNotEmpty).joinToString(" / ")
                addRow("${index + 1}", summary) {
                    findNavController().navigate(
                        NavigationRoute.ThemeKeyboardKeyEditor(
                            keyboardId,
                            designerViewModel.keyboardKeyId(draft, keyboardId, index),
                        ),
                    )
                }
            }
        }
    }
}

class ThemeKeyboardKeyEditorFragment : ThemeDraftPreferenceFragment() {
    private val keyboardId by lazy { requireArguments().getString("keyboardId").orEmpty() }
    private val draftKeyId by lazy { requireArguments().getString("draftKeyId").orEmpty() }

    override fun PreferenceScreen.renderDraft(draft: ThemeDraft) {
        val keyIndex = designerViewModel.keyboardKeyIndex(draft, keyboardId, draftKeyId)
        if (keyIndex < 0) return
        requireActivity().title = "$keyboardId / ${keyIndex + 1}"
        val key = draft.theme.presetKeyboards[keyboardId]?.keys?.getOrNull(keyIndex) ?: return
        addCategory(R.string.theme_designer_keyboard_key) {
            if (keyIndex > 0) {
                addRow(getString(R.string.theme_designer_move_key_up)) {
                    designerViewModel.moveKeyboardKey(keyboardId, keyIndex, keyIndex - 1)
                    findNavController().popBackStack()
                }
            }
            if (keyIndex < (draft.theme.presetKeyboards[keyboardId]?.keys?.lastIndex ?: -1)) {
                addRow(getString(R.string.theme_designer_move_key_down)) {
                    designerViewModel.moveKeyboardKey(keyboardId, keyIndex, keyIndex + 1)
                    findNavController().popBackStack()
                }
            }
            addRow(getString(R.string.theme_designer_copy_key)) {
                designerViewModel.copyKeyboardKey(keyboardId, keyIndex)
            }
            addRow(getString(R.string.theme_designer_delete_key)) {
                designerViewModel.deleteKeyboardKey(keyboardId, keyIndex)
                findNavController().popBackStack()
            }
            ThemeFieldRegistry.keyFields.forEach { field ->
                val keyNode = ThemeYamlEditor.valueAt(
                    draft.yamlNode,
                    listOf("preset_keyboards", keyboardId, "keys"),
                )?.sequence?.getOrNull(keyIndex)
                val value = yamlNodeValue(nodeValueAt(keyNode, field.path), field.type)
                if (field.key == "click") {
                    addActionField(
                        field.title,
                        value,
                        draft.theme.presetKeys.keys.sorted(),
                    ) {
                        designerViewModel.updateKeyboardKeyField(keyboardId, keyIndex, field.key, it)
                    }
                } else {
                    addEditableField(field, value) {
                        designerViewModel.updateKeyboardKeyField(keyboardId, keyIndex, field.key, it)
                    }
                }
            }
            ThemeFieldRegistry.keyActionBehaviors.forEach { behavior ->
                val field = behavior.name.lowercase()
                addActionField(
                    field,
                    key.behaviors[behavior].orEmpty(),
                    draft.theme.presetKeys.keys.sorted(),
                ) {
                    designerViewModel.updateKeyboardKeyField(keyboardId, keyIndex, field, it)
                }
            }
            addTextField("popup", key.popup.joinToString("\n")) {
                designerViewModel.updateKeyboardKeyPopup(
                    keyboardId,
                    keyIndex,
                    it.lines().filter(String::isNotBlank),
                )
            }
        }
    }
}

class ThemePresetKeyEditorFragment : ThemeDraftPreferenceFragment() {
    private val presetKeyId by lazy { requireArguments().getString("presetKeyId").orEmpty() }

    override fun PreferenceScreen.renderDraft(draft: ThemeDraft) {
        if (presetKeyId !in draft.theme.presetKeys) return
        requireActivity().title = presetKeyId
        addCategory(R.string.theme_designer_preset_keys) {
            addRow(getString(R.string.theme_designer_copy_preset_key)) {
                designerViewModel.copyPresetKey(presetKeyId)
            }
            addRow(getString(R.string.theme_designer_delete_preset_key)) {
                confirm(
                    R.string.theme_designer_delete_preset_key,
                    getString(R.string.theme_designer_delete_preset_key_message, presetKeyId),
                ) {
                    designerViewModel.deletePresetKey(presetKeyId)
                    findNavController().popBackStack()
                }
            }
            ThemeFieldRegistry.presetKeyFields.forEach { field ->
                val value = yamlValue(
                    draft,
                    listOf("preset_keys", presetKeyId) + field.path,
                    field.type,
                )
                if (field.key == "select") {
                    addReferenceField(
                        field.title,
                        value,
                        draft.theme.presetKeyboards.keys.sorted(),
                    ) {
                        designerViewModel.updatePresetKeyField(presetKeyId, field.key, it)
                    }
                } else {
                    addEditableField(field, value) {
                        designerViewModel.updatePresetKeyField(presetKeyId, field.key, it)
                    }
                }
            }
        }
    }
}

private fun androidx.preference.PreferenceGroup.addEditableField(
    field: ThemeFieldRegistry.Field,
    value: String,
    onValue: (String) -> Unit,
) {
    val preference = if (field.key.endsWith("_color") || field.key.endsWith("_background")) {
        Preference(context).apply {
            setOnPreferenceClickListener {
                ColorPickerDialog.buildColorValue(
                    context = context,
                    title = field.title,
                    value = value,
                    onConfirm = onValue,
                ).show()
                true
            }
        }
    } else {
        when (field.type) {
            ThemeFieldRegistry.Type.BOOLEAN -> SwitchPreferenceCompat(context).apply {
                isChecked = value.toBooleanStrictOrNull() ?: false
                setOnPreferenceChangeListener { _, newValue ->
                    onValue(newValue.toString())
                    true
                }
            }
            ThemeFieldRegistry.Type.ENUM -> ListPreference(context).apply {
                entries = field.enumValues.toTypedArray()
                entryValues = field.enumValues.toTypedArray()
                this.value = value.ifEmpty { field.defaultValue }
                setOnPreferenceChangeListener { _, newValue ->
                    onValue(newValue.toString())
                    true
                }
            }
            else -> EditTextPreference(context).apply {
                text = value
                setOnPreferenceChangeListener { _, newValue ->
                    onValue(newValue.toString())
                    true
                }
            }
        }
    }
    preference.title = field.title
    preference.key = "theme_designer:layered:${field.path.joinToString("/")}"
    preference.summary = value.ifEmpty { field.defaultValue }
    preference.isIconSpaceReserved = false
    addPreference(preference)
}

private fun androidx.preference.PreferenceGroup.addTextField(
    title: String,
    value: String,
    onValue: (String) -> Unit,
) {
    addPreference(
        EditTextPreference(context).apply {
            this.title = title
            key = "theme_designer:layered:text:$title"
            text = value
            summary = value
            isIconSpaceReserved = false
            setOnPreferenceChangeListener { _, newValue ->
                onValue(newValue.toString())
                true
            }
        },
    )
}

private fun yamlValue(
    draft: ThemeDraft,
    path: List<String>,
    type: ThemeFieldRegistry.Type,
): String = yamlNodeValue(ThemeYamlEditor.valueAt(draft.yamlNode, path), type)

private fun yamlNodeValue(
    node: com.osfans.trime.util.yaml.Node?,
    type: ThemeFieldRegistry.Type,
): String = when (type) {
    ThemeFieldRegistry.Type.STRING_LIST -> node?.sequence?.mapNotNull { it.string }?.joinToString(", ")
        ?: node?.string.orEmpty()
    ThemeFieldRegistry.Type.BOOLEAN -> node?.boolean?.toString() ?: node?.string.orEmpty()
    else -> node?.string.orEmpty()
}

private fun nodeValueAt(
    node: com.osfans.trime.util.yaml.Node?,
    path: List<String>,
): com.osfans.trime.util.yaml.Node? = path.fold(node) { current, key ->
    current?.mapping?.get(key)
}

class ThemeLiquidKeyboardListFragment : ThemeDraftPreferenceFragment() {
    override fun PreferenceScreen.renderDraft(draft: ThemeDraft) {
        addCategory(R.string.theme_designer_liquid_keyboard) {
            addRow(getString(R.string.theme_designer_search_liquid_keyboards)) {
                showResourceSearch(
                    getString(R.string.theme_designer_search_liquid_keyboards),
                    draft.theme.liquidKeyboard.keyboards.map { it.id }.sorted(),
                ) { findNavController().navigate(NavigationRoute.ThemeLiquidKeyboardEditor(it)) }
            }
            addRow(getString(R.string.theme_designer_new_liquid_keyboard)) {
                showIdDialog(
                    R.string.theme_designer_new_liquid_keyboard,
                    designerViewModel::createLiquidKeyboard,
                )
            }
            draft.theme.liquidKeyboard.keyboards.forEach { keyboard ->
                addRow(
                    keyboard.id,
                    getString(R.string.theme_designer_key_count, keyboard.keys.size),
                ) {
                    findNavController().navigate(
                        NavigationRoute.ThemeLiquidKeyboardEditor(keyboard.id),
                    )
                }
            }
        }
    }
}

class ThemeLiquidKeyboardHubFragment : ThemeDraftPreferenceFragment() {
    override fun PreferenceScreen.renderDraft(draft: ThemeDraft) {
        addCategory(R.string.theme_designer_liquid_keyboard) {
            addRow(getString(R.string.theme_designer_liquid_keyboard_global)) {
                findNavController().navigate(NavigationRoute.ThemeLiquidKeyboardGlobal)
            }
            addRow(
                getString(R.string.theme_designer_liquid_keyboard),
                draft.theme.liquidKeyboard.keyboards.size.toString(),
            ) {
                findNavController().navigate(NavigationRoute.ThemeLiquidKeyboardList)
            }
        }
    }
}

class ThemeLiquidKeyboardGlobalFragment : ThemeDraftPreferenceFragment() {
    override fun PreferenceScreen.renderDraft(draft: ThemeDraft) {
        addCategory(R.string.theme_designer_liquid_keyboard_global) {
            ThemeFieldRegistry.fieldsByModule[ThemeFieldRegistry.Module.LIQUID_KEYBOARD]
                .orEmpty()
                .filterNot { it.key == "liquid_keyboard/keyboards" }
                .forEach { field ->
                    val value = yamlValue(draft, field.path, field.type)
                    addEditableField(field, value) {
                        designerViewModel.updateField(field.key, it)
                    }
                }
        }
    }
}

class ThemeLiquidKeyboardEditorFragment : ThemeDraftPreferenceFragment() {
    private val keyboardId by lazy { requireArguments().getString("keyboardId").orEmpty() }

    override fun PreferenceScreen.renderDraft(draft: ThemeDraft) {
        val keyboard = draft.theme.liquidKeyboard.keyboards.firstOrNull { it.id == keyboardId } ?: return
        requireActivity().title = keyboardId
        addCategory(keyboard.name) {
            addRow(getString(R.string.theme_designer_copy_liquid_keyboard)) {
                designerViewModel.copyLiquidKeyboard(keyboardId)
            }
            addRow(getString(R.string.theme_designer_delete_liquid_keyboard)) {
                confirm(
                    R.string.theme_designer_delete_liquid_keyboard,
                    getString(R.string.theme_designer_delete_liquid_keyboard_message, keyboardId),
                ) {
                    designerViewModel.deleteLiquidKeyboard(keyboardId)
                    findNavController().popBackStack()
                }
            }
            addRow(
                getString(R.string.theme_designer_keyboard_keys),
                getString(R.string.theme_designer_key_count, keyboard.keys.size),
            ) {
                findNavController().navigate(
                    NavigationRoute.ThemeLiquidKeyboardKeyList(keyboardId),
                )
            }
            ThemeFieldRegistry.liquidKeyboardFields
                .filterNot { it.key == "keys" }
                .forEach { field ->
                    val value = yamlValue(
                        draft,
                        listOf("liquid_keyboard", keyboardId) + field.path,
                        field.type,
                    )
                    addEditableField(field, value) {
                        designerViewModel.updateLiquidKeyboardField(keyboardId, field.key, it)
                    }
                }
        }
        addPreference(LiquidKeyboardPreviewPreference(context, keyboard))
    }
}

class ThemeLiquidKeyboardKeyListFragment : ThemeDraftPreferenceFragment() {
    private val keyboardId by lazy { requireArguments().getString("keyboardId").orEmpty() }

    override fun PreferenceScreen.renderDraft(draft: ThemeDraft) {
        val keyboard = draft.theme.liquidKeyboard.keyboards.firstOrNull { it.id == keyboardId } ?: return
        requireActivity().title = "$keyboardId / ${getString(R.string.theme_designer_keyboard_keys)}"
        addCategory(R.string.theme_designer_keyboard_keys) {
            addRow(getString(R.string.theme_designer_reorder_keyboard_keys)) {
                showReorderDialog(
                    keyboard.keys.mapIndexed { index, key ->
                        "${index + 1}. ${key.altText.ifEmpty { key.text }}"
                    },
                ) { designerViewModel.reorderLiquidKeyboardKeys(keyboardId, it) }
            }
            addRow(getString(R.string.theme_designer_new_liquid_keyboard_key)) {
                designerViewModel.insertLiquidKeyboardKey(keyboardId, keyboard.keys.size)
            }
            keyboard.keys.forEachIndexed { index, key ->
                addRow("${index + 1}", listOf(key.altText, key.text).distinct().joinToString(" / ")) {
                    findNavController().navigate(
                        NavigationRoute.ThemeLiquidKeyboardKeyEditor(
                            keyboardId,
                            designerViewModel.liquidKeyboardKeyId(draft, keyboardId, index),
                        ),
                    )
                }
            }
        }
    }
}

class ThemeLiquidKeyboardKeyEditorFragment : ThemeDraftPreferenceFragment() {
    private val keyboardId by lazy { requireArguments().getString("keyboardId").orEmpty() }
    private val draftKeyId by lazy { requireArguments().getString("draftKeyId").orEmpty() }

    override fun PreferenceScreen.renderDraft(draft: ThemeDraft) {
        val keyIndex = designerViewModel.liquidKeyboardKeyIndex(draft, keyboardId, draftKeyId)
        if (keyIndex < 0) return
        requireActivity().title = "$keyboardId / ${keyIndex + 1}"
        val key = draft.theme.liquidKeyboard.keyboards
            .firstOrNull { it.id == keyboardId }
            ?.keys
            ?.getOrNull(keyIndex) ?: return
        addCategory(R.string.theme_designer_keyboard_key) {
            val lastIndex = draft.theme.liquidKeyboard.keyboards
                .firstOrNull { it.id == keyboardId }
                ?.keys
                ?.lastIndex ?: -1
            if (keyIndex > 0) {
                addRow(getString(R.string.theme_designer_move_key_up)) {
                    designerViewModel.moveLiquidKeyboardKey(keyboardId, keyIndex, keyIndex - 1)
                    findNavController().popBackStack()
                }
            }
            if (keyIndex < lastIndex) {
                addRow(getString(R.string.theme_designer_move_key_down)) {
                    designerViewModel.moveLiquidKeyboardKey(keyboardId, keyIndex, keyIndex + 1)
                    findNavController().popBackStack()
                }
            }
            addRow(getString(R.string.theme_designer_copy_key)) {
                designerViewModel.copyLiquidKeyboardKey(keyboardId, keyIndex)
            }
            addRow(getString(R.string.theme_designer_delete_key)) {
                designerViewModel.deleteLiquidKeyboardKey(keyboardId, keyIndex)
                findNavController().popBackStack()
            }
            addActionField(
                getString(R.string.theme_designer_liquid_keyboard_key_action),
                key.text,
                draft.theme.presetKeys.keys.sorted(),
            ) {
                designerViewModel.updateLiquidKeyboardKey(keyboardId, keyIndex, it, key.altText)
            }
            addTextField(getString(R.string.theme_designer_liquid_keyboard_key_label), key.altText) {
                designerViewModel.updateLiquidKeyboardKey(keyboardId, keyIndex, key.text, it)
            }
        }
    }
}
