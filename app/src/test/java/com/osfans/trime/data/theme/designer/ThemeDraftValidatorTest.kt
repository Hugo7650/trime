/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.designer

import com.osfans.trime.util.yaml.Node
import com.osfans.trime.util.yaml.Yaml
import com.osfans.trime.util.yaml.mapping
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class ThemeDraftValidatorTest :
    BehaviorSpec({
        Given("a minimal theme draft") {
            When("style references a missing keyboard") {
                val result = ThemeDraftValidator.validate(
                    themeNode(
                        styleKeyboards = "[missing]",
                    ),
                )

                Then("deployment is blocked with an error") {
                    result.hasErrors shouldBe true
                    result.messages.map { it.message } shouldContain
                        "style/keyboards references missing preset keyboard 'missing'"
                }
            }

            When("style references keyboards by mapping keys") {
                val result = ThemeDraftValidator.validate(
                    themeNode(
                        styleKeyboards = "{ qwerty: true, missing: true }",
                    ),
                )

                Then("missing mapping keys are reported") {
                    result.hasErrors shouldBe true
                    result.messages.map { it.message } shouldContain
                        "style/keyboards references missing preset keyboard 'missing'"
                }
            }

            When("style references a special keyboard by mapping key") {
                val result = ThemeDraftValidator.validate(
                    themeNode(
                        styleKeyboards = "{ qwerty: true, .default: true }",
                    ),
                )

                Then("no missing keyboard error is reported") {
                    result.messages.any { it.message.contains("style/keyboards references missing preset keyboard '.default'") } shouldBe false
                }
            }

            When("style references an unknown dotted keyboard") {
                val result = ThemeDraftValidator.validate(
                    themeNode(
                        styleKeyboards = "{ qwerty: true, .missing: true }",
                    ),
                )

                Then("the dotted reference is reported") {
                    result.hasErrors shouldBe true
                    result.messages.map { it.message } shouldContain
                        "style/keyboards references missing preset keyboard '.missing'"
                }
            }

            When("a key references a missing select target") {
                val result = ThemeDraftValidator.validate(
                    themeNode(
                        keyExtra = "select: missing",
                    ),
                )

                Then("a warning is reported") {
                    result.messages.map { it.message } shouldContain
                        "preset_keyboards/qwerty has select target 'missing' that is not defined"
                }
            }

            When("a keyboard references a missing ascii keyboard") {
                val result = ThemeDraftValidator.validate(
                    themeNode(
                        keyboardExtra = "ascii_keyboard: missing",
                    ),
                )

                Then("a warning is reported") {
                    result.messages.map { it.message } shouldContain
                        "preset_keyboards/qwerty ascii_keyboard target 'missing' is not defined"
                }
            }

            When("a keyboard uses a dynamic command as its landscape keyboard") {
                val result = ThemeDraftValidator.validate(
                    themeNode(
                        keyboardExtra = "landscape_keyboard: .next",
                    ),
                )

                Then("a warning is reported because a concrete layout is required") {
                    result.messages.map { it.message } shouldContain
                        "preset_keyboards/qwerty landscape_keyboard target '.next' is not defined"
                }
            }

            When("a keyboard references an unknown dotted landscape keyboard") {
                val result = ThemeDraftValidator.validate(
                    themeNode(
                        keyboardExtra = "landscape_keyboard: .missing",
                    ),
                )

                Then("a warning is reported") {
                    result.messages.map { it.message } shouldContain
                        "preset_keyboards/qwerty landscape_keyboard target '.missing' is not defined"
                }
            }

            When("a preset key references a missing select target") {
                val result = ThemeDraftValidator.validate(
                    themeNode(
                        presetKeys = """
                          switch_missing:
                            select: missing
                        """.trimIndent(),
                    ),
                )

                Then("a warning is reported") {
                    result.messages.map { it.message } shouldContain
                        "preset_keys/switch_missing select target 'missing' is not defined"
                }
            }

            When("a preset key references a special select target") {
                val result = ThemeDraftValidator.validate(
                    themeNode(
                        presetKeys = """
                          switch_next:
                            select: .next
                        """.trimIndent(),
                    ),
                )

                Then("no missing keyboard warning is reported") {
                    result.messages.any { it.message.contains("preset_keys/switch_next select target '.next'") } shouldBe false
                }
            }

            When("preset keys reference every runtime special keyboard target") {
                val presetKeys = ThemeKeyboardReferences.specialIds.joinToString("\n") { id ->
                    "  switch_${id.removePrefix(".")}: {select: $id}"
                }
                val yaml =
                    """
                    name: Special targets
                    style:
                      keyboard_height: 200
                      keyboards: [qwerty]
                    preset_keys:
                    __PRESET_KEYS__
                    preset_color_schemes:
                      default:
                        name: Default
                    preset_keyboards:
                      qwerty:
                        width: 100
                        keys:
                          - {click: a}
                    """.trimIndent().replace("__PRESET_KEYS__", presetKeys)
                val node = com.osfans.trime.util.yaml.Yaml.parseToYamlNode(yaml).mapping!!

                Then("none of the runtime special targets is reported missing") {
                    ThemeDraftValidator.validate(node).messages.none {
                        it.message.contains("select target") && it.message.contains("is not defined")
                    } shouldBe true
                }
            }

            When("a preset key references an unknown dotted select target") {
                val result = ThemeDraftValidator.validate(
                    themeNode(
                        presetKeys = """
                          switch_missing:
                            select: .missing
                        """.trimIndent(),
                    ),
                )

                Then("a warning is reported") {
                    result.messages.map { it.message } shouldContain
                        "preset_keys/switch_missing select target '.missing' is not defined"
                }
            }

            When("a key references an unknown color") {
                val result = ThemeDraftValidator.validate(
                    themeNode(
                        keyExtra = "key_text_color: unknown_color",
                    ),
                )

                Then("a warning is reported") {
                    result.messages.map { it.message } shouldContain
                        "preset_keyboards/qwerty key 1 references missing color 'unknown_color'"
                }
            }

            When("fallback colors contain a cycle") {
                val node = ThemeYamlEditor.updateNode(
                    themeNode(),
                    listOf("fallback_colors"),
                    com.osfans.trime.util.yaml.Yaml.parseToYamlNode(
                        "{key_text_color: label_color, label_color: key_text_color}",
                    ).mapping!!,
                )

                Then("an error prevents deployment from entering ColorManager's infinite lookup") {
                    val result = ThemeDraftValidator.validate(node)
                    result.hasErrors shouldBe true
                    result.messages.any { it.message.contains("fallback_colors contains cycle") } shouldBe true
                }
            }

            When("a key references a schema-defined color key") {
                val result = ThemeDraftValidator.validate(
                    themeNode(
                        keyExtra = "key_text_color: label_color",
                    ),
                )

                Then("no missing color warning is reported") {
                    result.messages.any { it.message.contains("references missing color 'label_color'") } shouldBe false
                }
            }

            When("fallback colors reference schema-defined color keys") {
                val result = ThemeDraftValidator.validate(
                    themeNode(
                        fallbackColors = """
                          preview_text_color: preview_back_color
                        """.trimIndent(),
                    ),
                )

                Then("no missing fallback color warning is reported") {
                    result.messages.any {
                        it.message.contains("fallback_colors/preview_text_color references missing color 'preview_back_color'")
                    } shouldBe false
                }
            }

            When("a color scheme references an unknown color") {
                val result = ThemeDraftValidator.validate(
                    themeNode(
                        colorSchemeExtra = "label_color: missing_color",
                    ),
                )

                Then("a warning is reported") {
                    result.messages.map { it.message } shouldContain
                        "preset_color_schemes/default/label_color references missing color 'missing_color'"
                }
            }

            When("a theme has no color schemes") {
                val result = ThemeDraftValidator.validate(
                    ThemeYamlEditor.remove(themeNode(), listOf("preset_color_schemes", "default")),
                )

                Then("deployment is blocked before ColorManager can receive an empty list") {
                    result.hasErrors shouldBe true
                    result.messages.map { it.message } shouldContain
                        "preset_color_schemes must contain at least one color scheme"
                }
            }

            When("a day-night color scheme references a missing scheme") {
                val result = ThemeDraftValidator.validate(
                    themeNode(
                        colorSchemeExtra = "dark_scheme: missing_dark",
                    ),
                )

                Then("a color scheme warning is reported instead of a color warning") {
                    result.messages.map { it.message } shouldContain
                        "preset_color_schemes/default/dark_scheme references missing color scheme 'missing_dark'"
                    result.messages.any {
                        it.message == "preset_color_schemes/default/dark_scheme references missing color 'missing_dark'"
                    } shouldBe false
                }
            }

            When("a day-night color scheme references an existing scheme") {
                val result = ThemeDraftValidator.validate(
                    themeNode(
                        colorSchemeExtra = "light_scheme: default",
                    ),
                )

                Then("no missing scheme warning is reported") {
                    result.messages.any { it.message.contains("light_scheme references missing") } shouldBe false
                }
            }

            When("a custom color alias references an unknown color") {
                val result = ThemeDraftValidator.validate(
                    themeNode(
                        colorSchemeExtra = """
                          name: Default
                          custom_alias: missing_color
                        """.trimIndent(),
                    ),
                )

                Then("the alias is checked without treating metadata as a color") {
                    result.messages.map { it.message } shouldContain
                        "preset_color_schemes/default/custom_alias references missing color 'missing_color'"
                    result.messages.any {
                        it.message.contains("preset_color_schemes/default/name references missing color")
                    } shouldBe false
                }
            }

            When("a row width is unusually large") {
                val result = ThemeDraftValidator.validate(
                    themeNode(
                        keyWidth = 130,
                    ),
                )

                Then("a row width warning is reported") {
                    result.messages.map { it.message } shouldContain
                        "preset_keyboards/qwerty row 1 width is unusually large: 130.0"
                }
            }

            When("keyboard columns force narrow rows") {
                val keys = Node.Sequence(
                    List(4) { index ->
                        Node.Mapping(
                            Node.Scalar("click") to Node.Scalar(index.toString()),
                            Node.Scalar("width") to Node.Scalar("10"),
                        )
                    },
                )
                val withColumns = ThemeYamlEditor.updateScalar(
                    themeNode(),
                    listOf("preset_keyboards", "qwerty", "columns"),
                    "2",
                )
                val result = ThemeDraftValidator.validate(
                    ThemeYamlEditor.updateNode(withColumns, listOf("preset_keyboards", "qwerty", "keys"), keys),
                )

                Then("each column-wrapped row is checked") {
                    result.messages.map { it.message } shouldContain
                        "preset_keyboards/qwerty row 1 width is unusually small: 20.0"
                    result.messages.map { it.message } shouldContain
                        "preset_keyboards/qwerty row 2 width is unusually small: 20.0"
                }
            }

            When("keyboard columns is zero") {
                val node = ThemeYamlEditor.updateScalar(
                    themeNode(),
                    listOf("preset_keyboards", "qwerty", "columns"),
                    "0",
                )
                val result = ThemeDraftValidator.validate(node)

                Then("deployment is blocked with an explicit columns error") {
                    result.hasErrors shouldBe true
                    result.messages.map { it.message } shouldContain
                        "preset_keyboards/qwerty columns must be -1 or greater than 0"
                }
            }

            When("the next key would overflow the row width") {
                val keys = Node.Sequence(
                    listOf(
                        Node.Mapping(
                            Node.Scalar("click") to Node.Scalar("a"),
                            Node.Scalar("width") to Node.Scalar("30"),
                        ),
                        Node.Mapping(
                            Node.Scalar("click") to Node.Scalar("b"),
                            Node.Scalar("width") to Node.Scalar("80"),
                        ),
                    ),
                )
                val result = ThemeDraftValidator.validate(
                    ThemeYamlEditor.updateNode(themeNode(), listOf("preset_keyboards", "qwerty", "keys"), keys),
                )

                Then("the row before the overflow is checked independently") {
                    result.messages.map { it.message } shouldContain
                        "preset_keyboards/qwerty row 1 width is unusually small: 30.0"
                }
            }
        }
    }) {
    companion object {
        private fun themeNode(
            styleKeyboards: String = "[qwerty]",
            keyWidth: Int = 100,
            keyExtra: String = "",
            keyboardExtra: String = "",
            presetKeys: String = "",
            colorSchemeExtra: String = "",
            fallbackColors: String = "",
        ) = Yaml.parseToYamlNode(
            """
            name: Validator Test
            style:
              keyboards: $styleKeyboards
              key_width: 10
              key_height: 50
              keyboard_height: 220
            preset_keys:
              ${presetKeys.prependIndent("              ").trimStart()}
            preset_color_schemes:
              default:
                key_text_color: "#000000"
                key_back_color: "#ffffff"
                ${colorSchemeExtra.prependIndent("                ").trimStart()}
            fallback_colors:
              ${fallbackColors.prependIndent("              ").trimStart()}
            preset_keyboards:
              qwerty:
                width: 10
                height: 50
                ${keyboardExtra.prependIndent("                ").trimStart()}
                keys:
                  - click: q
                    width: $keyWidth
                    ${keyExtra.prependIndent("                    ").trimStart()}
            """.trimIndent(),
        ).mapping!!
    }
}
