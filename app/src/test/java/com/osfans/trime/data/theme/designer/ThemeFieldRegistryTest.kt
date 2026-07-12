/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.designer

import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.ime.keyboard.KeyBehavior
import com.osfans.trime.ime.symbol.LiquidData
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe

class ThemeFieldRegistryTest :
    BehaviorSpec({
        Given("the theme field registry") {
            When("global scalar and list model paths are inspected") {
                val paths = ThemeFieldRegistry.fields.map { it.key }

                Then("the current decoded style, preedit, window, toolbar and liquid roots are covered") {
                    paths.shouldContainAll(
                        listOf(
                            "name",
                            "author",
                            "config_version",
                            "style/keyboards",
                            "style/auto_caps",
                            "style/candidate_border",
                            "style/candidate_border_round",
                            "style/candidate_font",
                            "style/candidate_padding",
                            "style/candidate_spacing",
                            "style/candidate_text_size",
                            "style/candidate_text_vertical_bias",
                            "style/candidate_view_height",
                            "style/candidate_corner_radius",
                            "style/comment_font",
                            "style/comment_height",
                            "style/comment_on_top",
                            "style/comment_position",
                            "style/comment_text_size",
                            "style/comment_vertical_bias",
                            "style/hanb_font",
                            "style/horizontal_gap",
                            "style/keyboard_padding",
                            "style/keyboard_padding_left",
                            "style/keyboard_padding_right",
                            "style/keyboard_padding_bottom",
                            "style/keyboard_padding_land",
                            "style/keyboard_padding_land_bottom",
                            "style/key_font",
                            "style/key_border",
                            "style/key_height",
                            "style/key_long_text_size",
                            "style/key_text_size",
                            "style/key_text_offset_x",
                            "style/key_text_offset_y",
                            "style/key_symbol_offset_x",
                            "style/key_symbol_offset_y",
                            "style/key_hint_offset_x",
                            "style/key_hint_offset_y",
                            "style/key_press_offset_x",
                            "style/key_press_offset_y",
                            "style/key_width",
                            "style/label_text_size",
                            "style/label_font",
                            "style/latin_font",
                            "style/keyboard_height",
                            "style/keyboard_height_land",
                            "style/popup_bottom_margin",
                            "style/popup_width",
                            "style/popup_height",
                            "style/popup_key_height",
                            "style/popup_font",
                            "style/popup_text_size",
                            "style/reset_ascii_mode_on_focus_change",
                            "style/round_corner",
                            "style/shadow_radius",
                            "style/symbol_font",
                            "style/symbol_text_size",
                            "style/text_font",
                            "style/vertical_correction",
                            "style/preview_font",
                            "style/preview_height",
                            "style/preview_offset",
                            "style/preview_text_size",
                            "style/proximity_correction",
                            "style/vertical_gap",
                            "style/background_folder",
                            "style/enter_label_mode",
                            "style/enter_labels/go",
                            "style/enter_labels/done",
                            "style/enter_labels/next",
                            "style/enter_labels/pre",
                            "style/enter_labels/search",
                            "style/enter_labels/send",
                            "style/enter_labels/default",
                            "preedit/horizontal_padding",
                            "preedit/top_end_radius",
                            "preedit/alpha",
                            "preedit/foreground/font_size",
                            "window/insets/vertical",
                            "window/insets/horizontal",
                            "window/item_padding/vertical",
                            "window/item_padding/horizontal",
                            "window/min_width",
                            "window/corner_radius",
                            "window/border",
                            "window/shadow",
                            "window/alpha",
                            "window/foreground/label_font_size",
                            "window/foreground/text_font_size",
                            "window/foreground/comment_font_size",
                            "tool_bar/button_spacing",
                            "tool_bar/button_font",
                            "tool_bar/back_style",
                            "liquid_keyboard/author",
                            "liquid_keyboard/row",
                            "liquid_keyboard/row_land",
                            "liquid_keyboard/single_width",
                            "liquid_keyboard/key_height",
                            "liquid_keyboard/key_height_land",
                            "liquid_keyboard/vertical_gap",
                            "liquid_keyboard/margin_x",
                            "liquid_keyboard/keyboards",
                            "liquid_keyboard/fixed_key_bar/position",
                            "liquid_keyboard/fixed_key_bar/keys",
                        ),
                    )
                }
            }

            When("schema enum fields are inspected") {
                val autoCaps = ThemeFieldRegistry.field("style/auto_caps")

                Then("auto_caps preserves the schema-supported ascii mode") {
                    autoCaps?.type shouldBe ThemeFieldRegistry.Type.ENUM
                    autoCaps?.enumValues shouldBe listOf("false", "true", "ascii")
                }
            }

            When("schema ranged fields are inspected") {
                val enterLabelMode = ThemeFieldRegistry.field("style/enter_label_mode")
                val preeditAlpha = ThemeFieldRegistry.field("preedit/alpha")
                val windowBorder = ThemeFieldRegistry.field("window/border")
                val windowMinWidth = ThemeFieldRegistry.field("window/min_width")

                Then("known schema minimum and maximum values are retained") {
                    enterLabelMode?.minValue shouldBe 0.0
                    enterLabelMode?.maxValue shouldBe 3.0
                    preeditAlpha?.minValue shouldBe 0.0
                    preeditAlpha?.maxValue shouldBe 1.0
                    windowBorder?.minValue shouldBe 0.0
                    windowBorder?.maxValue shouldBe 24.0
                    windowMinWidth?.minValue shouldBe 0.0
                    windowMinWidth?.maxValue shouldBe 640.0
                }
            }

            When("keyboard scalar model paths are inspected") {
                val paths = ThemeFieldRegistry.keyboardFields.map { it.key }

                Then("the current decoded keyboard properties are covered") {
                    paths.shouldContainAll(
                        listOf(
                            "name",
                            "author",
                            "width",
                            "height",
                            "keyboard_height",
                            "keyboard_height_land",
                            "auto_height_index",
                            "horizontal_gap",
                            "vertical_gap",
                            "round_corner",
                            "key_border",
                            "columns",
                            "ascii_mode",
                            "reset_ascii_mode",
                            "label_transform",
                            "lock",
                            "ascii_keyboard",
                            "landscape_keyboard",
                            "landscape_split_percent",
                            "key_text_offset_x",
                            "key_text_offset_y",
                            "key_symbol_offset_x",
                            "key_symbol_offset_y",
                            "key_hint_offset_x",
                            "key_hint_offset_y",
                            "key_press_offset_x",
                            "key_press_offset_y",
                            "import_preset",
                        ),
                    )
                }
            }

            When("keyboard layout ranged fields are inspected") {
                val width = ThemeFieldRegistry.keyboardFields.first { it.key == "width" }
                val split = ThemeFieldRegistry.keyboardFields.first { it.key == "landscape_split_percent" }
                val columns = ThemeFieldRegistry.keyboardFields.first { it.key == "columns" }

                Then("layout limits are retained for editor controls") {
                    width.minValue shouldBe 0.0
                    width.maxValue shouldBe 100.0
                    split.minValue shouldBe 0.0
                    split.maxValue shouldBe 100.0
                    columns.minValue shouldBe -1.0
                    columns.maxValue shouldBe 100.0
                }
            }

            When("preset key model paths are inspected") {
                val paths = ThemeFieldRegistry.presetKeyFields.map { it.key }

                Then("the current decoded preset key properties are covered") {
                    paths.shouldContainAll(
                        listOf(
                            "command",
                            "option",
                            "select",
                            "toggle",
                            "label",
                            "preview",
                            "shift_lock",
                            "commit",
                            "text",
                            "sticky",
                            "repeatable",
                            "slide_cursor",
                            "slide_delete",
                            "functional",
                            "states",
                            "send",
                        ),
                    )
                }
            }

            When("keyboard key model paths are inspected") {
                val paths = ThemeFieldRegistry.keyFields.map { it.key }

                Then("the current decoded key properties are covered") {
                    paths.shouldContainAll(
                        listOf(
                            "width",
                            "height",
                            "round_corner",
                            "key_border",
                            "label",
                            "label_symbol",
                            "hint",
                            "click",
                            "send_bindings",
                            "key_text_size",
                            "symbol_text_size",
                            "key_text_offset_x",
                            "key_text_offset_y",
                            "key_symbol_offset_x",
                            "key_symbol_offset_y",
                            "key_hint_offset_x",
                            "key_hint_offset_y",
                            "key_press_offset_x",
                            "key_press_offset_y",
                            "key_text_color",
                            "key_back_color",
                            "key_symbol_color",
                            "hilited_key_text_color",
                            "hilited_key_back_color",
                            "hilited_key_symbol_color",
                        ),
                    )
                }
            }

            When("keyboard key ranged fields are inspected") {
                val width = ThemeFieldRegistry.keyFields.first { it.key == "width" }
                val roundCorner = ThemeFieldRegistry.keyFields.first { it.key == "round_corner" }
                val textSize = ThemeFieldRegistry.keyFields.first { it.key == "key_text_size" }

                Then("key layout and text limits are retained for editor controls") {
                    width.minValue shouldBe 0.0
                    width.maxValue shouldBe 100.0
                    roundCorner.minValue shouldBe -1.0
                    roundCorner.maxValue shouldBe 48.0
                    textSize.minValue shouldBe 0.0
                    textSize.maxValue shouldBe 64.0
                }
            }

            When("keyboard key action behaviors are inspected") {
                val behaviors = ThemeFieldRegistry.keyActionBehaviors

                Then("gesture and alternate key behavior fields are exposed without duplicating click") {
                    behaviors shouldBe KeyBehavior.entries.filterNot { it == KeyBehavior.CLICK }
                }
            }

            When("toolbar button model paths are inspected") {
                val paths = ThemeFieldRegistry.toolbarButtonFields.map { it.key }

                Then("the current decoded toolbar button properties are covered") {
                    paths.shouldContainAll(
                        listOf(
                            "action",
                            "long_press_action",
                            "size",
                            "background/type",
                            "background/corner_radius",
                            "background/normal",
                            "background/highlight",
                            "background/vertical_inset",
                            "background/horizontal_inset",
                            "foreground/style",
                            "foreground/option_styles",
                            "foreground/normal",
                            "foreground/highlight",
                            "foreground/font_size",
                            "foreground/padding",
                        ),
                    )
                }
            }

            When("toolbar button numeric fields are inspected") {
                val fields = ThemeFieldRegistry.toolbarButtonFields.associateBy { it.key }

                Then("dimensions reject negative values") {
                    fields.getValue("background/corner_radius").minValue shouldBe 0.0
                    fields.getValue("background/vertical_inset").minValue shouldBe 0.0
                    fields.getValue("background/horizontal_inset").minValue shouldBe 0.0
                    fields.getValue("foreground/font_size").minValue shouldBe 0.0
                    fields.getValue("foreground/padding").minValue shouldBe 0.0
                }
            }

            When("liquid keyboard entry model paths are inspected") {
                val paths = ThemeFieldRegistry.liquidKeyboardFields.map { it.key }

                Then("the current decoded liquid keyboard entry properties are covered") {
                    paths.shouldContainAll(
                        listOf(
                            "name",
                            "type",
                            "keys",
                        ),
                    )
                }
            }

            When("liquid keyboard types are inspected") {
                val type = ThemeFieldRegistry.liquidKeyboardFields.first { it.key == "type" }
                val position = ThemeFieldRegistry.field("liquid_keyboard/fixed_key_bar/position")

                Then("the editor exposes every runtime liquid keyboard enum value") {
                    type.enumValues shouldBe LiquidData.Type.entries.map { it.name }
                    position?.enumValues shouldBe LiquidKeyboard.KeyBar.Position.entries.map { it.name.lowercase() }
                }
            }
        }
    })
