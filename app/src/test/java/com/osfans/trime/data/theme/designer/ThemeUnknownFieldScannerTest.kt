/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.designer

import com.osfans.trime.ime.keyboard.KeyBehavior
import com.osfans.trime.util.yaml.Node
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder

class ThemeUnknownFieldScannerTest :
    BehaviorSpec({
        Given("a theme YAML node with known dynamic sections and unknown fields") {
            val node = Node.Mapping(
                Node.Scalar("name") to Node.Scalar("test"),
                Node.Scalar("style") to Node.Mapping(
                    Node.Scalar("keyboards") to Node.Sequence(Node.Scalar("qwerty")),
                    Node.Scalar("enter_labels") to Node.Mapping(
                        Node.Scalar("go") to Node.Scalar("Go"),
                    ),
                    Node.Scalar("preview_font") to Node.Scalar("latin.ttf"),
                    Node.Scalar("preview_height") to Node.Scalar("60"),
                    Node.Scalar("preview_offset") to Node.Scalar("-12"),
                    Node.Scalar("preview_text_size") to Node.Scalar("40"),
                    Node.Scalar("comment_on_top") to Node.Scalar("false"),
                    Node.Scalar("proximity_correction") to Node.Scalar("false"),
                    Node.Scalar("vertical_correction") to Node.Scalar("-10"),
                    Node.Scalar("unknown_style") to Node.Scalar("x"),
                ),
                Node.Scalar("preset_keyboards") to Node.Mapping(
                    Node.Scalar("qwerty") to Node.Mapping(
                        Node.Scalar("name") to Node.Scalar("qwerty"),
                        Node.Scalar("keys") to Node.Sequence(
                            Node.Mapping(
                                *KeyBehavior.entries
                                    .map { Node.Scalar(it.name.lowercase()) to Node.Scalar(it.name.lowercase()) }
                                    .toTypedArray(),
                                Node.Scalar("unknown_key") to Node.Scalar("x"),
                            ),
                        ),
                        Node.Scalar("unknown_keyboard") to Node.Scalar("x"),
                    ),
                ),
                Node.Scalar("preset_color_schemes") to Node.Mapping(
                    Node.Scalar("default") to Node.Mapping(
                        Node.Scalar("key_text_color") to Node.Scalar("0x000000"),
                    ),
                ),
                Node.Scalar("liquid_keyboard") to Node.Mapping(
                    Node.Scalar("author") to Node.Scalar("tester"),
                    Node.Scalar("row") to Node.Scalar("6"),
                    Node.Scalar("row_land") to Node.Scalar("5"),
                    Node.Scalar("keyboards") to Node.Sequence(Node.Scalar("tabs")),
                    Node.Scalar("key_height_land") to Node.Scalar("40"),
                    Node.Scalar("vertical_gap") to Node.Scalar("1"),
                    Node.Scalar("tabs") to Node.Mapping(
                        Node.Scalar("name") to Node.Scalar("Tabs"),
                        Node.Scalar("type") to Node.Scalar("TABS"),
                        Node.Scalar("unknown_liquid") to Node.Scalar("x"),
                    ),
                ),
                Node.Scalar("unknown_root") to Node.Scalar("x"),
            )

            When("unknown fields are scanned") {
                val unknown = ThemeUnknownFieldScanner.scan(node)

                Then("dynamic ids are ignored and actual unknown paths are reported") {
                    unknown.shouldContainExactlyInAnyOrder(
                        listOf(
                            "preset_keyboards/qwerty/keys/1/unknown_key",
                            "preset_keyboards/qwerty/unknown_keyboard",
                            "liquid_keyboard/tabs/unknown_liquid",
                            "style/unknown_style",
                            "unknown_root",
                        ),
                    )
                }
            }
        }

        Given("a theme YAML node with nested preedit, window, and toolbar fields") {
            val node = Node.Mapping(
                Node.Scalar("preedit") to Node.Mapping(
                    Node.Scalar("horizontal_padding") to Node.Scalar("8"),
                    Node.Scalar("foreground") to Node.Mapping(
                        Node.Scalar("font_size") to Node.Scalar("16"),
                    ),
                ),
                Node.Scalar("window") to Node.Mapping(
                    Node.Scalar("insets") to Node.Mapping(
                        Node.Scalar("vertical") to Node.Scalar("4"),
                    ),
                    Node.Scalar("foreground") to Node.Mapping(
                        Node.Scalar("text_font_size") to Node.Scalar("20"),
                    ),
                ),
                Node.Scalar("tool_bar") to Node.Mapping(
                    Node.Scalar("button_spacing") to Node.Scalar("18"),
                    Node.Scalar("primary_button") to Node.Mapping(
                        Node.Scalar("action") to Node.Scalar("Keyboard_default"),
                        Node.Scalar("background") to Node.Mapping(
                            Node.Scalar("type") to Node.Scalar("rectangle"),
                        ),
                    ),
                    Node.Scalar("buttons") to Node.Sequence(
                        Node.Mapping(
                            Node.Scalar("foreground") to Node.Mapping(
                                Node.Scalar("font_size") to Node.Scalar("18"),
                            ),
                            Node.Scalar("unknown_button") to Node.Scalar("x"),
                        ),
                    ),
                ),
            )

            When("unknown fields are scanned") {
                val unknown = ThemeUnknownFieldScanner.scan(node)

                Then("known nested form paths are ignored and unknown toolbar button paths are reported") {
                    unknown.shouldContainExactlyInAnyOrder(
                        listOf("tool_bar/buttons/1/unknown_button"),
                    )
                }
            }
        }
    })
