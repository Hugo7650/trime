/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.designer

import com.osfans.trime.util.yaml.mapping
import com.osfans.trime.util.yaml.string
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class RimeDeployedThemeYamlTest :
    BehaviorSpec({
        Given("Rime output containing reserved plain scalar prefixes") {
            val source =
                """
                name: Test
                preset_keys:
                  Search:
                    option: %s
                  Mention:
                    option: @name
                  Literal:
                    option: `code`
                  Colon:
                    send: :
                """.trimIndent()

            When("the deployed theme is parsed") {
                val node = RimeDeployedThemeYaml.parse(source)
                val keys = node["preset_keys"]?.mapping!!

                Then("reserved scalars retain their exact values") {
                    keys["Search"]?.mapping?.get("option")?.string shouldBe "%s"
                    keys["Mention"]?.mapping?.get("option")?.string shouldBe "@name"
                    keys["Literal"]?.mapping?.get("option")?.string shouldBe "`code`"
                    keys["Colon"]?.mapping?.get("send")?.string shouldBe ":"
                }
            }
        }

        Given("strictly valid quoted and plain scalars") {
            val source = "name: Test\nauthor: \"Rime community\"\n"

            Then("the compatibility pass leaves them unchanged") {
                RimeDeployedThemeYaml.quoteReservedPlainScalars(source) shouldBe source
            }
        }
    })
