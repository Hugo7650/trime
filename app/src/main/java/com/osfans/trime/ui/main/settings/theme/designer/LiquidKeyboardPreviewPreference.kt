/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.theme.designer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.osfans.trime.R
import com.osfans.trime.data.theme.model.LiquidKeyboard
import splitties.dimensions.dp
import kotlin.math.ceil
import kotlin.math.min

class LiquidKeyboardPreviewPreference(
    context: Context,
    private val keyboard: LiquidKeyboard.Keyboard,
) : Preference(context) {
    init {
        isSelectable = false
        isIconSpaceReserved = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val container = holder.itemView as? ViewGroup ?: return
        container.removeAllViews()
        container.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(context.dp(16), context.dp(10), context.dp(16), context.dp(10))
                addView(
                    TextView(context).apply {
                        text = "${keyboard.name} - ${context.getString(R.string.theme_designer_key_count, keyboard.keys.size)}"
                        textSize = 14f
                        setTextColor(Color.rgb(45, 47, 48))
                    },
                )
                addView(PreviewCanvas(context, keyboard.keys))
            },
        )
    }

    private class PreviewCanvas(
        context: Context,
        private val keys: List<LiquidKeyboard.KeyItem>,
    ) : View(context) {
        private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(238, 240, 242) }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(190, 194, 198)
            style = Paint.Style.STROKE
            strokeWidth = context.dp(1).toFloat()
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(35, 38, 40)
            textAlign = Paint.Align.CENTER
            textSize = context.dp(14).toFloat()
        }
        private val columns = 6
        private val visibleCount = min(keys.size, columns * MAX_VISIBLE_ROWS)
        private val rows = if (visibleCount == 0) 1 else ceil(visibleCount / columns.toDouble()).toInt()

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            setMeasuredDimension(width, context.dp(8) + rows * context.dp(48))
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (visibleCount == 0) return
            val gap = context.dp(4).toFloat()
            val cellWidth = (width - gap * (columns + 1)) / columns
            val cellHeight = context.dp(44).toFloat()
            keys.take(visibleCount).forEachIndexed { index, key ->
                val column = index % columns
                val row = index / columns
                val left = gap + column * (cellWidth + gap)
                val top = gap + row * (cellHeight + gap)
                val rect = RectF(left, top, left + cellWidth, top + cellHeight)
                canvas.drawRoundRect(rect, context.dp(4).toFloat(), context.dp(4).toFloat(), keyPaint)
                canvas.drawRoundRect(rect, context.dp(4).toFloat(), context.dp(4).toFloat(), borderPaint)
                val label = key.altText.ifEmpty { key.text }
                val baseline = rect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2
                canvas.drawText(label.take(MAX_LABEL_LENGTH), rect.centerX(), baseline, textPaint)
            }
        }

        private companion object {
            const val MAX_VISIBLE_ROWS = 6
            const val MAX_LABEL_LENGTH = 8
        }
    }
}
