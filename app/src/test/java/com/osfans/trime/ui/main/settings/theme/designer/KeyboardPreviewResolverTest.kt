/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.theme.designer

import com.osfans.trime.data.theme.model.PresetKey
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.ime.keyboard.KeyBehavior
import com.osfans.trime.util.yaml.Yaml
import com.osfans.trime.util.yaml.mapping
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class KeyboardPreviewResolverTest :
    BehaviorSpec({
        Given("a keyboard with a dedicated landscape layout") {
            val portrait = keyboard("portrait", "landscape")
            val landscape = keyboard("landscape")

            When("the landscape preview keyboard is resolved") {
                Then("the referenced layout is used") {
                    KeyboardPreviewResolver.landscapeKeyboard(
                        portrait,
                        mapOf("portrait" to portrait, "landscape" to landscape),
                    ) shouldBe landscape
                }
            }

            When("the referenced layout is missing") {
                Then("the current layout is retained") {
                    KeyboardPreviewResolver.landscapeKeyboard(
                        portrait,
                        mapOf("portrait" to portrait),
                    ) shouldBe portrait
                }
            }

            When("a layout defines its own landscape split") {
                val splitKeyboard = keyboard("split", splitPercent = 35)

                Then("the layout value takes priority over the user preference") {
                    KeyboardPreviewResolver.landscapeSplitPercent(splitKeyboard, 20) shouldBe 35
                }
            }

            When("a layout does not define a landscape split") {
                Then("the user preference is used") {
                    KeyboardPreviewResolver.landscapeSplitPercent(portrait, 20) shouldBe 20
                }
            }

            When("multiple runtime conditions are active") {
                val key = key(
                    """
                    click: Return
                    composing: Commit
                    paging: Page_Down
                    """.trimIndent(),
                )

                Then("the first configured action in the IME priority order is selected") {
                    KeyboardPreviewResolver.action(
                        key,
                        KeyboardPreviewState(asciiMode = true, paging = true, composing = true),
                    ) shouldBe KeyboardPreviewResolver.Action(KeyBehavior.PAGING, "Page_Down")
                }
            }

            When("a normal key has an explicit label") {
                val key = key("{click: BackSpace, label: Delete}")
                val state = KeyboardPreviewState()
                val action = KeyboardPreviewResolver.action(key, state)

                Then("the explicit label is shown instead of the raw action") {
                    KeyboardPreviewResolver.label(key, action, state, emptyMap()) shouldBe "Delete"
                }
            }

            When("a conditional action references a preset key") {
                val key = key("{click: a, paging: Page_Down}")
                val state = KeyboardPreviewState(paging = true)
                val action = KeyboardPreviewResolver.action(key, state)

                Then("the preset key label is shown") {
                    KeyboardPreviewResolver.label(
                        key,
                        action,
                        state,
                        mapOf("Page_Down" to PresetKey(label = "Next page")),
                    ) shouldBe "Next page"
                }
            }

            When("an action references a functional preset key") {
                val key = key("{click: BackSpace}")
                val action = KeyboardPreviewResolver.action(key, KeyboardPreviewState())

                Then("the preview marks it for off-state functional colors") {
                    KeyboardPreviewResolver.isFunctional(
                        action,
                        mapOf("BackSpace" to PresetKey(functional = true)),
                    ) shouldBe true
                }
            }
        }
    }) {
    companion object {
        private fun keyboard(
            id: String,
            landscapeKeyboard: String = "",
            splitPercent: Int = 0,
        ): TextKeyboard = TextKeyboard.decode(
            Yaml.parseToYamlNode(
                """
                name: $id
                landscape_keyboard: $landscapeKeyboard
                landscape_split_percent: $splitPercent
                width: 100
                height: 50
                keys:
                  - {click: $id}
                """.trimIndent(),
            ).mapping!!,
        )

        private fun key(yaml: String): TextKeyboard.TextKey = TextKeyboard.TextKey.decode(
            Yaml.parseToYamlNode(yaml).mapping!!,
        )
    }
}
