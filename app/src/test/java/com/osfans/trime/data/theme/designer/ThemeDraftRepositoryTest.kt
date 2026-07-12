/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.designer

import com.osfans.trime.data.theme.Theme
import com.osfans.trime.util.yaml.Node
import com.osfans.trime.util.yaml.Yaml
import com.osfans.trime.util.yaml.mapping
import com.osfans.trime.util.yaml.sequence
import com.osfans.trime.util.yaml.string
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.io.File

class ThemeDraftRepositoryTest :
    BehaviorSpec({
        Given("a theme draft repository") {
            val repository = ThemeDraftRepository()

            When("a keyboard is renamed with an id that needs normalization") {
                val draft = draft()

                val renamed = repository.renameKeyboard(draft, "qwerty", "letters layout")

                Then("the normalized id is used and references are updated") {
                    renamed.theme.presetKeyboards.keys shouldBe setOf("letters_layout", "number")
                    renamed.yamlNode["style"]?.mapping?.get("keyboards")?.sequence?.mapNotNull { it.string }
                        .shouldContainExactly("letters_layout", "number")
                    renamed.dirty shouldBe true
                }
            }

            When("a keyboard is renamed to an existing id") {
                val draft = draft()

                Then("the conflict is reported instead of silently generating another id") {
                    shouldThrow<IllegalArgumentException> {
                        repository.renameKeyboard(draft, "qwerty", "number")
                    }.message shouldBe "Keyboard 'number' already exists"
                }
            }

            When("a keyboard is deleted from comma-separated style references") {
                val draft = draft(styleKeyboards = "qwerty, number")

                val deleted = repository.deleteKeyboard(draft, "qwerty")

                Then("the string reference is preserved without the deleted keyboard") {
                    deleted.theme.presetKeyboards.keys shouldBe setOf("number")
                    deleted.yamlNode["style"]?.mapping?.get("keyboards")?.string shouldBe "number"
                    deleted.dirty shouldBe true
                }
            }

            When("a keyboard is deleted from mapping style references") {
                val draft = draft(styleKeyboards = "{ qwerty: true, number: true }")

                val deleted = repository.deleteKeyboard(draft, "qwerty")

                Then("the mapping reference is preserved without the deleted keyboard") {
                    deleted.theme.presetKeyboards.keys shouldBe setOf("number")
                    deleted.yamlNode["style"]?.mapping?.get("keyboards")?.mapping?.keys?.mapNotNull { it.string }
                        .shouldContainExactly("number")
                    deleted.dirty shouldBe true
                }
            }

            When("color and fallback color fields are edited") {
                val draft = draft()

                val withColor = repository.updateColor(draft, "default", "key_text_color", "0x112233")
                val withFallback = repository.updateFallbackColor(withColor, "label_color", "key_text_color")
                val reparsed = Yaml.parseToYamlNode(withFallback.yamlText).mapping!!

                Then("the generated draft YAML remains parseable and decodable") {
                    Theme.decode(reparsed).colorSchemes.first().colors["key_text_color"] shouldBe "0x112233"
                    Theme.decode(reparsed).fallbackColors["label_color"] shouldBe "key_text_color"
                    withFallback.dirty shouldBe true
                }
            }

            When("color and fallback color fields are cleared") {
                val draft = draft()

                val withFallback = repository.updateFallbackColor(draft, "label_color", "key_text_color")
                val withoutColor = repository.updateColor(withFallback, "default", "key_text_color", "")
                val withoutFallback = repository.updateFallbackColor(withoutColor, "label_color", " ")
                val reparsed = Yaml.parseToYamlNode(withoutFallback.yamlText).mapping!!

                Then("the cleared fields are removed instead of retained as blank values") {
                    Theme.decode(reparsed).colorSchemes.first().colors["key_text_color"] shouldBe null
                    Theme.decode(reparsed).fallbackColors["label_color"] shouldBe null
                    reparsed["preset_color_schemes"]
                        ?.mapping
                        ?.get("default")
                        ?.mapping
                        ?.get("key_text_color") shouldBe null
                    reparsed["fallback_colors"]?.mapping?.get("label_color") shouldBe null
                    withoutFallback.dirty shouldBe true
                }
            }

            When("the last color scheme is deleted") {
                Then("the edit is rejected because ColorManager requires a fallback scheme") {
                    shouldThrow<IllegalArgumentException> {
                        repository.deleteColorScheme(draft(), "default")
                    }.message shouldBe "A theme must contain at least one color scheme"
                }
            }

            When("one of multiple color schemes is deleted") {
                val withCustom = repository.createColorScheme(draft(), "custom")
                val deleted = repository.deleteColorScheme(withCustom, "default")

                Then("the remaining scheme keeps the theme deployable") {
                    deleted.theme.colorSchemes.map { it.id } shouldBe listOf("custom")
                    deleted.validation.hasErrors shouldBe false
                }
            }

            When("liquid keyboard key actions and labels are edited") {
                val withKeyboard = repository.createLiquidKeyboard(draft(), "symbols")
                val withKey = repository.insertLiquidKeyboardKey(withKeyboard, "symbols", 0)
                val edited = repository.updateLiquidKeyboardKey(withKey, "symbols", 0, "exclamation", "!")
                val copied = repository.copyLiquidKeyboardKey(edited, "symbols", 0)
                val moved = repository.moveLiquidKeyboardKey(copied, "symbols", 1, 0)
                val deleted = repository.deleteLiquidKeyboardKey(moved, "symbols", 1)
                val keys = deleted.theme.liquidKeyboard.keyboards.single { it.id == "symbols" }.keys

                Then("CRUD operations preserve both the action and alternate display label") {
                    keys.map { it.text } shouldBe listOf("exclamation")
                    keys.map { it.altText } shouldBe listOf("!")
                    deleted.yamlNode["liquid_keyboard"]
                        ?.mapping
                        ?.get("symbols")
                        ?.mapping
                        ?.get("keys")
                        ?.sequence
                        ?.first()
                        ?.mapping
                        ?.get("label")
                        ?.string shouldBe "!"
                }
            }

            When("liquid keyboard keys are reordered") {
                val withKeyboard = repository.createLiquidKeyboard(draft(), "symbols")
                val withFirst = repository.insertLiquidKeyboardKey(withKeyboard, "symbols", 0)
                val firstEdited = repository.updateLiquidKeyboardKey(withFirst, "symbols", 0, "first", "1")
                val withSecond = repository.insertLiquidKeyboardKey(firstEdited, "symbols", 1)
                val secondEdited = repository.updateLiquidKeyboardKey(withSecond, "symbols", 1, "second", "2")
                val reordered = repository.reorderLiquidKeyboardKeys(secondEdited, "symbols", listOf(1, 0))
                val reparsed = Theme.decode(Yaml.parseToYamlNode(reordered.yamlText).mapping!!)
                val keys = reparsed.liquidKeyboard.keyboards.single { it.id == "symbols" }.keys

                Then("actions and labels move together and generated YAML remains decodable") {
                    keys.map { it.text } shouldBe listOf("second", "first")
                    keys.map { it.altText } shouldBe listOf("2", "1")
                    reordered.dirty shouldBe true
                }
            }

            When("toolbar button actions are edited") {
                val actionField = ThemeFieldRegistry.toolbarButtonFields.first { it.key == "action" }
                val longPressField = ThemeFieldRegistry.toolbarButtonFields.first { it.key == "long_press_action" }
                val withPrimary = repository.setPrimaryToolbarButton(draft())
                val primaryEdited = repository.updatePrimaryToolbarButtonField(withPrimary, actionField, "BackSpace")
                val withButton = repository.insertToolbarButton(primaryEdited, 0)
                val edited = repository.updateToolbarButtonField(withButton, 0, longPressField, "Menu")
                val reparsed = Theme.decode(Yaml.parseToYamlNode(edited.yamlText).mapping!!)

                Then("primary and list button actions survive normalization") {
                    reparsed.toolBar.primaryButton?.action shouldBe "BackSpace"
                    reparsed.toolBar.buttons.single().longPressAction shouldBe "Menu"
                    edited.dirty shouldBe true
                }
            }

            When("toolbar button size values are edited") {
                val sizeField = ThemeFieldRegistry.toolbarButtonFields.first { it.key == "size" }
                val withPrimary = repository.setPrimaryToolbarButton(draft())

                Then("only non-negative integer lists are accepted") {
                    shouldThrow<IllegalArgumentException> {
                        repository.updatePrimaryToolbarButtonField(withPrimary, sizeField, "wide, 24")
                    }.message shouldBe "Size values must be integers"
                    shouldThrow<IllegalArgumentException> {
                        repository.updatePrimaryToolbarButtonField(withPrimary, sizeField, "-1, 24")
                    }.message shouldBe "Size values must be at least 0"
                    repository.updatePrimaryToolbarButtonField(withPrimary, sizeField, "24, 32")
                        .theme.toolBar.primaryButton?.size shouldBe listOf(24, 32)
                }
            }

            When("keyboard keys are reordered") {
                val draft = draft()

                val reordered = repository.reorderKeyboardKeys(draft, "qwerty", "2, 1")
                val reparsed = Yaml.parseToYamlNode(reordered.yamlText).mapping!!

                Then("key order is updated and the generated YAML remains decodable") {
                    Theme.decode(reparsed).presetKeyboards["qwerty"]?.keys?.map { it.click } shouldBe listOf("w", "q")
                    reordered.dirty shouldBe true
                }
            }

            When("a key width edit is saved and reloaded") {
                val file = kotlin.io.path.createTempFile("theme-designer-key-width", ".yaml").toFile()
                val original = draft(file = file)
                file.writeText(original.yamlText)

                val updated = repository.updateKeyboardKeyField(original, "qwerty", 0, "width", "40")
                val saved = repository.saveDraft(updated)
                val persisted = Theme.decode(Yaml.parseToYamlNode(file.readText()).mapping!!)

                Then("the edited width survives normalization and reload") {
                    saved.theme.presetKeyboards.getValue("qwerty").keys.first().width shouldBe 40f
                    persisted.presetKeyboards.getValue("qwerty").keys.first().width shouldBe 40f
                    ThemeDraftFileStrategy.backupFile(file).exists() shouldBe true
                }
            }

            When("popup keys contain separator characters") {
                val file = kotlin.io.path.createTempFile("theme-designer-key-popup", ".yaml").toFile()
                val original = draft(file = file)
                file.writeText(original.yamlText)

                val popup = listOf(",", " ", "Preset,Name")
                val updated = repository.updateKeyboardKeyPopup(original, "qwerty", 0, popup)
                repository.saveDraft(updated)
                val persisted = Theme.decode(Yaml.parseToYamlNode(file.readText()).mapping!!)

                Then("the popup list survives normalization without delimiter splitting") {
                    persisted.presetKeyboards.getValue("qwerty").keys.first().popup shouldBe popup
                }
            }

            When("a numeric field receives a non-numeric value") {
                val field = ThemeFieldRegistry.keyboardFields.first { it.key == "width" }

                Then("the edit is rejected before the draft is rebuilt") {
                    shouldThrow<IllegalArgumentException> {
                        repository.updateKeyboardField(draft(), "qwerty", field, "wide")
                    }.message shouldBe "Keyboard key width must be a number"
                }
            }

            When("a numeric field exceeds its registered range") {
                val field = ThemeFieldRegistry.keyboardFields.first { it.key == "landscape_split_percent" }

                Then("the range violation is reported") {
                    shouldThrow<IllegalArgumentException> {
                        repository.updateKeyboardField(draft(), "qwerty", field, "101")
                    }.message shouldBe "Landscape split percent must be at most 100"
                }
            }

            When("an enum field receives an unsupported value") {
                val field = ThemeFieldRegistry.keyboardFields.first { it.key == "label_transform" }

                Then("the supported values are reported") {
                    shouldThrow<IllegalArgumentException> {
                        repository.updateKeyboardField(draft(), "qwerty", field, "capitalize")
                    }.message shouldBe "Label transform must be one of: none, uppercase"
                }
            }

            When("keyboard columns receives zero") {
                val field = ThemeFieldRegistry.keyboardFields.first { it.key == "columns" }

                Then("the special unlimited value and positive values are enforced") {
                    shouldThrow<IllegalArgumentException> {
                        repository.updateKeyboardField(draft(), "qwerty", field, "0")
                    }.message shouldBe "Columns must be -1 or greater than 0"
                    repository.updateKeyboardField(draft(), "qwerty", field, "-1")
                        .theme.presetKeyboards.getValue("qwerty").columns shouldBe -1
                }
            }

            When("keyboard keys are batch resized beyond the supported width") {
                Then("the invalid batch edit is rejected") {
                    shouldThrow<IllegalArgumentException> {
                        repository.resizeKeyboardKeys(draft(), "qwerty", "120", "")
                    }.message shouldBe "Width must be at most 100"
                }
            }

            When("advanced source YAML is edited") {
                val draft = draft()
                val updated = repository.updateSource(
                    draft,
                    """
                    __include: base:/
                    name: Source Edited
                    style:
                      __patch:
                        key_width: 99
                      key_width: 12
                      key_height: 50
                      keyboard_height: 220
                    android_keys:
                      KEYCODE_TEST: 1
                    preset_keyboards:
                      qwerty:
                        width: 12
                        height: 50
                        keys:
                          - {click: q, width: 100}
                    preset_color_schemes:
                      default:
                        name: Default
                        key_text_color: text_color
                        text_color: 0x000000
                    """.trimIndent(),
                )
                val reparsed = Yaml.parseToYamlNode(updated.yamlText).mapping!!

                Then("the source is normalized, decodable, and ordinary unknown sections are retained") {
                    Theme.decode(reparsed).name shouldBe "Source Edited"
                    reparsed["__include"] shouldBe null
                    updated.yamlNode["__include"] shouldBe null
                    updated.yamlNode["style"]?.mapping?.get("__patch") shouldBe null
                    reparsed["android_keys"]?.mapping?.get("KEYCODE_TEST")?.string shouldBe "1"
                    updated.dirty shouldBe true
                }
            }

            When("a source-edited draft is saved") {
                val file = kotlin.io.path.createTempFile("theme-designer-save", ".yaml").toFile()
                val draft = draft(file = file)
                val updated = repository.updateSource(
                    draft,
                    """
                    __include: base:/
                    name: Saved Source
                    author: Tester
                    config_version: "1"
                    style:
                      key_width: 10
                      key_height: 50
                      keyboard_height: 220
                    preset_keyboards:
                      qwerty:
                        width: 10
                        height: 50
                        keys:
                          - {click: q, width: 100}
                    preset_color_schemes:
                      default:
                        name: Default
                        key_text_color: text_color
                        text_color: 0x000000
                    """.trimIndent(),
                )

                val saved = repository.saveDraft(updated)
                val reparsed = Yaml.parseToYamlNode(file.readText()).mapping!!

                Then("the persisted draft is normalized and decodable") {
                    saved.dirty shouldBe false
                    Theme.decode(reparsed).name shouldBe "Saved Source"
                    reparsed["__include"] shouldBe null
                    file.readText() shouldBe saved.yamlText
                }
            }

            When("a draft with validation errors is deployed") {
                val invalid = repository.updateSource(
                    draft(),
                    """
                    name: Invalid Deploy
                    style:
                      keyboards: [missing]
                      key_width: 10
                      key_height: 50
                      keyboard_height: 220
                    preset_keyboards:
                      qwerty:
                        width: 10
                        height: 50
                        keys:
                          - {click: q, width: 100}
                    preset_color_schemes:
                      default:
                        name: Default
                        key_text_color: text_color
                        text_color: 0x000000
                    """.trimIndent(),
                )

                Then("deployment is rejected before native Rime deployment is invoked") {
                    shouldThrow<IllegalArgumentException> {
                        repository.deployDraft(invalid)
                    }.message shouldBe
                        "Theme draft has validation errors: style/keyboards references missing preset keyboard 'missing'"
                }
            }
        }
    }) {
    companion object {
        private fun draft(
            styleKeyboards: String = "[qwerty, number]",
            file: File = File("base.designer.trime.yaml"),
        ): ThemeDraft {
            val node = Yaml.parseToYamlNode(
                """
                name: Repository Test
                style:
                  keyboards: $styleKeyboards
                  key_width: 10
                  key_height: 50
                  keyboard_height: 220
                preset_keyboards:
                  qwerty:
                    width: 10
                    height: 50
                    keys:
                      - {click: q, width: 50}
                      - {click: w, width: 50}
                  number:
                    width: 10
                    height: 50
                    keys:
                      - {click: 1, width: 100}
                preset_color_schemes:
                  default:
                    name: Default
                    key_text_color: text_color
                    text_color: 0x000000
                """.trimIndent(),
            ).mapping!!
            return ThemeDraft(
                theme = Theme.decode(node),
                sourceThemeId = "base",
                derivedThemeId = "base.designer.trime",
                file = file,
                yamlNode = node,
                yamlText = ThemeYamlWriter.write(node),
                dirty = false,
                validation = ThemeDraftValidator.validate(node),
            )
        }
    }
}
