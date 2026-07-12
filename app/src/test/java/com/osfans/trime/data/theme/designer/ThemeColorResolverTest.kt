/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.designer

import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.ColorScheme
import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.data.theme.model.Preedit
import com.osfans.trime.data.theme.model.ToolBar
import com.osfans.trime.data.theme.model.Window
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class ThemeColorResolverTest :
    BehaviorSpec({
        Given("a theme with fallback colors") {
            val theme = Theme(
                name = "test",
                generalStyle = GeneralStyle.decode(com.osfans.trime.util.yaml.Node.Mapping(emptyMap())),
                preedit = Preedit(),
                window = Window(),
                liquidKeyboard = LiquidKeyboard.decode(null),
                presetKeys = emptyMap(),
                presetKeyboards = emptyMap(),
                colorSchemes = listOf(
                    ColorScheme(
                        "default",
                        mapOf(
                            "text_color" to "#112233",
                            "off_key_symbol_color" to "#334455",
                        ),
                    ),
                ),
                fallbackColors = mapOf("key_text_color" to "text_color"),
                toolBar = ToolBar(),
            )

            When("a color is resolved through fallback") {
                val color = ThemeColorResolver(theme).color("key_text_color", BLACK)

                Then("the target color is returned") {
                    color shouldBe 0xff112233.toInt()
                }
            }

            When("a raw color value is resolved") {
                val color = ThemeColorResolver(theme).colorValue("0x445566", BLACK)

                Then("the literal color is returned") {
                    color shouldBe 0xff445566.toInt()
                }
            }

            When("a key-specific color field references another color key") {
                val color = ThemeColorResolver(theme).colorValue("key_text_color", BLACK)

                Then("the referenced color is returned") {
                    color shouldBe 0xff112233.toInt()
                }
            }

            When("functional key colors use built-in fallback chains") {
                val resolver = ThemeColorResolver(theme)

                Then("off and on state colors resolve through the runtime fallback table") {
                    resolver.color("off_key_text_color", BLACK) shouldBe 0xff112233.toInt()
                    resolver.color("on_key_text_color", BLACK) shouldBe 0xff112233.toInt()
                    resolver.color("hilited_on_key_text_color", BLACK) shouldBe 0xff112233.toInt()
                    resolver.color("off_key_symbol_color", BLACK) shouldBe 0xff334455.toInt()
                }
            }
        }
    }) {
    companion object {
        private const val BLACK: Int = -0x1000000
    }
}
