/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.designer

import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.data.theme.model.ToolBar
import com.osfans.trime.util.yaml.Yaml
import com.osfans.trime.util.yaml.mapping
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class ThemeFontDecodeTest :
    BehaviorSpec({
        Given("raw theme font fields") {
            When("font fields are written as scalars or sequences") {
                val node = Yaml.parseToYamlNode(
                    """
                    style:
                      candidate_font: han.ttf
                      key_font: [symbol.ttf, fallback.ttf]
                      label_font: label.ttf
                    tool_bar:
                      button_font: toolbar.ttf
                    """.trimIndent(),
                ).mapping!!
                val style = GeneralStyle.decode(node["style"]!!)
                val toolBar = ToolBar.decode(node["tool_bar"]?.mapping)

                Then("both forms are decoded as editable font lists") {
                    style.candidateFont shouldBe listOf("han.ttf")
                    style.keyFont shouldBe listOf("symbol.ttf", "fallback.ttf")
                    style.labelFont shouldBe listOf("label.ttf")
                    toolBar.buttonFont shouldBe listOf("toolbar.ttf")
                }
            }
        }
    })
