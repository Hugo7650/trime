/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.theme.designer

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
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
import androidx.preference.SwitchPreferenceCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.R
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.theme.designer.ThemeColorFieldRegistry
import com.osfans.trime.data.theme.designer.ThemeColorResolver
import com.osfans.trime.data.theme.designer.ThemeDraft
import com.osfans.trime.data.theme.designer.ThemeFieldRegistry
import com.osfans.trime.data.theme.designer.ThemeKeyboardReferences
import com.osfans.trime.data.theme.designer.ThemeReferenceScanner
import com.osfans.trime.data.theme.designer.ThemeUnknownFieldScanner
import com.osfans.trime.data.theme.designer.ThemeValidationResult
import com.osfans.trime.data.theme.designer.ThemeYamlEditor
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.data.theme.model.ToolBar
import com.osfans.trime.ui.common.PaddingPreferenceFragment
import com.osfans.trime.ui.main.MainViewModel
import com.osfans.trime.ui.main.NavigationRoute
import com.osfans.trime.ui.main.settings.ColorPickerDialog
import com.osfans.trime.util.addCategory
import com.osfans.trime.util.addPreference
import com.osfans.trime.util.yaml.boolean
import com.osfans.trime.util.yaml.mapping
import com.osfans.trime.util.yaml.sequence
import com.osfans.trime.util.yaml.string
import kotlinx.coroutines.launch
import splitties.dimensions.dp
import java.util.Collections

class ThemeDesignerFragment : PaddingPreferenceFragment() {
    private val viewModel: ThemeDesignerViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private var previewState = KeyboardPreviewState()
    private var activeSection = DesignerSection.OVERVIEW
    private var keyboardDetail: KeyboardDetail? = null
    private var isDesignerRoot = true

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        isDesignerRoot = !requireArguments().containsKey("section")
        activeSection = requireArguments().getString("section")
            ?.let { runCatching { DesignerSection.valueOf(it) }.getOrNull() }
            ?: DesignerSection.OVERVIEW
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext())
    }

    override fun onViewCreated(
        view: android.view.View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        if (isDesignerRoot && savedInstanceState == null) {
            viewModel.load()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().title = getString(
            if (activeSection == DesignerSection.OVERVIEW) {
                R.string.theme_designer
            } else {
                activeSection.titleRes
            },
        )
        mainViewModel.disableTopOptionsMenu()
    }

    private fun render(state: ThemeDesignerViewModel.State) {
        val context = preferenceManager.context
        preferenceScreen = preferenceManager.createPreferenceScreen(context).apply {
            when (state) {
                ThemeDesignerViewModel.State.Loading -> {
                    addPreference(R.string.loading)
                }
                is ThemeDesignerViewModel.State.Error -> {
                    addPreference(R.string.failure, state.message)
                    addPreference(R.string.theme_designer_reload) {
                        viewModel.load()
                    }
                    if (state.draft == null && state.canRestoreBackup) {
                        addPreference(R.string.theme_designer_restore_backup) {
                            viewModel.restoreBackup()
                        }
                    }
                    state.draft?.let { addDraftContent(it) }
                }
                is ThemeDesignerViewModel.State.Working -> {
                    addDraftContent(state.draft)
                    addPreference(state.message)
                }
                is ThemeDesignerViewModel.State.Loaded -> {
                    addDraftContent(state.draft)
                }
            }
        }
    }

    private fun androidx.preference.PreferenceScreen.addDraftContent(draft: ThemeDraft) {
        if (activeSection == DesignerSection.OVERVIEW) {
            addModules(draft)
            addActions(draft)
        }
        when (activeSection) {
            DesignerSection.OVERVIEW -> addOverview(draft)
            DesignerSection.KEYBOARD_LAYOUTS -> addFieldSection(
                R.string.theme_designer_keyboard_layouts,
                draft,
                ThemeFieldRegistry.Module.KEYBOARD_LAYOUTS,
            )
            DesignerSection.STYLE -> addFieldSection(
                R.string.theme_designer_style,
                draft,
                ThemeFieldRegistry.Module.STYLE,
            )
            DesignerSection.COLORS -> addColorSection(draft)
            DesignerSection.PREEDIT_WINDOW -> addFieldSection(
                R.string.theme_designer_preedit_window,
                draft,
                ThemeFieldRegistry.Module.PREEDIT_WINDOW,
            )
            DesignerSection.TOOL_BAR -> addFieldSection(
                R.string.theme_designer_toolbar,
                draft,
                ThemeFieldRegistry.Module.TOOL_BAR,
            )
            DesignerSection.LIQUID_KEYBOARD -> addFieldSection(
                R.string.theme_designer_liquid_keyboard,
                draft,
                ThemeFieldRegistry.Module.LIQUID_KEYBOARD,
            )
            DesignerSection.SOURCE -> addSourceSection(draft)
        }
        addValidation(draft)
        if (activeSection != DesignerSection.OVERVIEW) {
            addActions(draft)
        }
    }

    private fun androidx.preference.PreferenceScreen.addOverview(draft: ThemeDraft) {
        addCategory(R.string.theme_designer_overview) {
            addPreference(
                title = draft.theme.name,
                summary = "${draft.derivedThemeId}\n${draft.file.absolutePath}",
            )
            addPreference(
                title = getString(R.string.theme_designer_status),
                summary = draft.statusSummary(),
            )
            ThemeFieldRegistry.fieldsByModule[ThemeFieldRegistry.Module.OVERVIEW].orEmpty().forEach {
                addFieldPreference(draft, it)
            }
            addPreference(
                title = getString(R.string.theme_designer_source_theme),
                summary = draft.sourceThemeId,
            )
        }
    }

    private fun androidx.preference.PreferenceScreen.addActions(draft: ThemeDraft) {
        addCategory(R.string.theme_designer_actions) {
            addPreference(R.string.theme_designer_save_draft) {
                viewModel.save()
            }
            addPreference(
                Preference(context).apply {
                    title = getString(R.string.theme_designer_deploy_draft)
                    summary = if (draft.validation.hasErrors) {
                        getString(R.string.theme_designer_deploy_blocked)
                    } else {
                        null
                    }
                    isEnabled = !draft.validation.hasErrors
                    isIconSpaceReserved = false
                    setOnPreferenceClickListener {
                        viewModel.deploy()
                        true
                    }
                },
            )
        }
    }

    private fun androidx.preference.PreferenceScreen.addModules(draft: ThemeDraft) {
        addCategory(R.string.theme_designer_modules) {
            addModulePreference(
                DesignerSection.OVERVIEW,
                summary = draft.statusSummary(),
            )
            addModulePreference(
                DesignerSection.KEYBOARD_LAYOUTS,
                summary = getString(
                    R.string.theme_designer_keyboard_layouts_summary,
                    draft.theme.presetKeyboards.size,
                    draft.theme.presetKeys.size,
                ),
            )
            addModulePreference(
                DesignerSection.STYLE,
                summary = "${draft.theme.generalStyle.keyWidth} / ${draft.theme.generalStyle.keyHeight}",
            )
            addModulePreference(
                DesignerSection.COLORS,
                summary = getString(
                    R.string.theme_designer_colors_summary,
                    draft.theme.colorSchemes.size,
                    draft.theme.fallbackColors.size,
                ),
            )
            addModulePreference(
                DesignerSection.PREEDIT_WINDOW,
                summary = "${draft.theme.generalStyle.candidateViewHeight} / ${draft.theme.preedit.alpha}",
            )
            addModulePreference(
                DesignerSection.TOOL_BAR,
                summary = "${draft.theme.toolBar.buttons.size} / ${draft.theme.toolBar.buttonSpacing}",
            )
            addModulePreference(
                DesignerSection.LIQUID_KEYBOARD,
                summary = draft.theme.liquidKeyboard.keyboards.size.toString(),
            )
            addModulePreference(
                DesignerSection.SOURCE,
                summary = sourceSummary(draft),
            )
        }
    }

    private fun androidx.preference.PreferenceGroup.addModulePreference(
        section: DesignerSection,
        summary: String,
    ) {
        val modulePreference = Preference(context).apply {
            title = getString(section.titleRes)
            this.summary = summary
            isIconSpaceReserved = activeSection == section
            if (activeSection == section) {
                icon = context.getDrawable(android.R.drawable.checkbox_on_background)
                isSelectable = false
            } else {
                setOnPreferenceClickListener {
                    when (section) {
                        DesignerSection.KEYBOARD_LAYOUTS ->
                            findNavController().navigate(NavigationRoute.ThemeKeyboardHub)
                        DesignerSection.LIQUID_KEYBOARD ->
                            findNavController().navigate(NavigationRoute.ThemeLiquidKeyboardHub)
                        else -> {
                            findNavController().navigate(
                                NavigationRoute.ThemeDesignerModule(section.name),
                            )
                        }
                    }
                    true
                }
            }
        }
        addPreference(modulePreference)
    }

    private fun androidx.preference.PreferenceScreen.addFieldSection(
        title: Int,
        draft: ThemeDraft,
        module: ThemeFieldRegistry.Module,
    ) {
        val fields = ThemeFieldRegistry.fieldsByModule[module].orEmpty()
        if (fields.isEmpty()) return
        addCategory(title) {
            if (module == ThemeFieldRegistry.Module.KEYBOARD_LAYOUTS) {
                addKeyboardLayoutContent(draft)
            }
            if (module == ThemeFieldRegistry.Module.TOOL_BAR) {
                addToolbarEditor(draft)
            }
            if (module == ThemeFieldRegistry.Module.LIQUID_KEYBOARD) {
                addLiquidKeyboardEditor(draft)
            }
            fields.forEach {
                addFieldPreference(draft, it)
            }
        }
    }

    private fun androidx.preference.PreferenceGroup.addKeyboardLayoutContent(draft: ThemeDraft) {
        when (val detail = keyboardDetail) {
            null -> {
                addPreference(R.string.theme_designer_new_keyboard) { showCreateKeyboardDialog() }
                addPreference(R.string.theme_designer_new_preset_key) { showCreatePresetKeyDialog() }
                draft.theme.presetKeyboards.forEach { (id, keyboard) ->
                    addPreference(
                        title = id,
                        summary = "${keyboard.keys.size} keys",
                    ) {
                        keyboardDetail = KeyboardDetail.Keyboard(id)
                        render(viewModel.state.value)
                    }
                }
                draft.theme.presetKeys.forEach { (id, presetKey) ->
                    addPreference(
                        title = getString(R.string.theme_designer_preset_key_item, id),
                        summary = presetKey.label.ifEmpty { presetKey.send },
                    ) {
                        keyboardDetail = KeyboardDetail.PresetKey(id)
                        render(viewModel.state.value)
                    }
                }
            }
            is KeyboardDetail.Keyboard -> {
                addPreference(android.R.string.cancel) {
                    keyboardDetail = null
                    render(viewModel.state.value)
                }
                val keyboard = draft.theme.presetKeyboards[detail.id]
                if (keyboard == null) {
                    keyboardDetail = null
                    return
                }
                addKeyboardEditor(draft, detail.id, keyboard)
                addPreference(
                    KeyboardPreviewPreference(
                        context = context,
                        keyboardId = detail.id,
                        theme = draft.theme,
                        keyboard = keyboard,
                        colors = ThemeColorResolver(draft.theme),
                        state = previewState,
                        onToggleState = {
                            previewState = previewState.next()
                            render(viewModel.state.value)
                        },
                    ),
                )
            }
            is KeyboardDetail.PresetKey -> {
                addPreference(android.R.string.cancel) {
                    keyboardDetail = null
                    render(viewModel.state.value)
                }
                val presetKey = draft.theme.presetKeys[detail.id]
                if (presetKey == null) {
                    keyboardDetail = null
                    return
                }
                addPresetKeyEditor(draft, detail.id, presetKey.label.ifEmpty { presetKey.send })
            }
        }
    }

    private fun androidx.preference.PreferenceGroup.addPresetKeyEditor(
        draft: ThemeDraft,
        id: String,
        summaryValue: String,
    ) {
        addPreference(
            title = getString(R.string.theme_designer_preset_key_item, id),
            summary = summaryValue.ifEmpty { getString(R.string.theme_designer_unset) },
        ) {
            showPresetKeyActionDialog(id)
        }
        ThemeFieldRegistry.presetKeyFields.forEach { field ->
            addPresetKeyFieldPreference(draft, id, field)
        }
    }

    private fun androidx.preference.PreferenceGroup.addPresetKeyFieldPreference(
        draft: ThemeDraft,
        presetKeyId: String,
        field: ThemeFieldRegistry.Field,
    ) {
        val rawValue = presetKeyFieldValue(draft, presetKeyId, field)
        val preference = if (field.key == "select") {
            Preference(context).apply {
                setOnPreferenceClickListener {
                    showKeyboardReferenceDialog(
                        title = field.title,
                        value = rawValue,
                        keyboardIds = draft.theme.presetKeyboards.keys.toList(),
                        allowSpecial = true,
                        onValue = { viewModel.updatePresetKeyField(presetKeyId, field.key, it) },
                    )
                    true
                }
            }
        } else {
            when (field.type) {
                ThemeFieldRegistry.Type.BOOLEAN -> SwitchPreferenceCompat(context).apply {
                    isChecked = rawValue.toBooleanStrictOrNull() ?: false
                    setOnPreferenceChangeListener { _, newValue ->
                        viewModel.updatePresetKeyField(presetKeyId, field.key, newValue.toString())
                        true
                    }
                }
                ThemeFieldRegistry.Type.ENUM -> ListPreference(context).apply {
                    entries = field.enumValues.toTypedArray()
                    entryValues = field.enumValues.toTypedArray()
                    value = rawValue.ifEmpty { field.defaultValue }
                    setOnPreferenceChangeListener { _, newValue ->
                        viewModel.updatePresetKeyField(presetKeyId, field.key, newValue.toString())
                        true
                    }
                }
                else -> EditTextPreference(context).apply {
                    text = rawValue
                    configureFieldInput(field)
                    setOnPreferenceChangeListener { _, newValue ->
                        viewModel.updatePresetKeyField(presetKeyId, field.key, newValue.toString())
                        true
                    }
                }
            }
        }
        preference.key = "theme_designer:preset-key:$presetKeyId:${field.key}"
        preference.title = "$presetKeyId: ${field.title}"
        preference.summary = fieldSummary(field, rawValue)
        preference.isIconSpaceReserved = false
        addPreference(preference)
    }

    private fun androidx.preference.PreferenceGroup.addLiquidKeyboardEditor(draft: ThemeDraft) {
        addPreference(R.string.theme_designer_new_liquid_keyboard) {
            showCreateLiquidKeyboardDialog()
        }
        draft.theme.liquidKeyboard.keyboards.forEach { keyboard ->
            addPreference(
                title = getString(R.string.theme_designer_liquid_keyboard_item, keyboard.id),
                summary = liquidKeyboardSummary(keyboard),
            ) {
                showLiquidKeyboardActionDialog(keyboard.id)
            }
            ThemeFieldRegistry.liquidKeyboardFields.forEach { field ->
                if (field.key != "keys") addLiquidKeyboardFieldPreference(draft, keyboard.id, field)
            }
            addPreference(
                title = getString(R.string.theme_designer_new_liquid_keyboard_key),
                summary = keyboard.id,
            ) {
                viewModel.insertLiquidKeyboardKey(keyboard.id, keyboard.keys.size)
            }
            keyboard.keys.forEachIndexed { index, key ->
                addPreference(
                    title = getString(R.string.theme_designer_liquid_keyboard_key_item, index + 1),
                    summary = "${key.text} / ${key.altText}",
                ) {
                    showLiquidKeyboardKeyActionDialog(keyboard.id, index, key.text, key.altText)
                }
            }
        }
    }

    private fun showLiquidKeyboardKeyActionDialog(
        keyboardId: String,
        index: Int,
        action: String,
        label: String,
    ) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.theme_designer_liquid_keyboard_key_item, index + 1))
            .setItems(
                arrayOf(
                    getString(R.string.theme_designer_edit_liquid_keyboard_key),
                    getString(R.string.theme_designer_choose_preset_key),
                    getString(R.string.theme_designer_insert_key_before),
                    getString(R.string.theme_designer_copy_key),
                    getString(R.string.theme_designer_delete_key),
                    getString(R.string.theme_designer_move_key_up),
                    getString(R.string.theme_designer_move_key_down),
                ),
            ) { _, which ->
                when (which) {
                    0 -> showLiquidKeyboardKeyEditor(keyboardId, index, action, label)
                    1 -> showLiquidKeyboardPresetKeyDialog(keyboardId, index, action, label)
                    2 -> viewModel.insertLiquidKeyboardKey(keyboardId, index)
                    3 -> viewModel.copyLiquidKeyboardKey(keyboardId, index)
                    4 -> confirmDeleteLiquidKeyboardKey(keyboardId, index)
                    5 -> viewModel.moveLiquidKeyboardKey(keyboardId, index, index - 1)
                    6 -> viewModel.moveLiquidKeyboardKey(keyboardId, index, index + 1)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showLiquidKeyboardPresetKeyDialog(
        keyboardId: String,
        index: Int,
        action: String,
        label: String,
    ) {
        val presetKeys = currentDraft()?.theme?.presetKeys?.keys?.sorted().orEmpty()
        if (presetKeys.isEmpty()) {
            showLiquidKeyboardKeyEditor(keyboardId, index, action, label)
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.theme_designer_choose_preset_key)
            .setItems(presetKeys.toTypedArray()) { _, which ->
                viewModel.updateLiquidKeyboardKey(keyboardId, index, presetKeys[which], label)
            }
            .setNeutralButton(R.string.theme_designer_free_text) { _, _ ->
                showLiquidKeyboardKeyEditor(keyboardId, index, action, label)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showLiquidKeyboardKeyEditor(
        keyboardId: String,
        index: Int,
        action: String,
        label: String,
    ) {
        val actionInput = EditText(requireContext()).apply {
            hint = getString(R.string.theme_designer_liquid_keyboard_key_action)
            setText(action)
        }
        val labelInput = EditText(requireContext()).apply {
            hint = getString(R.string.theme_designer_liquid_keyboard_key_label)
            setText(label)
        }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(20), context.dp(8), context.dp(20), 0)
            addView(actionInput)
            addView(labelInput)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.theme_designer_liquid_keyboard_key_item, index + 1))
            .setView(content)
            .setPositiveButton(R.string.ok) { _, _ ->
                viewModel.updateLiquidKeyboardKey(
                    keyboardId,
                    index,
                    actionInput.text.toString(),
                    labelInput.text.toString(),
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteLiquidKeyboardKey(
        keyboardId: String,
        index: Int,
    ) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.theme_designer_delete_key)
            .setMessage(getString(R.string.theme_designer_delete_key_message, keyboardId, index + 1))
            .setPositiveButton(R.string.ok) { _, _ -> viewModel.deleteLiquidKeyboardKey(keyboardId, index) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun androidx.preference.PreferenceGroup.addLiquidKeyboardFieldPreference(
        draft: ThemeDraft,
        keyboardId: String,
        field: ThemeFieldRegistry.Field,
    ) {
        val rawValue = liquidKeyboardFieldValue(draft, keyboardId, field)
        val preference = when (field.type) {
            ThemeFieldRegistry.Type.BOOLEAN -> SwitchPreferenceCompat(context).apply {
                isChecked = rawValue.toBooleanStrictOrNull() ?: false
                setOnPreferenceChangeListener { _, newValue ->
                    viewModel.updateLiquidKeyboardField(keyboardId, field.key, newValue.toString())
                    true
                }
            }
            ThemeFieldRegistry.Type.ENUM -> ListPreference(context).apply {
                entries = field.enumValues.toTypedArray()
                entryValues = field.enumValues.toTypedArray()
                value = rawValue.ifEmpty { field.defaultValue }
                setOnPreferenceChangeListener { _, newValue ->
                    viewModel.updateLiquidKeyboardField(keyboardId, field.key, newValue.toString())
                    true
                }
            }
            else -> EditTextPreference(context).apply {
                text = rawValue
                configureFieldInput(field)
                setOnPreferenceChangeListener { _, newValue ->
                    viewModel.updateLiquidKeyboardField(keyboardId, field.key, newValue.toString())
                    true
                }
            }
        }
        preference.key = "theme_designer:liquid-keyboard:$keyboardId:${field.key}"
        preference.title = "$keyboardId: ${field.title}"
        preference.summary = fieldSummary(field, rawValue)
        preference.isIconSpaceReserved = false
        addPreference(preference)
    }

    private fun androidx.preference.PreferenceGroup.addToolbarEditor(draft: ThemeDraft) {
        val primaryButton = draft.theme.toolBar.primaryButton
        if (primaryButton == null) {
            addPreference(R.string.theme_designer_create_primary_toolbar_button) {
                viewModel.setPrimaryToolbarButton()
            }
        } else {
            addPreference(
                title = getString(R.string.theme_designer_primary_toolbar_button),
                summary = toolbarButtonSummary(primaryButton),
            )
            ThemeFieldRegistry.toolbarButtonFields.forEach { field ->
                addToolbarButtonFieldPreference(draft, primary = true, index = null, field = field)
            }
        }
        addPreference(R.string.theme_designer_insert_toolbar_button) {
            viewModel.insertToolbarButton(draft.theme.toolBar.buttons.size)
        }
        draft.theme.toolBar.buttons.forEachIndexed { index, button ->
            addPreference(
                title = getString(R.string.theme_designer_toolbar_button_item, index + 1),
                summary = toolbarButtonSummary(button),
            ) {
                showToolbarButtonActionDialog(index)
            }
            ThemeFieldRegistry.toolbarButtonFields.forEach { field ->
                addToolbarButtonFieldPreference(draft, primary = false, index = index, field = field)
            }
        }
    }

    private fun androidx.preference.PreferenceGroup.addToolbarButtonFieldPreference(
        draft: ThemeDraft,
        primary: Boolean,
        index: Int?,
        field: ThemeFieldRegistry.Field,
    ) {
        val rawValue = toolbarButtonFieldValue(draft, primary, index, field)
        val preference = if (field.key in TOOLBAR_ACTION_FIELDS) {
            Preference(context).apply {
                setOnPreferenceClickListener {
                    showActionValueDialog(
                        title = field.title,
                        value = rawValue,
                        presetKeys = draft.theme.presetKeys.keys.sorted(),
                        onValue = { updateToolbarButtonField(primary, index, field.key, it) },
                    )
                    true
                }
            }
        } else if (field.isFontField()) {
            Preference(context).apply {
                setOnPreferenceClickListener {
                    showFontValuePicker(draft, field.title, rawValue) {
                        updateToolbarButtonField(primary, index, field.key, it)
                    }
                    true
                }
            }
        } else {
            when (field.type) {
                ThemeFieldRegistry.Type.BOOLEAN -> SwitchPreferenceCompat(context).apply {
                    isChecked = rawValue.toBooleanStrictOrNull() ?: false
                    setOnPreferenceChangeListener { _, newValue ->
                        updateToolbarButtonField(primary, index, field.key, newValue.toString())
                        true
                    }
                }

                ThemeFieldRegistry.Type.ENUM -> ListPreference(context).apply {
                    entries = field.enumValues.toTypedArray()
                    entryValues = field.enumValues.toTypedArray()
                    value = rawValue.ifEmpty { field.defaultValue }
                    setOnPreferenceChangeListener { _, newValue ->
                        updateToolbarButtonField(primary, index, field.key, newValue.toString())
                        true
                    }
                }

                else -> EditTextPreference(context).apply {
                    text = rawValue
                    configureFieldInput(field)
                    setOnPreferenceChangeListener { _, newValue ->
                        updateToolbarButtonField(primary, index, field.key, newValue.toString())
                        true
                    }
                }
            }
        }
        val prefix = if (primary) {
            getString(R.string.theme_designer_primary_toolbar_button)
        } else {
            getString(R.string.theme_designer_toolbar_button_item, (index ?: 0) + 1)
        }
        preference.key = "theme_designer:toolbar-button:$primary:${index ?: -1}:${field.key}"
        preference.title = "$prefix: ${field.title}"
        preference.summary = fieldSummary(field, rawValue)
        preference.isIconSpaceReserved = false
        addPreference(preference)
    }

    private fun updateToolbarButtonField(
        primary: Boolean,
        index: Int?,
        fieldKey: String,
        value: String,
    ) {
        if (primary) {
            viewModel.updatePrimaryToolbarButtonField(fieldKey, value)
        } else {
            viewModel.updateToolbarButtonField(index ?: return, fieldKey, value)
        }
    }

    private fun androidx.preference.PreferenceGroup.addKeyboardEditor(
        draft: ThemeDraft,
        id: String,
        keyboard: TextKeyboard,
    ) {
        addPreference(
            title = getString(R.string.theme_designer_keyboard_item, id),
            summary = keyboardSummary(keyboard),
        ) {
            showKeyboardActionDialog(id)
        }
        ThemeFieldRegistry.keyboardFields.forEach { field ->
            addKeyboardFieldPreference(draft, id, field)
        }
        keyboard.keys.forEachIndexed { index, key ->
            addPreference(
                title = getString(R.string.theme_designer_key_actions, id, index + 1),
                summary = listOf(key.label, key.click).filter { it.isNotEmpty() }.joinToString(" / "),
            ) {
                showKeyActionDialog(id, index)
            }
            ThemeFieldRegistry.keyFields.forEach { field ->
                addKeyFieldPreference(draft, id, index, field)
            }
            ThemeFieldRegistry.keyActionBehaviors.forEach { behavior ->
                val field = behavior.name.lowercase()
                val value = key.behaviors[behavior].orEmpty()
                addKeyActionPreference(draft, id, index, field, value)
            }
            addKeyActionPreference(draft, id, index, "popup", key.popup.joinToString("\n"), popup = true)
        }
    }

    private fun androidx.preference.PreferenceGroup.addKeyboardFieldPreference(
        draft: ThemeDraft,
        keyboardId: String,
        field: ThemeFieldRegistry.Field,
    ) {
        val rawValue = keyboardFieldValue(draft, keyboardId, field)
        val preference = if (field.isKeyboardReferenceField()) {
            Preference(context).apply {
                setOnPreferenceClickListener {
                    showKeyboardReferenceDialog(
                        title = field.title,
                        value = rawValue,
                        keyboardIds = draft.theme.presetKeyboards.keys.toList(),
                        onValue = { viewModel.updateKeyboardField(keyboardId, field.key, it) },
                    )
                    true
                }
            }
        } else {
            when (field.type) {
                ThemeFieldRegistry.Type.BOOLEAN -> SwitchPreferenceCompat(context).apply {
                    isChecked = rawValue.toBooleanStrictOrNull() ?: false
                    setOnPreferenceChangeListener { _, newValue ->
                        viewModel.updateKeyboardField(keyboardId, field.key, newValue.toString())
                        true
                    }
                }

                ThemeFieldRegistry.Type.ENUM -> ListPreference(context).apply {
                    entries = field.enumValues.toTypedArray()
                    entryValues = field.enumValues.toTypedArray()
                    value = rawValue.ifEmpty { field.defaultValue }
                    setOnPreferenceChangeListener { _, newValue ->
                        viewModel.updateKeyboardField(keyboardId, field.key, newValue.toString())
                        true
                    }
                }

                else -> EditTextPreference(context).apply {
                    text = rawValue
                    configureFieldInput(field)
                    setOnPreferenceChangeListener { _, newValue ->
                        viewModel.updateKeyboardField(keyboardId, field.key, newValue.toString())
                        true
                    }
                }
            }
        }
        preference.key = "theme_designer:keyboard:$keyboardId:${field.key}"
        preference.title = "$keyboardId: ${field.title}"
        preference.summary = fieldSummary(field, rawValue)
        preference.isIconSpaceReserved = false
        addPreference(preference)
    }

    private fun androidx.preference.PreferenceGroup.addKeyFieldPreference(
        draft: ThemeDraft,
        keyboardId: String,
        index: Int,
        field: ThemeFieldRegistry.Field,
    ) {
        val rawValue = keyFieldValue(draft, keyboardId, index, field)
        val preference = if (field.isColorField()) {
            Preference(context).apply {
                setOnPreferenceClickListener {
                    showThemeValuePicker(
                        draft = draft,
                        title = field.title,
                        value = rawValue,
                        onValue = { viewModel.updateKeyboardKeyField(keyboardId, index, field.key, it) },
                    )
                    true
                }
            }
        } else {
            when (field.type) {
                ThemeFieldRegistry.Type.BOOLEAN -> SwitchPreferenceCompat(context).apply {
                    isChecked = rawValue.toBooleanStrictOrNull() ?: field.defaultValue.toBooleanStrictOrNull() ?: false
                    setOnPreferenceChangeListener { _, newValue ->
                        viewModel.updateKeyboardKeyField(keyboardId, index, field.key, newValue.toString())
                        true
                    }
                }
                else -> EditTextPreference(context).apply {
                    text = rawValue
                    configureFieldInput(field)
                    setOnPreferenceChangeListener { _, newValue ->
                        viewModel.updateKeyboardKeyField(keyboardId, index, field.key, newValue.toString())
                        true
                    }
                }
            }
        }
        preference.key = "theme_designer:key:$keyboardId:$index:${field.key}"
        preference.title = getString(R.string.theme_designer_key_field, keyboardId, index + 1, field.title)
        preference.summary = fieldSummary(field, rawValue)
        preference.isIconSpaceReserved = false
        addPreference(preference)
    }

    private fun androidx.preference.PreferenceGroup.addKeyActionPreference(
        draft: ThemeDraft,
        keyboardId: String,
        index: Int,
        field: String,
        value: String,
        popup: Boolean = false,
    ) {
        addPreference(
            Preference(context).apply {
                key = "theme_designer:key-action:$keyboardId:$index:$field"
                title = getString(R.string.theme_designer_key_field, keyboardId, index + 1, field)
                summary = value.ifEmpty { getString(R.string.theme_designer_unset) }
                isIconSpaceReserved = false
                setOnPreferenceClickListener {
                    showKeyActionValueDialog(draft, keyboardId, index, field, value, popup)
                    true
                }
            },
        )
    }

    private fun showKeyActionValueDialog(
        draft: ThemeDraft,
        keyboardId: String,
        index: Int,
        field: String,
        value: String,
        popup: Boolean,
    ) {
        if (popup) {
            showPopupKeyPickerDialog(draft, keyboardId, index, field, value)
            return
        }
        val presetKeys = draft.theme.presetKeys.keys.sorted()
        if (presetKeys.isEmpty()) {
            showKeyFreeTextDialog(keyboardId, index, field, value, popup = false)
            return
        }
        showPresetKeySearchDialog(
            title = getString(R.string.theme_designer_key_field, keyboardId, index + 1, field),
            presetKeys = presetKeys,
            onSelect = { viewModel.updateKeyboardKeyField(keyboardId, index, field, it) },
            onFreeText = { showKeyFreeTextDialog(keyboardId, index, field, value, popup = false) },
        )
    }

    private fun showPresetKeySearchDialog(
        title: String,
        presetKeys: List<String>,
        onSelect: (String) -> Unit,
        onFreeText: () -> Unit,
    ) {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, presetKeys.toMutableList())
        val list = ListView(requireContext()).apply { this.adapter = adapter }
        val search = SearchView(requireContext()).apply {
            isIconified = false
            queryHint = getString(R.string.theme_designer_search_preset_keys)
            setOnQueryTextListener(
                object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean = false

                    override fun onQueryTextChange(newText: String?): Boolean {
                        val query = newText.orEmpty()
                        adapter.clear()
                        adapter.addAll(presetKeys.filter { it.contains(query, ignoreCase = true) })
                        return true
                    }
                },
            )
        }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = resources.displayMetrics.heightPixels / 2
            addView(
                search,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
            addView(
                list,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(content)
            .setNeutralButton(R.string.theme_designer_free_text) { _, _ -> onFreeText() }
            .setNegativeButton(R.string.cancel, null)
            .create()
        list.setOnItemClickListener { _, _, position, _ ->
            adapter.getItem(position)?.let(onSelect)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showPopupKeyPickerDialog(
        draft: ThemeDraft,
        keyboardId: String,
        index: Int,
        field: String,
        value: String,
    ) {
        val presetKeys = draft.theme.presetKeys.keys.sorted()
        if (presetKeys.isEmpty()) {
            showKeyFreeTextDialog(keyboardId, index, field, value, popup = true)
            return
        }
        val selected = value.lineSequence().filter { it.isNotEmpty() }.toMutableSet()
        val customValues = selected.filterNot { it in presetKeys }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.theme_designer_key_field, keyboardId, index + 1, field))
            .setMultiChoiceItems(
                presetKeys.toTypedArray(),
                BooleanArray(presetKeys.size) { presetKeys[it] in selected },
            ) { _, which, isChecked ->
                if (isChecked) {
                    selected += presetKeys[which]
                } else {
                    selected -= presetKeys[which]
                }
            }
            .setPositiveButton(R.string.ok) { _, _ ->
                val ordered = customValues + presetKeys.filter { it in selected }
                viewModel.updateKeyboardKeyPopup(keyboardId, index, ordered)
            }
            .setNeutralButton(R.string.theme_designer_free_text) { _, _ ->
                showKeyFreeTextDialog(keyboardId, index, field, value, popup = true)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showKeyFreeTextDialog(
        keyboardId: String,
        index: Int,
        field: String,
        value: String,
        popup: Boolean,
    ) {
        val input = EditText(requireContext()).apply {
            setText(value)
            if (popup) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            selectAll()
        }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.theme_designer_key_field, keyboardId, index + 1, field))
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                if (popup) {
                    viewModel.updateKeyboardKeyPopup(
                        keyboardId,
                        index,
                        input.text.lineSequence().filter { it.isNotEmpty() }.toList(),
                    )
                } else {
                    viewModel.updateKeyboardKeyField(keyboardId, index, field, input.text.toString())
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showActionValueDialog(
        title: String,
        value: String,
        presetKeys: List<String>,
        onValue: (String) -> Unit,
    ) {
        val items = buildList {
            add(getString(R.string.theme_designer_free_text))
            addAll(presetKeys)
            if (value.isNotEmpty()) add(getString(R.string.theme_designer_clear_value))
        }
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setItems(items.toTypedArray()) { _, which ->
                when {
                    which == 0 -> showActionFreeTextDialog(title, value, onValue)
                    which <= presetKeys.size -> onValue(presetKeys[which - 1])
                    else -> onValue("")
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showActionFreeTextDialog(
        title: String,
        value: String,
        onValue: (String) -> Unit,
    ) {
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

    private fun showKeyboardActionDialog(id: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(id)
            .setItems(
                arrayOf(
                    getString(R.string.theme_designer_copy_keyboard),
                    getString(R.string.theme_designer_rename_keyboard),
                    getString(R.string.theme_designer_reorder_keyboard_keys),
                    getString(R.string.theme_designer_resize_keyboard_keys),
                    getString(R.string.theme_designer_delete_keyboard),
                ),
            ) { _, which ->
                when (which) {
                    0 -> viewModel.copyKeyboard(id)
                    1 -> showRenameKeyboardDialog(id)
                    2 -> showReorderKeysDialog(id)
                    3 -> showResizeKeysDialog(id)
                    4 -> confirmDeleteKeyboard(id)
                }
            }
            .show()
    }

    private fun showLiquidKeyboardActionDialog(id: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(id)
            .setItems(
                arrayOf(
                    getString(R.string.theme_designer_copy_liquid_keyboard),
                    getString(R.string.theme_designer_delete_liquid_keyboard),
                ),
            ) { _, which ->
                when (which) {
                    0 -> viewModel.copyLiquidKeyboard(id)
                    1 -> confirmDeleteLiquidKeyboard(id)
                }
            }
            .show()
    }

    private fun confirmDeleteLiquidKeyboard(id: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.theme_designer_delete_liquid_keyboard)
            .setMessage(getString(R.string.theme_designer_delete_liquid_keyboard_message, id))
            .setPositiveButton(R.string.ok) { _, _ -> viewModel.deleteLiquidKeyboard(id) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showToolbarButtonActionDialog(index: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.theme_designer_toolbar_button_item, index + 1))
            .setItems(
                arrayOf(
                    getString(R.string.theme_designer_insert_toolbar_button_before),
                    getString(R.string.theme_designer_copy_toolbar_button),
                    getString(R.string.theme_designer_delete_toolbar_button),
                    getString(R.string.theme_designer_move_toolbar_button_up),
                    getString(R.string.theme_designer_move_toolbar_button_down),
                ),
            ) { _, which ->
                when (which) {
                    0 -> viewModel.insertToolbarButton(index)
                    1 -> viewModel.copyToolbarButton(index)
                    2 -> confirmDeleteToolbarButton(index)
                    3 -> viewModel.moveToolbarButton(index, index - 1)
                    4 -> viewModel.moveToolbarButton(index, index + 1)
                }
            }
            .show()
    }

    private fun confirmDeleteToolbarButton(index: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.theme_designer_delete_toolbar_button)
            .setMessage(getString(R.string.theme_designer_delete_toolbar_button_message, index + 1))
            .setPositiveButton(R.string.ok) { _, _ -> viewModel.deleteToolbarButton(index) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showPresetKeyActionDialog(id: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(id)
            .setItems(
                arrayOf(
                    getString(R.string.theme_designer_copy_preset_key),
                    getString(R.string.theme_designer_delete_preset_key),
                ),
            ) { _, which ->
                when (which) {
                    0 -> viewModel.copyPresetKey(id)
                    1 -> confirmDeletePresetKey(id)
                }
            }
            .show()
    }

    private fun confirmDeletePresetKey(id: String) {
        val references = currentDraft()?.let { ThemeReferenceScanner.presetKeyReferences(it.yamlNode, id) }.orEmpty()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.theme_designer_delete_preset_key)
            .setMessage(deleteMessage(getString(R.string.theme_designer_delete_preset_key_message, id), references))
            .setPositiveButton(R.string.ok) { _, _ -> viewModel.deletePresetKey(id) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showKeyActionDialog(
        keyboardId: String,
        index: Int,
    ) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.theme_designer_key_actions, keyboardId, index + 1))
            .setItems(
                arrayOf(
                    getString(R.string.theme_designer_insert_key_before),
                    getString(R.string.theme_designer_copy_key),
                    getString(R.string.theme_designer_delete_key),
                    getString(R.string.theme_designer_move_key_up),
                    getString(R.string.theme_designer_move_key_down),
                ),
            ) { _, which ->
                when (which) {
                    0 -> viewModel.insertKeyboardKey(keyboardId, index)
                    1 -> viewModel.copyKeyboardKey(keyboardId, index)
                    2 -> confirmDeleteKey(keyboardId, index)
                    3 -> viewModel.moveKeyboardKey(keyboardId, index, index - 1)
                    4 -> viewModel.moveKeyboardKey(keyboardId, index, index + 1)
                }
            }
            .show()
    }

    private fun confirmDeleteKey(
        keyboardId: String,
        index: Int,
    ) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.theme_designer_delete_key)
            .setMessage(getString(R.string.theme_designer_delete_key_message, keyboardId, index + 1))
            .setPositiveButton(R.string.ok) { _, _ -> viewModel.deleteKeyboardKey(keyboardId, index) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showResizeKeysDialog(keyboardId: String) {
        val widthInput = EditText(requireContext()).apply {
            hint = "width"
            inputType = DECIMAL_NUMBER_INPUT
        }
        val heightInput = EditText(requireContext()).apply {
            hint = "height"
            inputType = DECIMAL_NUMBER_INPUT
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
            addView(widthInput)
            addView(heightInput)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.theme_designer_resize_keyboard_keys)
            .setView(container)
            .setPositiveButton(R.string.ok) { _, _ ->
                viewModel.resizeKeyboardKeys(
                    keyboardId,
                    widthInput.text.toString(),
                    heightInput.text.toString(),
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showReorderKeysDialog(keyboardId: String) {
        val draft = currentDraft() ?: return
        val keys = draft.theme.presetKeyboards[keyboardId]?.keys ?: return
        val items = keys.mapIndexed { index, key ->
            ReorderKeyItem(
                originalIndex = index,
                label = key.label.ifEmpty { key.click }.ifEmpty { "#" + (index + 1) },
                summary = listOf(key.click, key.hint, key.labelSymbol)
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .joinToString(" / "),
            )
        }.toMutableList()
        val adapter = ReorderKeyAdapter(items)
        val list = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
            overScrollMode = RecyclerView.OVER_SCROLL_IF_CONTENT_SCROLLS
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.displayMetrics.heightPixels / 2,
            )
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

                override fun onSwiped(
                    viewHolder: RecyclerView.ViewHolder,
                    direction: Int,
                ) = Unit

                override fun isLongPressDragEnabled(): Boolean = true
            },
        ).attachToRecyclerView(list)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.theme_designer_reorder_keyboard_keys)
            .setView(list)
            .setPositiveButton(R.string.ok) { _, _ ->
                viewModel.reorderKeyboardKeys(keyboardId, items.joinToString(", ") { (it.originalIndex + 1).toString() })
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showRenameKeyboardDialog(id: String) {
        val input = EditText(requireContext()).apply {
            setText(id)
            selectAll()
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.theme_designer_rename_keyboard)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                viewModel.renameKeyboard(id, input.text.toString())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteKeyboard(id: String) {
        val references = currentDraft()?.let { ThemeReferenceScanner.keyboardReferences(it.yamlNode, id) }.orEmpty()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.theme_designer_delete_keyboard)
            .setMessage(deleteMessage(getString(R.string.theme_designer_delete_keyboard_message, id), references))
            .setPositiveButton(R.string.ok) { _, _ -> viewModel.deleteKeyboard(id) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun currentDraft(): ThemeDraft? = when (val state = viewModel.state.value) {
        is ThemeDesignerViewModel.State.Loaded -> state.draft
        is ThemeDesignerViewModel.State.Error -> state.draft
        is ThemeDesignerViewModel.State.Working -> state.draft
        ThemeDesignerViewModel.State.Loading -> null
    }

    private fun deleteMessage(
        base: String,
        references: List<String>,
    ): String {
        if (references.isEmpty()) return base
        return buildString {
            append(base)
            append("\n\n")
            append(getString(R.string.theme_designer_delete_references_warning))
            references.take(DELETE_REFERENCE_PREVIEW_LIMIT).forEach {
                append("\n")
                append(it)
            }
            if (references.size > DELETE_REFERENCE_PREVIEW_LIMIT) {
                append("\n")
                append(getString(R.string.theme_designer_delete_references_more, references.size - DELETE_REFERENCE_PREVIEW_LIMIT))
            }
        }
    }

    private fun showCreateKeyboardDialog() {
        val input = EditText(requireContext()).apply {
            setText("custom")
            selectAll()
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.theme_designer_new_keyboard)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                viewModel.createKeyboard(input.text.toString())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showCreatePresetKeyDialog() {
        val input = EditText(requireContext()).apply {
            setText("custom_key")
            selectAll()
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.theme_designer_new_preset_key)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                viewModel.createPresetKey(input.text.toString())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showCreateLiquidKeyboardDialog() {
        val input = EditText(requireContext()).apply {
            setText("custom_liquid")
            selectAll()
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.theme_designer_new_liquid_keyboard)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                viewModel.createLiquidKeyboard(input.text.toString())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEnabledKeyboardsDialog(
        draft: ThemeDraft,
        rawValue: String,
    ) {
        val keyboardIds = draft.theme.presetKeyboards.keys.toList()
        val selected = rawValue.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableSet()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.theme_designer_keyboard_layouts)
            .setMultiChoiceItems(
                keyboardIds.toTypedArray(),
                BooleanArray(keyboardIds.size) { keyboardIds[it] in selected },
            ) { _, which, isChecked ->
                if (isChecked) {
                    selected += keyboardIds[which]
                } else {
                    selected -= keyboardIds[which]
                }
            }
            .setPositiveButton(R.string.ok) { _, _ ->
                val ordered = keyboardIds.filter { it in selected }
                viewModel.updateField("style/keyboards", ordered.joinToString(", "))
            }
            .setNeutralButton(R.string.theme_designer_free_text) { _, _ ->
                showCustomKeyboardListDialog(rawValue)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showKeyboardReferenceDialog(
        title: String,
        value: String,
        keyboardIds: List<String>,
        allowSpecial: Boolean = false,
        onValue: (String) -> Unit,
    ) {
        val specialIds = ThemeKeyboardReferences.specialIds.takeIf { allowSpecial }.orEmpty()
        val items = (listOf("") + keyboardIds + specialIds).distinct()
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setSingleChoiceItems(
                items.map { it.ifEmpty { getString(R.string.theme_designer_unset) } }.toTypedArray(),
                items.indexOf(value).takeIf { it >= 0 } ?: -1,
            ) { dialog, which ->
                onValue(items[which])
                dialog.dismiss()
            }
            .setNeutralButton(R.string.theme_designer_free_text) { _, _ ->
                showCustomKeyboardReferenceDialog(title, value, onValue)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showCustomKeyboardListDialog(value: String) {
        val input = EditText(requireContext()).apply {
            setText(value)
            hint = "qwerty, number, symbols"
            selectAll()
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.theme_designer_keyboard_layouts)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                viewModel.updateField("style/keyboards", input.text.toString())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showCustomKeyboardReferenceDialog(
        title: String,
        value: String,
        onValue: (String) -> Unit,
    ) {
        val input = EditText(requireContext()).apply {
            setText(value)
            hint = "qwerty"
            selectAll()
        }
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ -> onValue(input.text.toString()) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun androidx.preference.PreferenceScreen.addSourceSection(draft: ThemeDraft) {
        addCategory(R.string.theme_designer_source) {
            addPreference(
                title = getString(R.string.theme_designer_source),
                summary = sourceSummary(draft),
            ) {
                showSourceDialog(draft)
            }
        }
    }

    private fun showSourceDialog(draft: ThemeDraft) {
        val unknownFields = ThemeUnknownFieldScanner.scan(draft.yamlNode)
        val input = EditText(requireContext()).apply {
            setText(draft.yamlText)
            minLines = SOURCE_EDIT_MIN_LINES
            maxLines = SOURCE_EDIT_MAX_LINES
            setHorizontallyScrolling(true)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.theme_designer_source)
            .setMessage(unknownFieldMessage(unknownFields))
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                viewModel.updateSource(input.text.toString())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun sourceSummary(draft: ThemeDraft): String {
        val unknownFields = ThemeUnknownFieldScanner.scan(draft.yamlNode)
        if (unknownFields.isEmpty()) return draft.yamlText.take(SOURCE_PREVIEW_LIMIT)
        return buildString {
            append(getString(R.string.theme_designer_unknown_fields, unknownFields.size))
            unknownFields.take(UNKNOWN_FIELD_PREVIEW_LIMIT).forEach {
                append("\n")
                append(it)
            }
            append("\n\n")
            append(draft.yamlText.take(SOURCE_PREVIEW_LIMIT))
        }
    }

    private fun unknownFieldMessage(unknownFields: List<String>): String? {
        if (unknownFields.isEmpty()) return null
        return buildString {
            append(getString(R.string.theme_designer_unknown_fields, unknownFields.size))
            unknownFields.take(UNKNOWN_FIELD_DIALOG_LIMIT).forEach {
                append("\n")
                append(it)
            }
            if (unknownFields.size > UNKNOWN_FIELD_DIALOG_LIMIT) {
                append("\n")
                append(getString(R.string.theme_designer_delete_references_more, unknownFields.size - UNKNOWN_FIELD_DIALOG_LIMIT))
            }
        }
    }

    private fun androidx.preference.PreferenceScreen.addColorSection(draft: ThemeDraft) {
        addCategory(R.string.theme_designer_colors) {
            addPreference(R.string.theme_designer_new_color_scheme) {
                showCreateColorSchemeDialog()
            }
            addPreference(
                title = getString(R.string.theme_designer_fallback_colors),
                summary = draft.theme.fallbackColors.size.toString(),
            ) {
                showFallbackColorsDialog(draft)
            }
            draft.theme.colorSchemes.forEach { scheme ->
                addPreference(
                    title = getString(R.string.theme_designer_color_scheme_item, scheme.id),
                    summary = scheme.colors.entries.take(COLOR_PREVIEW_LIMIT)
                        .joinToString("\n") { "${it.key} = ${it.value}" },
                ) {
                    showColorSchemeDialog(draft, scheme.id)
                }
            }
        }
    }

    private fun showColorSchemeDialog(
        draft: ThemeDraft,
        schemeId: String,
    ) {
        val scheme = draft.theme.colorSchemes.firstOrNull { it.id == schemeId } ?: return
        val actionCount = 2
        val keys = ThemeColorFieldRegistry.colorKeysFor(draft, schemeId)
        val items = listOf(
            getString(R.string.theme_designer_copy_color_scheme),
            getString(R.string.theme_designer_delete_color_scheme),
        ) + keys.map { "$it = ${scheme.colors[it].orEmpty()}" }
        AlertDialog.Builder(requireContext())
            .setTitle(schemeId)
            .setItems(items.toTypedArray()) { _, which ->
                when (which) {
                    0 -> viewModel.copyColorScheme(schemeId)
                    1 -> confirmDeleteColorScheme(schemeId)
                    else -> {
                        val colorKey = keys[which - actionCount]
                        if (colorKey in ThemeColorFieldRegistry.schemeReferenceKeys) {
                            showColorSchemeReferenceDialog(draft, schemeId, colorKey, scheme.colors[colorKey].orEmpty())
                        } else {
                            showColorValueDialog(draft, schemeId, colorKey, scheme.colors[colorKey].orEmpty())
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showColorSchemeReferenceDialog(
        draft: ThemeDraft,
        schemeId: String,
        field: String,
        value: String,
    ) {
        val schemeIds = draft.theme.colorSchemes.map { it.id }
        val items = buildList {
            addAll(schemeIds)
            if (value.isNotBlank()) add(getString(R.string.theme_designer_clear_value))
        }
        AlertDialog.Builder(requireContext())
            .setTitle(field)
            .setItems(items.toTypedArray()) { _, which ->
                viewModel.updateColor(schemeId, field, schemeIds.getOrNull(which).orEmpty())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showCreateColorSchemeDialog() {
        val input = EditText(requireContext()).apply {
            setText("custom_colors")
            selectAll()
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.theme_designer_new_color_scheme)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                viewModel.createColorScheme(input.text.toString())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteColorScheme(schemeId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.theme_designer_delete_color_scheme)
            .setMessage(getString(R.string.theme_designer_delete_color_scheme_message, schemeId))
            .setPositiveButton(R.string.ok) { _, _ -> viewModel.deleteColorScheme(schemeId) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showColorValueDialog(
        draft: ThemeDraft,
        schemeId: String,
        colorKey: String,
        value: String,
    ) {
        showThemeValuePicker(
            draft = draft,
            title = colorKey,
            value = value,
            onValue = { viewModel.updateColor(schemeId, colorKey, it) },
            referenceSchemeId = schemeId,
            neutralTitle = getString(R.string.theme_designer_fallback_color),
            onNeutral = { showFallbackColorDialog(draft, colorKey, draft.theme.fallbackColors[colorKey].orEmpty()) },
        )
    }

    private fun showThemeValuePicker(
        draft: ThemeDraft,
        title: String,
        value: String,
        onValue: (String) -> Unit,
        referenceSchemeId: String = draft.theme.colorSchemes.firstOrNull()?.id.orEmpty(),
        neutralTitle: String? = null,
        onNeutral: (() -> Unit)? = null,
    ) {
        val references = ThemeColorFieldRegistry.colorKeysFor(draft, referenceSchemeId)
        val groups = mutableListOf(
            getString(R.string.theme_designer_custom_color),
            getString(R.string.theme_designer_color_references),
            getString(R.string.theme_designer_common_colors),
        )
        val clearIndex = if (value.isNotBlank()) {
            groups += getString(R.string.theme_designer_clear_value)
            groups.lastIndex
        } else {
            -1
        }
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setItems(groups.toTypedArray()) { _, which ->
                when (which) {
                    0 -> showCustomColorValueDialog(title, value, onValue)
                    1 -> showColorReferenceDialog(title, references, onValue)
                    2 -> showCommonColorDialog(title, COMMON_COLOR_VALUES, onValue)
                    clearIndex -> onValue("")
                }
            }
            .apply {
                if (neutralTitle != null && onNeutral != null) {
                    setNeutralButton(neutralTitle) { _, _ -> onNeutral() }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showColorReferenceDialog(
        title: String,
        references: List<String>,
        onValue: (String) -> Unit,
    ) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setItems(references.toTypedArray()) { _, which -> onValue(references[which]) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showCommonColorDialog(
        title: String,
        values: List<String>,
        onValue: (String) -> Unit,
    ) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setItems(values.toTypedArray()) { _, which -> onValue(values[which]) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showCustomColorValueDialog(
        title: String,
        value: String,
        onValue: (String) -> Unit,
    ) {
        ColorPickerDialog.buildColorValue(
            context = requireContext(),
            title = title,
            value = value,
            onConfirm = onValue,
        ).show()
    }

    private fun showFallbackColorsDialog(draft: ThemeDraft) {
        val keys = ThemeColorFieldRegistry.fallbackKeysFor(draft)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.theme_designer_fallback_colors)
            .setItems(keys.map { "$it = ${draft.theme.fallbackColors[it].orEmpty()}" }.toTypedArray()) { _, which ->
                val colorKey = keys[which]
                showFallbackColorDialog(draft, colorKey, draft.theme.fallbackColors[colorKey].orEmpty())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showFallbackColorDialog(
        draft: ThemeDraft,
        colorKey: String,
        fallbackValue: String,
    ) {
        showThemeValuePicker(
            draft = draft,
            title = "${getString(R.string.theme_designer_fallback_color)}: $colorKey",
            value = fallbackValue,
            onValue = { viewModel.updateFallbackColor(colorKey, it) },
        )
    }

    private fun showFontValuePicker(
        draft: ThemeDraft,
        title: String,
        value: String,
        onValue: (String) -> Unit,
    ) {
        val groups = listOf(
            getString(R.string.theme_designer_font_files),
            getString(R.string.theme_designer_theme_fonts),
            getString(R.string.theme_designer_custom_fonts),
        )
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setItems(groups.toTypedArray()) { _, which ->
                when (which) {
                    0 -> showFontListDialog(title, userFontFiles(), onValue)
                    1 -> showFontListDialog(title, themeFonts(draft), onValue)
                    2 -> showCustomFontValueDialog(title, value, onValue)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showFontListDialog(
        title: String,
        values: List<String>,
        onValue: (String) -> Unit,
    ) {
        if (values.isEmpty()) {
            showCustomFontValueDialog(title, "", onValue)
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setItems(values.toTypedArray()) { _, which -> onValue(values[which]) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showCustomFontValueDialog(
        title: String,
        value: String,
        onValue: (String) -> Unit,
    ) {
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

    private fun keyboardSummary(keyboard: TextKeyboard): String = buildString {
        append(
            getString(
                R.string.theme_designer_keyboard_summary,
                keyboard.keys.size,
                keyboard.width,
                keyboard.height,
            ),
        )
    }

    private fun liquidKeyboardSummary(keyboard: LiquidKeyboard.Keyboard): String = "${keyboard.type} / ${keyboard.name} / ${keyboard.keys.size}"

    private fun toolbarButtonSummary(button: ToolBar.Button): String = listOf(
        button.action,
        button.longPressAction,
        button.foreground.normal,
        button.foreground.style,
    ).firstOrNull { it.isNotEmpty() } ?: getString(R.string.theme_designer_unset)

    private fun androidx.preference.PreferenceGroup.addFieldPreference(
        draft: ThemeDraft,
        field: ThemeFieldRegistry.Field,
    ) {
        val rawValue = fieldValue(draft, field)
        val preference = if (field.key == "style/keyboards") {
            Preference(context).apply {
                setOnPreferenceClickListener {
                    showEnabledKeyboardsDialog(draft, rawValue)
                    true
                }
            }
        } else if (field.isFontField()) {
            Preference(context).apply {
                setOnPreferenceClickListener {
                    showFontValuePicker(draft, field.title, rawValue) {
                        viewModel.updateField(field.key, it)
                    }
                    true
                }
            }
        } else {
            when (field.type) {
                ThemeFieldRegistry.Type.BOOLEAN -> SwitchPreferenceCompat(context).apply {
                    isChecked = rawValue.toBooleanStrictOrNull() ?: false
                    setOnPreferenceChangeListener { _, newValue ->
                        viewModel.updateField(field.key, newValue.toString())
                        true
                    }
                }

                ThemeFieldRegistry.Type.ENUM -> ListPreference(context).apply {
                    entries = field.enumValues.toTypedArray()
                    entryValues = field.enumValues.toTypedArray()
                    value = rawValue.ifEmpty { field.defaultValue }
                    setOnPreferenceChangeListener { _, newValue ->
                        viewModel.updateField(field.key, newValue.toString())
                        true
                    }
                }

                else -> EditTextPreference(context).apply {
                    text = rawValue
                    configureFieldInput(field)
                    setOnPreferenceChangeListener { _, newValue ->
                        viewModel.updateField(field.key, newValue.toString())
                        true
                    }
                }
            }
        }
        preference.title = field.title
        preference.summary = fieldSummary(field, rawValue)
        preference.key = "theme_designer:${field.key}"
        preference.isIconSpaceReserved = false
        addPreference(preference)
    }

    private fun EditTextPreference.configureFieldInput(field: ThemeFieldRegistry.Field) {
        setOnBindEditTextListener { editText ->
            when (field.type) {
                ThemeFieldRegistry.Type.INT -> editText.inputType = SIGNED_NUMBER_INPUT
                ThemeFieldRegistry.Type.FLOAT -> editText.inputType = DECIMAL_NUMBER_INPUT
                else -> Unit
            }
        }
    }

    private fun fieldValue(
        draft: ThemeDraft,
        field: ThemeFieldRegistry.Field,
    ): String {
        val node = ThemeYamlEditor.valueAt(draft.yamlNode, field.path)
        return when (field.type) {
            ThemeFieldRegistry.Type.STRING_LIST -> node.stringListValue()
            ThemeFieldRegistry.Type.BOOLEAN -> node?.boolean?.toString() ?: node?.string.orEmpty()
            else -> node?.string.orEmpty()
        }
    }

    private fun liquidKeyboardFieldValue(
        draft: ThemeDraft,
        keyboardId: String,
        field: ThemeFieldRegistry.Field,
    ): String {
        val node = ThemeYamlEditor.valueAt(draft.yamlNode, listOf("liquid_keyboard", keyboardId) + field.path)
        return nodeValue(node, field.type)
    }

    private fun toolbarButtonFieldValue(
        draft: ThemeDraft,
        primary: Boolean,
        index: Int?,
        field: ThemeFieldRegistry.Field,
    ): String {
        val path = if (primary) {
            listOf("tool_bar", "primary_button") + field.path
        } else {
            val buttons = ThemeYamlEditor.valueAt(draft.yamlNode, listOf("tool_bar", "buttons"))?.sequence
            val item = buttons?.getOrNull(index ?: return "")
            return nodeValueAt(item, field.path, field.type)
        }
        val node = ThemeYamlEditor.valueAt(draft.yamlNode, path)
        return nodeValue(node, field.type)
    }

    private fun nodeValueAt(
        node: com.osfans.trime.util.yaml.Node?,
        path: List<String>,
        type: ThemeFieldRegistry.Type,
    ): String {
        val value = path.fold(node) { current, key ->
            (current as? com.osfans.trime.util.yaml.Node.Mapping)?.get(key)
        }
        return nodeValue(value, type)
    }

    private fun nodeValue(
        node: com.osfans.trime.util.yaml.Node?,
        type: ThemeFieldRegistry.Type,
    ): String = when (type) {
        ThemeFieldRegistry.Type.STRING_LIST -> node.stringListValue()
        ThemeFieldRegistry.Type.BOOLEAN -> node?.boolean?.toString() ?: node?.string.orEmpty()
        else -> node?.string.orEmpty()
    }

    private fun presetKeyFieldValue(
        draft: ThemeDraft,
        presetKeyId: String,
        field: ThemeFieldRegistry.Field,
    ): String {
        val node = ThemeYamlEditor.valueAt(draft.yamlNode, listOf("preset_keys", presetKeyId) + field.path)
        return when (field.type) {
            ThemeFieldRegistry.Type.STRING_LIST -> node.stringListValue()
            ThemeFieldRegistry.Type.BOOLEAN -> node?.boolean?.toString() ?: node?.string.orEmpty()
            else -> node?.string.orEmpty()
        }
    }

    private fun keyFieldValue(
        draft: ThemeDraft,
        keyboardId: String,
        index: Int,
        field: ThemeFieldRegistry.Field,
    ): String {
        val keyNode = ThemeYamlEditor.valueAt(draft.yamlNode, listOf("preset_keyboards", keyboardId, "keys"))
            ?.sequence
            ?.getOrNull(index)
        return nodeValueAt(keyNode, field.path, field.type)
    }

    private fun keyboardFieldValue(
        draft: ThemeDraft,
        keyboardId: String,
        field: ThemeFieldRegistry.Field,
    ): String {
        val node = ThemeYamlEditor.valueAt(draft.yamlNode, listOf("preset_keyboards", keyboardId) + field.path)
        return when (field.type) {
            ThemeFieldRegistry.Type.STRING_LIST -> node.stringListValue()
            ThemeFieldRegistry.Type.BOOLEAN -> node?.boolean?.toString() ?: node?.string.orEmpty()
            else -> node?.string.orEmpty()
        }
    }

    private fun com.osfans.trime.util.yaml.Node?.stringListValue(): String = this?.sequence?.mapNotNull { it.string }?.joinToString(", ")
        ?: this?.string.orEmpty()
            .ifEmpty { this?.mapping?.keys?.mapNotNull { it.string }?.joinToString(", ").orEmpty() }

    private fun ThemeFieldRegistry.Field.isColorField(): Boolean = key.endsWith("_color") || key.endsWith("_background")

    private fun ThemeFieldRegistry.Field.isFontField(): Boolean = key.endsWith("_font") || key.endsWith("button_font")

    private fun ThemeFieldRegistry.Field.isKeyboardReferenceField(): Boolean = key == "ascii_keyboard" || key == "landscape_keyboard"

    private fun fieldSummary(
        field: ThemeFieldRegistry.Field,
        rawValue: String,
    ): String = listOf(
        field.description,
        field.rangeSummary(),
        rawValue.ifEmpty { field.defaultValue },
    ).filter { it.isNotEmpty() }
        .joinToString("\n")

    private fun ThemeFieldRegistry.Field.rangeSummary(): String {
        val min = minValue
        val max = maxValue
        return when {
            min != null && max != null -> getString(
                R.string.theme_designer_field_range,
                min.formatRangeValue(),
                max.formatRangeValue(),
            )
            min != null -> getString(R.string.theme_designer_field_min, min.formatRangeValue())
            max != null -> getString(R.string.theme_designer_field_max, max.formatRangeValue())
            else -> ""
        }
    }

    private fun Double.formatRangeValue(): String = if (rem(1.0) == 0.0) toInt().toString() else toString()

    private fun userFontFiles(): List<String> {
        val fontDir = DataManager.userDataDir.resolve("fonts")
        return fontDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in FONT_EXTENSIONS }
            ?.map { it.name }
            ?.sorted()
            .orEmpty()
    }

    private fun themeFonts(draft: ThemeDraft): List<String> {
        val style = draft.theme.generalStyle
        return listOf(
            style.keyFont,
            style.labelFont,
            style.latinFont,
            style.hanbFont,
            style.symbolFont,
            style.textFont,
            style.candidateFont,
            style.commentFont,
            style.popupFont,
            draft.theme.toolBar.buttonFont,
        ).flatten().distinct().sorted()
    }

    private fun androidx.preference.PreferenceScreen.addValidation(draft: ThemeDraft) {
        addCategory(R.string.theme_designer_validation) {
            draft.validation.messages.forEach {
                addPreference(
                    Preference(context).apply {
                        title = it.level.name
                        summary = it.message
                        isIconSpaceReserved = false
                    },
                )
            }
        }
    }

    private fun ThemeDraft.statusSummary(): String {
        val errors = validation.messages.count { it.level == ThemeValidationResult.Level.ERROR }
        val warnings = validation.messages.count { it.level == ThemeValidationResult.Level.WARNING }
        val info = validation.messages.count { it.level == ThemeValidationResult.Level.INFO }
        return listOf(
            getString(
                if (dirty) {
                    R.string.theme_designer_status_unsaved
                } else {
                    R.string.theme_designer_status_saved
                },
            ),
            getString(R.string.theme_designer_status_validation, errors, warnings, info),
        ).joinToString("\n")
    }

    private companion object {
        const val SOURCE_PREVIEW_LIMIT = 400
        const val SOURCE_EDIT_MIN_LINES = 12
        const val SOURCE_EDIT_MAX_LINES = 24
        const val COLOR_PREVIEW_LIMIT = 8
        const val DELETE_REFERENCE_PREVIEW_LIMIT = 8
        const val UNKNOWN_FIELD_PREVIEW_LIMIT = 6
        const val UNKNOWN_FIELD_DIALOG_LIMIT = 20
        val SIGNED_NUMBER_INPUT = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        val DECIMAL_NUMBER_INPUT = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val FONT_EXTENSIONS = setOf("ttf", "otf", "ttc", "otc")
        val TOOLBAR_ACTION_FIELDS = setOf("action", "long_press_action")
        val COMMON_COLOR_VALUES = listOf(
            "0x000000",
            "0xffffff",
            "0xff0000",
            "0x00ff00",
            "0x0000ff",
            "0xffff00",
            "0x00ffff",
            "0xff00ff",
            "0x808080",
            "0x00000000",
        )
    }

    private sealed interface KeyboardDetail {
        val id: String

        data class Keyboard(override val id: String) : KeyboardDetail

        data class PresetKey(override val id: String) : KeyboardDetail
    }

    private enum class DesignerSection(val titleRes: Int) {
        OVERVIEW(R.string.theme_designer_overview),
        KEYBOARD_LAYOUTS(R.string.theme_designer_keyboard_layouts),
        STYLE(R.string.theme_designer_style),
        COLORS(R.string.theme_designer_colors),
        PREEDIT_WINDOW(R.string.theme_designer_preedit_window),
        TOOL_BAR(R.string.theme_designer_toolbar),
        LIQUID_KEYBOARD(R.string.theme_designer_liquid_keyboard),
        SOURCE(R.string.theme_designer_source),
    }

    private data class ReorderKeyItem(
        val originalIndex: Int,
        val label: String,
        val summary: String,
    )

    private class ReorderKeyAdapter(
        private val items: List<ReorderKeyItem>,
    ) : RecyclerView.Adapter<ReorderKeyAdapter.ViewHolder>() {
        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): ViewHolder {
            val text = TextView(parent.context).apply {
                setPadding(32, 20, 32, 20)
                textSize = 16f
            }
            return ViewHolder(text)
        }

        override fun onBindViewHolder(
            holder: ViewHolder,
            position: Int,
        ) {
            val item = items[position]
            holder.textView.text = buildString {
                append(position + 1)
                append(". ")
                append(item.label)
                if (item.summary.isNotEmpty()) {
                    append("\n")
                    append(item.summary)
                }
            }
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(
            val textView: TextView,
        ) : RecyclerView.ViewHolder(textView)
    }
}
