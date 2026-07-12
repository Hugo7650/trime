/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.util.yaml.Yaml
import com.osfans.trime.util.yaml.mapping
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.io.File

class KeyboardLayoutCalculatorTest :
    BehaviorSpec({
        Given("a simple two-row keyboard") {
            val key = TextKeyboard.TextKey(
                width = 0f,
                height = 0f,
                roundCorner = -1f,
                keyBorder = -1,
                label = "",
                labelSymbol = "",
                hint = "",
                click = "a",
                sendBindings = true,
                keyTextSize = 0f,
                symbolTextSize = 0f,
                keyTextOffsetX = 0f,
                keyTextOffsetY = 0f,
                keySymbolOffsetX = 0f,
                keySymbolOffsetY = 0f,
                keyHintOffsetX = 0f,
                keyHintOffsetY = 0f,
                keyPressOffsetX = 0f,
                keyPressOffsetY = 0f,
                keyTextColor = "",
                keyBackColor = "",
                keySymbolColor = "",
                hlKeyTextColor = "",
                hlKeyBackColor = "",
                hlKeySymbolColor = "",
                behaviors = mapOf(KeyBehavior.CLICK to "a"),
            )
            val keyboard = TextKeyboard(
                name = "test",
                author = "",
                width = 50f,
                height = 10f,
                keyboardHeight = 0,
                keyboardHeightLand = 0,
                autoHeightIndex = -1,
                horizontalGap = 0,
                verticalGap = 0,
                roundCorner = -1f,
                keyBorder = -1,
                columns = 2,
                asciiMode = false,
                resetAsciiMode = true,
                labelTransform = TextKeyboard.LabelTransform.NONE,
                lock = false,
                asciiKeyboard = "",
                landscapeKeyboard = "",
                landscapeSplitPercent = 0,
                keyTextOffsetX = 0f,
                keyTextOffsetY = 0f,
                keySymbolOffsetX = 0f,
                keySymbolOffsetY = 0f,
                keyHintOffsetX = 0f,
                keyHintOffsetY = 0f,
                keyPressOffsetX = 0f,
                keyPressOffsetY = 0f,
                importPreset = "",
                keys = listOf(key, key, key),
            )

            When("layout is calculated") {
                val layout = KeyboardLayoutCalculator(
                    allowedWidth = 100,
                    defaultKeyHeight = 10,
                    keyboardHeight = 20,
                    landscapeSplitPercent = 0,
                    expandKeypressArea = false,
                ).calculate(keyboard)

                Then("keys wrap by column count and fill the expected rows") {
                    layout.keys.map { it.row } shouldBe listOf(0, 0, 1)
                    layout.keys.map { it.column } shouldBe listOf(0, 1, 0)
                    layout.keys.map { it.width } shouldBe listOf(50, 50, 50)
                    layout.height shouldBe 20
                }
            }

            When("layout is calculated with zero keyboard height and zero row height") {
                val zeroHeightKeyboard = keyboard.copy(height = 0f)
                val layout = KeyboardLayoutCalculator(
                    allowedWidth = 100,
                    defaultKeyHeight = 0,
                    keyboardHeight = 0,
                    landscapeSplitPercent = 0,
                    expandKeypressArea = false,
                ).calculate(zeroHeightKeyboard)

                Then("the layout remains stable with zero-height rows") {
                    layout.keys.map { it.height } shouldBe listOf(0, 0, 0)
                    layout.height shouldBe 0
                }
            }

            When("the first key in a row is wider than the available width") {
                val wideKeyboard = keyboard.copy(
                    width = 120f,
                    columns = -1,
                    keys = listOf(key.copy(width = 120f)),
                )
                val layout = KeyboardLayoutCalculator(
                    allowedWidth = 100,
                    defaultKeyHeight = 10,
                    keyboardHeight = 10,
                    landscapeSplitPercent = 0,
                    expandKeypressArea = false,
                ).calculate(wideKeyboard)

                Then("the key is placed on the first row without creating an empty row") {
                    layout.keys.map { it.row } shouldBe listOf(0)
                    layout.keys.map { it.y } shouldBe listOf(0)
                    layout.height shouldBe 10
                }
            }

            When("an empty-click key is used as a spacer") {
                val narrowKey = key.copy(width = 25f)
                val spacerKeyboard = keyboard.copy(
                    width = 25f,
                    columns = -1,
                    keys = listOf(narrowKey, narrowKey.copy(click = "", behaviors = emptyMap()), narrowKey),
                )
                val layout = KeyboardLayoutCalculator(
                    allowedWidth = 100,
                    defaultKeyHeight = 10,
                    keyboardHeight = 10,
                    landscapeSplitPercent = 0,
                    expandKeypressArea = false,
                ).calculate(spacerKeyboard)

                Then("the spacer consumes width without producing a clickable key") {
                    layout.keys.size shouldBe 2
                    layout.keys.map { it.x } shouldBe listOf(0, 50)
                    layout.keys.map { it.row } shouldBe listOf(0, 0)
                }
            }

            When("a landscape center split is applied") {
                val splitKeyboard = keyboard.copy(
                    width = 10f,
                    columns = 10,
                    keys = List(10) { key.copy(width = 10f) },
                )
                val layout = KeyboardLayoutCalculator(
                    allowedWidth = 100,
                    defaultKeyHeight = 10,
                    keyboardHeight = 10,
                    landscapeSplitPercent = 50,
                    expandKeypressArea = false,
                ).calculate(splitKeyboard)

                Then("a stable center gap is inserted without overlapping keys") {
                    layout.keys.map { it.x } shouldBe listOf(0, 6, 12, 18, 24, 63, 69, 75, 81, 87)
                    layout.keys.zipWithNext().all { (left, right) -> left.x + left.width <= right.x } shouldBe true
                    layout.keys.map { it.row }.distinct() shouldBe listOf(0)
                }
            }
        }

        Given("the default trime theme keyboard layouts") {
            val node = Yaml.parseToYamlNode(File("src/test/assets/trime.yaml").readText()).mapping!!
            val theme = Theme.decode(node)

            listOf(
                DefaultLayoutExpectation("qwerty", listOf(10, 9, 9, 7), listOf(62, 62, 62, 64)),
                DefaultLayoutExpectation("number", listOf(5, 5, 5, 5, 5), List(5) { 50 }),
                DefaultLayoutExpectation("symbols", listOf(10, 10, 10, 10, 7), List(5) { 50 }),
            ).forEach { expectation ->
                When("${expectation.id} is calculated from the deployed model") {
                    val keyboard = theme.presetKeyboards.getValue(expectation.id)
                    val layout = KeyboardLayoutCalculator(
                        allowedWidth = 1000,
                        defaultKeyHeight = keyboard.height.toInt().takeIf { it > 0 } ?: theme.generalStyle.keyHeight,
                        keyboardHeight = keyboard.keyboardHeight.takeIf { it > 0 } ?: theme.generalStyle.keyboardHeight,
                        landscapeSplitPercent = 0,
                        expandKeypressArea = false,
                    ).calculate(keyboard)

                    Then("its row structure and dimensions match the default layout baseline") {
                        layout.keys.groupingBy { it.row }.eachCount().values.toList() shouldBe expectation.keysPerRow
                        layout.keys.groupBy { it.row }.values.map { row -> row.first().height } shouldBe expectation.rowHeights
                        layout.keys.groupBy { it.row }.values.all { row ->
                            row.zipWithNext().all { (left, right) -> left.x + left.width <= right.x }
                        } shouldBe true
                        layout.height shouldBe 250
                        layout.minWidth shouldBe 1000
                    }
                }
            }
        }
    }) {
    private data class DefaultLayoutExpectation(
        val id: String,
        val keysPerRow: List<Int>,
        val rowHeights: List<Int>,
    )
}
