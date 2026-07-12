/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.designer

import com.osfans.trime.util.yaml.Yaml
import com.osfans.trime.util.yaml.mapping
import com.osfans.trime.util.yaml.sequence
import com.osfans.trime.util.yaml.string
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class ThemeKeyboardRenamerTest :
    BehaviorSpec({
        Given("a theme node with keyboard references") {
            val node = node()

            When("a keyboard is renamed") {
                val renamed = ThemeKeyboardRenamer.rename(node, "qwerty", "letters")

                Then("the keyboard id and all keyboard references are updated") {
                    val keyboards = renamed["preset_keyboards"]?.mapping!!
                    keyboards["qwerty"] shouldBe null
                    keyboards["letters"]?.mapping?.get("name")?.string shouldBe "Qwerty"
                    renamed["style"]?.mapping?.get("keyboards")?.sequence?.mapNotNull { it.string }
                        .shouldContainExactly("letters", "number")
                    keyboards["letters"]?.mapping?.get("ascii_keyboard")?.string shouldBe "letters"
                    keyboards["letters"]?.mapping?.get("landscape_keyboard")?.string shouldBe "letters"
                    keyboards["letters"]?.mapping
                        ?.get("keys")
                        ?.sequence
                        ?.first()
                        ?.mapping
                        ?.get("select")
                        ?.string shouldBe "letters"
                    renamed["preset_keys"]?.mapping
                        ?.get("switch_qwerty")
                        ?.mapping
                        ?.get("select")
                        ?.string shouldBe "letters"
                }
            }

            When("the target keyboard already exists") {
                Then("renaming is rejected") {
                    shouldThrow<IllegalArgumentException> {
                        ThemeKeyboardRenamer.rename(node, "qwerty", "number")
                    }.message shouldBe "Keyboard 'number' already exists"
                }
            }

            When("style keyboard references are a comma-separated string") {
                val renamed = ThemeKeyboardRenamer.rename(node("qwerty, number"), "qwerty", "letters")

                Then("the original scalar shape is preserved") {
                    renamed["style"]?.mapping?.get("keyboards")?.string shouldBe "letters, number"
                }
            }

            When("style keyboard references are mapping keys") {
                val renamed = ThemeKeyboardRenamer.rename(node("{ qwerty: true, number: true }"), "qwerty", "letters")

                Then("the original mapping shape is preserved") {
                    renamed["style"]?.mapping?.get("keyboards")?.mapping?.keys?.mapNotNull { it.string }
                        .shouldContainExactly("letters", "number")
                }
            }
        }
    }) {
    companion object {
        private fun node(styleKeyboards: String = "[qwerty, number]") = Yaml.parseToYamlNode(
            """
                name: Rename Test
                style:
                  keyboards: $styleKeyboards
                preset_keys:
                  switch_qwerty:
                    select: qwerty
                preset_keyboards:
                  qwerty:
                    name: Qwerty
                    ascii_keyboard: qwerty
                    landscape_keyboard: qwerty
                    keys:
                      - {click: a, select: qwerty}
                  number:
                    name: Number
                    keys:
                      - {click: 1}
            """.trimIndent(),
        ).mapping!!
    }
}
