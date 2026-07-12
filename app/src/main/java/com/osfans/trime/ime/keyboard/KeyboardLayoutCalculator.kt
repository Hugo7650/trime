/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import com.osfans.trime.data.theme.model.TextKeyboard
import kotlin.math.abs

class KeyboardLayoutCalculator(
    private val allowedWidth: Int,
    private val defaultKeyHeight: Int,
    private val keyboardHeight: Int,
    private val landscapeSplitPercent: Int,
    private val expandKeypressArea: Boolean,
) {
    data class Layout(
        val keys: List<KeyLayout>,
        val spacers: List<Spacer>,
        val height: Int,
        val minWidth: Int,
        val lastRow: Int,
    )

    data class KeyLayout(
        val source: TextKeyboard.TextKey,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val row: Int,
        val column: Int,
    )

    data class Spacer(
        val x: Int,
        val width: Int,
        val row: Int,
    )

    fun calculate(keyboard: TextKeyboard): Layout {
        val keys = keyboard.keys
        val keyboardKeyWidth = keyboard.width
        val maxColumns = if (keyboard.columns == -1) Int.MAX_VALUE else keyboard.columns
        val splitRatio = if (landscapeSplitPercent > 0) landscapeSplitPercent / 100f else 0f
        val oneWeightWidthPx = allowedWidth.toFloat() / (MAX_TOTAL_WEIGHT * (1 + splitRatio))
        val rowWidthTotalWeight = mutableListOf<Float>()
        val rowRawHeight = mutableListOf<Int>()

        var x = 0
        var column = 0
        var rowHeight = defaultKeyHeight
        var totalKeyWidth = 0f

        keys.forEach { key ->
            val keyWidthWeight = key.widthWeight(keyboardKeyWidth)
            val widthPx = (keyWidthWeight * allowedWidth / MAX_TOTAL_WEIGHT).toInt()

            if (column >= maxColumns || (column > 0 && x + widthPx > allowedWidth)) {
                rowWidthTotalWeight.add(totalKeyWidth)
                rowRawHeight.add(rowHeight)
                x = 0
                column = 0
                totalKeyWidth = 0f
            }

            if (column == 0) {
                rowHeight = if (key.height > 0) key.height.toInt() else defaultKeyHeight
            }

            totalKeyWidth += keyWidthWeight
            if (key.click.isNotEmpty()) {
                column++
            }
            x += widthPx
        }

        rowWidthTotalWeight.add(totalKeyWidth)
        rowRawHeight.add(rowHeight)

        val rowHeightScaled = scaleRowHeights(rowRawHeight)
        val keyLayouts = mutableListOf<KeyLayout>()
        val spacers = mutableListOf<Spacer>()

        var xPos = 0
        var yPos = 0
        var row = 0
        column = 0
        var rowWeightAccumulo = 0f
        var currentRowHeight = rowHeightScaled[0]
        var splitInserted = false
        var minWidth = 0

        keys.forEach { textKey ->
            val keyWidthWeight = textKey.widthWeight(keyboardKeyWidth)
            var widthPx = (keyWidthWeight * oneWeightWidthPx).toInt()

            if (column >= maxColumns || (column > 0 && xPos + widthPx > allowedWidth)) {
                xPos = 0
                yPos += currentRowHeight
                row++
                column = 0
                rowWeightAccumulo = 0f
                splitInserted = false
                currentRowHeight = rowHeightScaled[row]
            }

            rowWeightAccumulo += keyWidthWeight
            val totalWeight = rowWidthTotalWeight[row]

            if (landscapeSplitPercent > 0 && !splitInserted && rowWeightAccumulo > totalWeight * 0.5f) {
                splitInserted = true
                val gap = (totalWeight * splitRatio * oneWeightWidthPx).toInt()
                if (keyWidthWeight > 20f) {
                    widthPx += gap
                } else {
                    if (expandKeypressArea) spacers.add(Spacer(xPos, gap, row))
                    xPos += gap
                }
            }

            if (textKey.click.isEmpty()) {
                if (expandKeypressArea) spacers.add(Spacer(xPos, widthPx, row))
                xPos += widthPx
                return@forEach
            }

            val rightGap = abs(allowedWidth - xPos - widthPx)
            val keyWidth = if (rightGap <= allowedWidth / 100) allowedWidth - xPos else widthPx
            keyLayouts += KeyLayout(
                source = textKey,
                x = xPos,
                y = yPos,
                width = keyWidth,
                height = currentRowHeight,
                row = row,
                column = column,
            )
            column++
            xPos += keyWidth
            if (xPos > minWidth) {
                minWidth = xPos
            }
        }

        return Layout(
            keys = keyLayouts,
            spacers = spacers,
            height = yPos + currentRowHeight,
            minWidth = minWidth,
            lastRow = row,
        )
    }

    private fun scaleRowHeights(rowRawHeight: List<Int>): List<Int> {
        val rowHeightScaled = MutableList(rowRawHeight.size) { 0 }
        var remainHeight = keyboardHeight
        val rawHeightSum = rowRawHeight.sum()
        if (rawHeightSum <= 0 || keyboardHeight <= 0) return rowHeightScaled
        val scale = keyboardHeight.toFloat() / rawHeightSum
        for (i in 0 until rowRawHeight.size - 1) {
            val h = (rowRawHeight[i] * scale).toInt()
            rowHeightScaled[i] = h
            remainHeight -= h
        }
        rowHeightScaled[rowRawHeight.lastIndex] = remainHeight
        return rowHeightScaled
    }

    private fun TextKeyboard.TextKey.widthWeight(keyboardKeyWidth: Float): Float = if (width == 0f && click.isNotEmpty()) keyboardKeyWidth else width

    private companion object {
        const val MAX_TOTAL_WEIGHT = 100
    }
}
