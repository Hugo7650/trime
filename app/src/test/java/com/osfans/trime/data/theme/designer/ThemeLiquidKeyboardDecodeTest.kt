/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.designer

import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.util.yaml.Yaml
import com.osfans.trime.util.yaml.mapping
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class ThemeLiquidKeyboardDecodeTest :
    BehaviorSpec({
        Given("a liquid keyboard node with schema root fields") {
            val node = Yaml.parseToYamlNode(
                """
                author: tester
                row: 6
                row_land: 4
                single_width: 80
                key_height: 36
                key_height_land: 30
                vertical_gap: 2
                margin_x: 1.5
                keyboards: [tabs]
                tabs:
                  name: Tabs
                  type: TABS
                  keys: [a]
                """.trimIndent(),
            ).mapping!!

            When("decoded") {
                val liquidKeyboard = LiquidKeyboard.decode(node)

                Then("schema root fields are retained by the model") {
                    liquidKeyboard.author shouldBe "tester"
                    liquidKeyboard.row shouldBe 6
                    liquidKeyboard.rowLand shouldBe 4
                    liquidKeyboard.singleWidth shouldBe 80
                    liquidKeyboard.keyHeight shouldBe 36
                    liquidKeyboard.keyHeightLand shouldBe 30
                    liquidKeyboard.verticalGap shouldBe 2
                    liquidKeyboard.marginX shouldBe 1.5f
                }
            }
        }
    })
