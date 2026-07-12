/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.designer

import com.osfans.trime.util.yaml.Yaml
import com.osfans.trime.util.yaml.mapping
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe

class ThemeReferenceScannerTest :
    BehaviorSpec({
        Given("a theme with keyboard and preset key references") {
            val node = Yaml.parseToYamlNode(
                """
                name: Reference Test
                style:
                  keyboards: [qwerty, number]
                preset_keys:
                  switch_number:
                    select: number
                tool_bar:
                  primary_button:
                    action: switch_number
                    long_press_action: switch_number
                  buttons:
                    - action: switch_number
                      long_press_action: switch_number
                liquid_keyboard:
                  fixed_key_bar:
                    keys: [switch_number]
                  keyboards: [symbols]
                  symbols:
                    type: SYMBOL
                    keys:
                      - switch_number
                      - {click: switch_number, label: Switch}
                      - {switch_number: Switch}
                preset_keyboards:
                  qwerty:
                    ascii_keyboard: number
                    landscape_keyboard: number
                    keys:
                        - click: switch_number
                          long_click: switch_number
                          swipe_up: switch_number
                          swipe_down: switch_number
                          swipe_left: switch_number
                          swipe_right: switch_number
                          ascii: switch_number
                          composing: switch_number
                          has_menu: switch_number
                          paging: switch_number
                          combo: switch_number
                          double_click: switch_number
                          lazy_double_click: switch_number
                          extra: switch_number
                          popup: [switch_number]
                          select: number
                  number:
                    keys:
                      - click: 1
                """.trimIndent(),
            ).mapping!!

            When("keyboard references are scanned") {
                val references = ThemeReferenceScanner.keyboardReferences(node, "number")

                Then("style, keyboard, key and preset key references are reported") {
                    references.shouldContainAll(
                        listOf(
                            "style/keyboards[1]",
                            "preset_keyboards/qwerty/ascii_keyboard",
                            "preset_keyboards/qwerty/landscape_keyboard",
                            "preset_keyboards/qwerty/keys[1]/select",
                            "preset_keys/switch_number/select",
                        ),
                    )
                }
            }

            When("preset key references are scanned") {
                val references = ThemeReferenceScanner.presetKeyReferences(node, "switch_number")

                Then("key action and popup references are reported") {
                    references.shouldContainAll(
                        listOf(
                            "preset_keyboards/qwerty/keys[1]/click",
                            "preset_keyboards/qwerty/keys[1]/long_click",
                            "preset_keyboards/qwerty/keys[1]/swipe_up",
                            "preset_keyboards/qwerty/keys[1]/swipe_down",
                            "preset_keyboards/qwerty/keys[1]/swipe_left",
                            "preset_keyboards/qwerty/keys[1]/swipe_right",
                            "preset_keyboards/qwerty/keys[1]/ascii",
                            "preset_keyboards/qwerty/keys[1]/composing",
                            "preset_keyboards/qwerty/keys[1]/has_menu",
                            "preset_keyboards/qwerty/keys[1]/paging",
                            "preset_keyboards/qwerty/keys[1]/combo",
                            "preset_keyboards/qwerty/keys[1]/double_click",
                            "preset_keyboards/qwerty/keys[1]/lazy_double_click",
                            "preset_keyboards/qwerty/keys[1]/extra",
                            "preset_keyboards/qwerty/keys[1]/popup[1]",
                            "tool_bar/primary_button/action",
                            "tool_bar/primary_button/long_press_action",
                            "tool_bar/buttons[1]/action",
                            "tool_bar/buttons[1]/long_press_action",
                            "liquid_keyboard/fixed_key_bar/keys[1]",
                            "liquid_keyboard/symbols/keys[1]",
                            "liquid_keyboard/symbols/keys[2]/click",
                            "liquid_keyboard/symbols/keys[3]/switch_number",
                        ),
                    )
                }
            }

            When("duplicate references are scanned") {
                val duplicateNode = Yaml.parseToYamlNode(
                    """
                    name: Duplicate References
                    style:
                      keyboards: [number, number]
                    preset_keyboards:
                      qwerty:
                        keys:
                          - click: switch_number
                            long_click: switch_number
                    """.trimIndent(),
                ).mapping!!

                Then("the result is stable and distinct") {
                    ThemeReferenceScanner.keyboardReferences(duplicateNode, "number") shouldBe
                        listOf("style/keyboards[0]", "style/keyboards[1]")
                    ThemeReferenceScanner.presetKeyReferences(duplicateNode, "switch_number") shouldBe
                        listOf(
                            "preset_keyboards/qwerty/keys[1]/click",
                            "preset_keyboards/qwerty/keys[1]/long_click",
                        )
                }
            }

            When("style keyboard references use a comma-separated string") {
                val stringNode = Yaml.parseToYamlNode(
                    """
                    name: String References
                    style:
                      keyboards: qwerty, number
                    preset_keyboards:
                      qwerty:
                        keys:
                          - click: q
                      number:
                        keys:
                          - click: 1
                    """.trimIndent(),
                ).mapping!!

                Then("the string reference is reported") {
                    ThemeReferenceScanner.keyboardReferences(stringNode, "number") shouldBe
                        listOf("style/keyboards[1]")
                }
            }

            When("style keyboard references use mapping keys") {
                val mappingNode = Yaml.parseToYamlNode(
                    """
                    name: Mapping References
                    style:
                      keyboards: { qwerty: true, number: true }
                    preset_keyboards:
                      qwerty:
                        keys:
                          - click: q
                      number:
                        keys:
                          - click: 1
                    """.trimIndent(),
                ).mapping!!

                Then("the mapping key reference is reported") {
                    ThemeReferenceScanner.keyboardReferences(mappingNode, "number") shouldBe
                        listOf("style/keyboards[1]")
                }
            }
        }
    })
