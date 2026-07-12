/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.designer

import com.osfans.trime.data.theme.ThemeColorFallbacks
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainAll

class ThemeColorFieldRegistryTest :
    BehaviorSpec({
        Given("the theme color field registry") {
            When("known editable color keys are inspected") {
                Then("core keyboard and candidate color keys are covered") {
                    ThemeColorFieldRegistry.colorKeys.shouldContainAll(
                        listOf(
                            "key_back_color",
                            "key_text_color",
                            "keyboard_back_color",
                            "candidate_background",
                            "candidate_text_color",
                            "hilited_candidate_back_color",
                            "root_background",
                            "text_back_color",
                            "long_text_back_color",
                        ),
                    )
                }
            }

            When("known fallback keys are inspected") {
                Then("preview and liquid keyboard color keys are covered") {
                    ThemeColorFieldRegistry.fallbackKeys.shouldContainAll(
                        listOf(
                            "preview_back_color",
                            "candidate_background",
                            "liquid_keyboard_background",
                        ),
                    )
                }
            }

            When("schema-defined color keys are inspected") {
                Then("all color keys declared by doc/trime-schema.json are editable") {
                    ThemeColorFieldRegistry.colorKeys.shouldContainAll(
                        listOf(
                            "back_color",
                            "border_color",
                            "candidate_background",
                            "candidate_separator_color",
                            "candidate_text_color",
                            "comment_text_color",
                            "hilited_back_color",
                            "hilited_candidate_back_color",
                            "hilited_candidate_button_color",
                            "hilited_candidate_text_color",
                            "hilited_comment_text_color",
                            "hilited_key_back_color",
                            "hilited_key_symbol_color",
                            "hilited_key_text_color",
                            "hilited_label_color",
                            "hilited_off_key_back_color",
                            "hilited_off_key_symbol_color",
                            "hilited_off_key_text_color",
                            "hilited_on_key_back_color",
                            "hilited_on_key_symbol_color",
                            "hilited_on_key_text_color",
                            "hilited_text_color",
                            "hilited_popup_back_color",
                            "hilited_popup_text_color",
                            "key_back_color",
                            "key_border_color",
                            "key_symbol_color",
                            "key_text_color",
                            "keyboard_back_color",
                            "keyboard_background",
                            "label_color",
                            "liquid_keyboard_background",
                            "long_text_back_color",
                            "long_text_color",
                            "off_key_back_color",
                            "off_key_symbol_color",
                            "off_key_text_color",
                            "on_key_back_color",
                            "on_key_symbol_color",
                            "on_key_text_color",
                            "preview_back_color",
                            "preview_text_color",
                            "popup_back_color",
                            "popup_text_color",
                            "root_background",
                            "shadow_color",
                            "text_back_color",
                            "text_color",
                        ),
                    )
                }
            }

            When("runtime fallback colors are inspected") {
                Then("every fallback source and target is editable") {
                    ThemeColorFieldRegistry.colorKeys.shouldContainAll(
                        ThemeColorFallbacks.values.keys + ThemeColorFallbacks.values.values,
                    )
                }
            }
        }
    })
