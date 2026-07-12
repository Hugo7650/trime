/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import android.os.Parcelable
import androidx.navigation.NavController
import androidx.navigation.createGraph
import androidx.navigation.fragment.fragment
import com.osfans.trime.R
import com.osfans.trime.ui.main.settings.AdvancedSettingsFragment
import com.osfans.trime.ui.main.settings.CandidatesSettingsFragment
import com.osfans.trime.ui.main.settings.ClipboardSettingsFragment
import com.osfans.trime.ui.main.settings.GeneralSettingsFragment
import com.osfans.trime.ui.main.settings.KeyboardSettingsFragment
import com.osfans.trime.ui.main.settings.ProfileSettingsFragment
import com.osfans.trime.ui.main.settings.schema.SchemaListFragment
import com.osfans.trime.ui.main.settings.theme.ThemeSettingsFragment
import com.osfans.trime.ui.main.settings.theme.designer.ThemeDesignerFragment
import com.osfans.trime.ui.main.settings.theme.designer.ThemeKeyboardEditorFragment
import com.osfans.trime.ui.main.settings.theme.designer.ThemeKeyboardHubFragment
import com.osfans.trime.ui.main.settings.theme.designer.ThemeKeyboardKeyEditorFragment
import com.osfans.trime.ui.main.settings.theme.designer.ThemeKeyboardKeyListFragment
import com.osfans.trime.ui.main.settings.theme.designer.ThemeKeyboardListFragment
import com.osfans.trime.ui.main.settings.theme.designer.ThemeKeyboardPreviewFragment
import com.osfans.trime.ui.main.settings.theme.designer.ThemeLiquidKeyboardEditorFragment
import com.osfans.trime.ui.main.settings.theme.designer.ThemeLiquidKeyboardGlobalFragment
import com.osfans.trime.ui.main.settings.theme.designer.ThemeLiquidKeyboardHubFragment
import com.osfans.trime.ui.main.settings.theme.designer.ThemeLiquidKeyboardKeyEditorFragment
import com.osfans.trime.ui.main.settings.theme.designer.ThemeLiquidKeyboardKeyListFragment
import com.osfans.trime.ui.main.settings.theme.designer.ThemeLiquidKeyboardListFragment
import com.osfans.trime.ui.main.settings.theme.designer.ThemePresetKeyEditorFragment
import com.osfans.trime.ui.main.settings.theme.designer.ThemePresetKeyListFragment
import com.osfans.trime.ui.main.settings.userdict.UserDictionaryFragment
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
sealed class NavigationRoute : Parcelable {

    @Serializable
    data object Main : NavigationRoute()

    @Serializable
    data object SchemaList : NavigationRoute()

    @Serializable
    data object UserDict : NavigationRoute()

    @Serializable
    data object Profile : NavigationRoute()

    @Serializable
    data object General : NavigationRoute()

    @Serializable
    data object VirtualKeyboard : NavigationRoute()

    @Serializable
    data object CandidatesWindow : NavigationRoute()

    @Serializable
    data object Theme : NavigationRoute()

    @Serializable
    data object ThemeDesigner : NavigationRoute()

    @Parcelize
    @Serializable
    data class ThemeDesignerModule(val section: String) : NavigationRoute()

    @Serializable
    data object ThemeKeyboardHub : NavigationRoute()

    @Serializable
    data object ThemeKeyboardList : NavigationRoute()

    @Serializable
    data object ThemePresetKeyList : NavigationRoute()

    @Parcelize
    @Serializable
    data class ThemeKeyboardEditor(val keyboardId: String) : NavigationRoute()

    @Parcelize
    @Serializable
    data class ThemeKeyboardKeyList(val keyboardId: String) : NavigationRoute()

    @Parcelize
    @Serializable
    data class ThemeKeyboardPreview(val keyboardId: String) : NavigationRoute()

    @Parcelize
    @Serializable
    data class ThemeKeyboardKeyEditor(
        val keyboardId: String,
        val draftKeyId: String,
    ) : NavigationRoute()

    @Parcelize
    @Serializable
    data class ThemePresetKeyEditor(val presetKeyId: String) : NavigationRoute()

    @Serializable
    data object ThemeLiquidKeyboardList : NavigationRoute()

    @Serializable
    data object ThemeLiquidKeyboardHub : NavigationRoute()

    @Serializable
    data object ThemeLiquidKeyboardGlobal : NavigationRoute()

    @Parcelize
    @Serializable
    data class ThemeLiquidKeyboardEditor(val keyboardId: String) : NavigationRoute()

    @Parcelize
    @Serializable
    data class ThemeLiquidKeyboardKeyList(val keyboardId: String) : NavigationRoute()

    @Parcelize
    @Serializable
    data class ThemeLiquidKeyboardKeyEditor(
        val keyboardId: String,
        val draftKeyId: String,
    ) : NavigationRoute()

    @Serializable
    data object Clipboard : NavigationRoute()

    @Serializable
    data object Advanced : NavigationRoute()

    @Serializable
    data object Developer : NavigationRoute()

    @Serializable
    data object About : NavigationRoute()

    @Serializable
    data object License : NavigationRoute()

    companion object {
        fun createGraph(controller: NavController) = controller.createGraph(Main) {
            val ctx = controller.context

            fragment<MainFragment, Main> {
                label = ctx.getString(R.string.trime_app_name)
            }

            fragment<SchemaListFragment, SchemaList> {
                label = ctx.getString(R.string.schemata)
            }
            fragment<UserDictionaryFragment, UserDict> {
                label = ctx.getString(R.string.user_dictionary)
            }
            fragment<ProfileSettingsFragment, Profile> {
                label = ctx.getString(R.string.profile)
            }

            fragment<GeneralSettingsFragment, General> {
                label = ctx.getString(R.string.general)
            }
            fragment<KeyboardSettingsFragment, VirtualKeyboard> {
                label = ctx.getString(R.string.virtual_keyboard)
            }
            fragment<CandidatesSettingsFragment, CandidatesWindow> {
                label = ctx.getString(R.string.candidates_window)
            }
            fragment<ThemeSettingsFragment, Theme> {
                label = ctx.getString(R.string.theme)
            }
            fragment<ThemeDesignerFragment, ThemeDesigner> {
                label = ctx.getString(R.string.theme_designer)
            }
            fragment<ThemeDesignerFragment, ThemeDesignerModule> {
                label = ctx.getString(R.string.theme_designer)
            }
            fragment<ThemeKeyboardHubFragment, ThemeKeyboardHub> {
                label = ctx.getString(R.string.theme_designer_keyboard_layouts)
            }
            fragment<ThemeKeyboardListFragment, ThemeKeyboardList> {
                label = ctx.getString(R.string.theme_designer_keyboard_layouts)
            }
            fragment<ThemePresetKeyListFragment, ThemePresetKeyList> {
                label = ctx.getString(R.string.theme_designer_preset_keys)
            }
            fragment<ThemeKeyboardEditorFragment, ThemeKeyboardEditor> {
                label = ctx.getString(R.string.theme_designer_keyboard_layouts)
            }
            fragment<ThemeKeyboardKeyListFragment, ThemeKeyboardKeyList> {
                label = ctx.getString(R.string.theme_designer_keyboard_keys)
            }
            fragment<ThemeKeyboardPreviewFragment, ThemeKeyboardPreview> {
                label = ctx.getString(R.string.theme_designer_preview)
            }
            fragment<ThemeKeyboardKeyEditorFragment, ThemeKeyboardKeyEditor> {
                label = ctx.getString(R.string.theme_designer_keyboard_key)
            }
            fragment<ThemePresetKeyEditorFragment, ThemePresetKeyEditor> {
                label = ctx.getString(R.string.theme_designer_preset_keys)
            }
            fragment<ThemeLiquidKeyboardListFragment, ThemeLiquidKeyboardList> {
                label = ctx.getString(R.string.theme_designer_liquid_keyboard)
            }
            fragment<ThemeLiquidKeyboardHubFragment, ThemeLiquidKeyboardHub> {
                label = ctx.getString(R.string.theme_designer_liquid_keyboard)
            }
            fragment<ThemeLiquidKeyboardGlobalFragment, ThemeLiquidKeyboardGlobal> {
                label = ctx.getString(R.string.theme_designer_liquid_keyboard_global)
            }
            fragment<ThemeLiquidKeyboardEditorFragment, ThemeLiquidKeyboardEditor> {
                label = ctx.getString(R.string.theme_designer_liquid_keyboard)
            }
            fragment<ThemeLiquidKeyboardKeyListFragment, ThemeLiquidKeyboardKeyList> {
                label = ctx.getString(R.string.theme_designer_keyboard_keys)
            }
            fragment<ThemeLiquidKeyboardKeyEditorFragment, ThemeLiquidKeyboardKeyEditor> {
                label = ctx.getString(R.string.theme_designer_keyboard_key)
            }
            fragment<ClipboardSettingsFragment, Clipboard> {
                label = ctx.getString(R.string.clipboard)
            }
            fragment<AdvancedSettingsFragment, Advanced> {
                label = ctx.getString(R.string.advanced)
            }
            fragment<DeveloperFragment, Developer> {
                label = ctx.getString(R.string.developer)
            }
            fragment<AboutFragment, About> {
                label = ctx.getString(R.string.about)
            }
            fragment<LicenseFragment, License> {
                label = ctx.getString(R.string.license)
            }
        }
    }
}
