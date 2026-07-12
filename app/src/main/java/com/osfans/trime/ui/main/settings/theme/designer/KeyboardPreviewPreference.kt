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
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.osfans.trime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.designer.ThemeColorResolver
import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.data.theme.model.PresetKey
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.ime.keyboard.KeyBehavior
import com.osfans.trime.ime.keyboard.KeyboardLayoutCalculator
import com.osfans.trime.util.sp
import splitties.dimensions.dp
import kotlin.math.max
import kotlin.math.min

class KeyboardPreviewPreference(
    context: Context,
    private val keyboardId: String,
    private val theme: Theme,
    private val keyboard: TextKeyboard,
    private val colors: ThemeColorResolver,
    private val state: KeyboardPreviewState,
    private val onToggleState: () -> Unit,
    private val onKeyClick: ((Int) -> Unit)? = null,
) : Preference(context) {
    private val preferredSplitPercent = AppPrefs.defaultInstance().keyboard.splitSpacePercent.getValue()

    init {
        isSelectable = onKeyClick == null
        isIconSpaceReserved = false
        if (onKeyClick == null) {
            setOnPreferenceClickListener {
                onToggleState()
                true
            }
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val container = holder.itemView as? ViewGroup ?: return
        holder.itemView.setPadding(0, 0, 0, 0)
        container.removeAllViews()
        container.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, context.dp(10), 0, context.dp(10))
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                addView(
                    TextView(context).apply {
                        text = "$keyboardId - ${localizedStateLabel()}"
                        textSize = 14f
                        setTextColor(Color.rgb(45, 47, 48))
                        setPadding(context.dp(16), 0, context.dp(16), 0)
                    },
                )
                if (state.orientation != KeyboardPreviewState.Orientation.LANDSCAPE) {
                    addView(
                        PreviewCanvas(
                            context,
                            theme.generalStyle,
                            keyboard,
                            theme.presetKeys,
                            colors,
                            state,
                            landscape = false,
                            onKeyClick = onKeyClick,
                        ),
                    )
                }
                if (state.orientation != KeyboardPreviewState.Orientation.PORTRAIT) {
                    val landscapeKeyboard = KeyboardPreviewResolver.landscapeKeyboard(keyboard, theme.presetKeyboards)
                    addView(
                        PreviewCanvas(
                            context,
                            theme.generalStyle,
                            landscapeKeyboard,
                            theme.presetKeys,
                            colors,
                            state,
                            landscape = true,
                            landscapeSplitPercent = KeyboardPreviewResolver.landscapeSplitPercent(
                                landscapeKeyboard,
                                preferredSplitPercent,
                            ),
                            onKeyClick = onKeyClick,
                        ),
                    )
                }
            },
        )
    }

    private class PreviewCanvas(
        context: Context,
        private val style: GeneralStyle,
        private val keyboard: TextKeyboard,
        private val presetKeys: Map<String, PresetKey>,
        private val colors: ThemeColorResolver,
        private val state: KeyboardPreviewState,
        private val landscape: Boolean,
        private val landscapeSplitPercent: Int = 0,
        private val onKeyClick: ((Int) -> Unit)?,
    ) : View(context) {
        private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val keyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
        }
        private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.RIGHT
        }
        private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(97, 97, 97)
            textSize = context.dp(10).toFloat()
        }
        private val rect = RectF()
        private val horizontalGap = context.dp(
            listOf(keyboard.horizontalGap, style.horizontalGap).firstOrNull { it > 0 } ?: 0,
        ).toFloat()
        private val verticalGap = context.dp(
            listOf(keyboard.verticalGap, style.verticalGap).firstOrNull { it > 0 } ?: 0,
        ).toFloat()

        init {
            isClickable = onKeyClick != null
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (onKeyClick == null) return false
            if (event.action == MotionEvent.ACTION_UP) {
                val layout = calculate(width)
                val top = context.dp(22)
                val verticalScale = max(1f, minimumPreviewHeight().toFloat() / max(1, layout.height))
                val hit = layout.keys.firstOrNull { key ->
                    event.x >= key.x && event.x <= key.x + key.width &&
                        event.y >= top + key.y * verticalScale &&
                        event.y <= top + (key.y + key.height) * verticalScale
                }
                hit?.let { keyboard.keys.indexOf(it.source).takeIf { index -> index >= 0 }?.let(onKeyClick) }
                performClick()
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            val width = MeasureSpec.getSize(widthMeasureSpec).takeIf { it > 0 } ?: context.dp(320)
            val layout = calculate(width)
            val height = context.dp(24) + max(minimumPreviewHeight(), layout.height)
            setMeasuredDimension(width, height)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val layout = calculate(width)
            val caption = context.getString(
                if (landscape) R.string.theme_designer_preview_landscape else R.string.theme_designer_preview_portrait,
                layout.minWidth,
                layout.height,
            )
            canvas.drawText(caption, 0f, context.dp(14).toFloat(), captionPaint)
            val top = context.dp(22)
            val verticalScale = max(1f, minimumPreviewHeight().toFloat() / max(1, layout.height))
            layout.keys.forEach { key ->
                rect.set(
                    key.x.toFloat(),
                    top + key.y * verticalScale,
                    (key.x + key.width).toFloat(),
                    top + (key.y + key.height) * verticalScale,
                )
                rect.inset(
                    min(horizontalGap / 2f, rect.width() / 2f),
                    min(verticalGap / 2f, rect.height() / 2f),
                )
                val previewAction = previewAction(key.source)
                val label = KeyboardPreviewResolver.label(
                    key.source,
                    previewAction,
                    state,
                    presetKeys,
                ).ifEmpty { " " }
                val appearance = appearance(
                    key.source,
                    label,
                    functional = KeyboardPreviewResolver.isFunctional(previewAction, presetKeys),
                )
                val paint = keyPaint
                paint.color = appearance.backgroundColor
                canvas.drawRoundRect(rect, appearance.cornerRadius, appearance.cornerRadius, paint)
                if (appearance.borderWidth > 0f) {
                    keyStrokePaint.color = appearance.borderColor
                    keyStrokePaint.strokeWidth = appearance.borderWidth
                    canvas.drawRoundRect(rect, appearance.cornerRadius, appearance.cornerRadius, keyStrokePaint)
                }
                textPaint.color = appearance.textColor
                textPaint.textSize = min(
                    appearance.textSize,
                    min(
                        rect.height() * 0.44f,
                        rect.width() * 1.45f / max(1, label.length),
                    ),
                )
                val baseline = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2 + scaledSp(textOffsetY(key.source))
                canvas.drawText(
                    label.take(LABEL_LIMIT),
                    rect.centerX() + scaledSp(textOffsetX(key.source)),
                    baseline,
                    textPaint,
                )
                val hint = previewHint(key.source)
                if (hint.isNotEmpty()) {
                    hintPaint.color = appearance.symbolColor
                    hintPaint.textSize = min(
                        appearance.symbolTextSize,
                        min(rect.height() * 0.24f, rect.width() * 0.24f),
                    )
                    val explicitHint = key.source.hint.isNotEmpty()
                    val hintOffsetX = if (explicitHint) hintOffsetX(key.source) else symbolOffsetX(key.source)
                    val hintOffsetY = if (explicitHint) hintOffsetY(key.source) else symbolOffsetY(key.source)
                    canvas.drawText(
                        hint.take(HINT_LIMIT),
                        rect.right - context.dp(4) + scaledSp(hintOffsetX),
                        rect.top + context.dp(10) + scaledSp(hintOffsetY),
                        hintPaint,
                    )
                }
            }
        }

        private fun calculate(width: Int): KeyboardLayoutCalculator.Layout = KeyboardLayoutCalculator(
            allowedWidth = width,
            defaultKeyHeight = keyboard.height.takeIf { it > 0 }?.toInt()
                ?: style.keyHeight.takeIf { it > 0 }
                ?: DEFAULT_KEY_HEIGHT,
            keyboardHeight = context.dp((if (landscape) keyboard.keyboardHeightLand else keyboard.keyboardHeight))
                .takeIf { it > 0 }
                ?: context.dp(if (landscape) style.keyboardHeightLand else style.keyboardHeight).takeIf { it > 0 }
                ?: context.dp(DEFAULT_KEYBOARD_HEIGHT),
            landscapeSplitPercent = if (landscape) landscapeSplitPercent else 0,
            expandKeypressArea = false,
        ).calculate(keyboard)

        private fun minimumPreviewHeight(): Int = context.dp(if (landscape) 132 else 168)

        private fun previewAction(key: TextKeyboard.TextKey): KeyboardPreviewResolver.Action = KeyboardPreviewResolver.action(key, state)

        private fun previewHint(key: TextKeyboard.TextKey): String = key.hint
            .ifEmpty { key.labelSymbol }
            .ifEmpty { key.behaviors[KeyBehavior.LONG_CLICK].orEmpty() }

        private fun appearance(
            key: TextKeyboard.TextKey,
            label: String,
            functional: Boolean,
        ): KeyAppearance {
            val defaultBack = colors.color("key_back_color", ThemeColorResolver.DEFAULT_KEY_BACK)
            val defaultText = colors.color("key_text_color", ThemeColorResolver.DEFAULT_KEY_TEXT)
            val defaultSymbol = colors.color("key_symbol_color", ThemeColorResolver.DEFAULT_KEY_SYMBOL)
            val backFallback = if (functional) colors.color("off_key_back_color", defaultBack) else defaultBack
            val textFallback = if (functional) colors.color("off_key_text_color", defaultText) else defaultText
            val symbolFallback = if (functional) colors.color("off_key_symbol_color", defaultSymbol) else defaultSymbol
            val keyboardCorner = keyboard.roundCorner.takeIf { it >= 0 } ?: style.roundCorner
            val corner = key.roundCorner.takeIf { it >= 0 } ?: keyboardCorner
            val keyboardBorder = keyboard.keyBorder.takeIf { it >= 0 } ?: style.keyBorder
            val border = key.keyBorder.takeIf { it >= 0 } ?: keyboardBorder
            return KeyAppearance(
                backgroundColor = colors.colorValue(
                    key.keyBackColor,
                    backFallback,
                ),
                textColor = colors.colorValue(
                    key.keyTextColor,
                    textFallback,
                ),
                symbolColor = colors.colorValue(
                    key.keySymbolColor,
                    symbolFallback,
                ),
                borderColor = colors.color("key_border_color", ThemeColorResolver.DEFAULT_BORDER),
                borderWidth = context.dp(border).toFloat(),
                cornerRadius = context.dp(corner).toFloat(),
                textSize = context.sp(
                    key.keyTextSize.takeIf { it > 0f }
                        ?: if (label.length > 1) style.keyLongTextSize else style.keyTextSize,
                ),
                symbolTextSize = context.sp(
                    key.symbolTextSize.takeIf { it > 0f }
                        ?: style.symbolTextSize.takeIf { it > 0f }
                        ?: style.keyTextSize,
                ),
            )
        }

        private fun textOffsetX(key: TextKeyboard.TextKey): Float = firstNonZero(key.keyTextOffsetX, keyboard.keyTextOffsetX, style.keyTextOffsetX)

        private fun textOffsetY(key: TextKeyboard.TextKey): Float = firstNonZero(key.keyTextOffsetY, keyboard.keyTextOffsetY, style.keyTextOffsetY)

        private fun symbolOffsetX(key: TextKeyboard.TextKey): Float = firstNonZero(key.keySymbolOffsetX, keyboard.keySymbolOffsetX, style.keySymbolOffsetX)

        private fun symbolOffsetY(key: TextKeyboard.TextKey): Float = firstNonZero(key.keySymbolOffsetY, keyboard.keySymbolOffsetY, style.keySymbolOffsetY)

        private fun hintOffsetX(key: TextKeyboard.TextKey): Float = firstNonZero(key.keyHintOffsetX, keyboard.keyHintOffsetX, style.keyHintOffsetX)

        private fun hintOffsetY(key: TextKeyboard.TextKey): Float = firstNonZero(key.keyHintOffsetY, keyboard.keyHintOffsetY, style.keyHintOffsetY)

        private fun firstNonZero(
            keyValue: Float,
            keyboardValue: Float,
            styleValue: Float,
        ): Float = when {
            keyValue != 0f -> keyValue
            keyboardValue != 0f -> keyboardValue
            else -> styleValue
        }

        private fun scaledSp(value: Float): Float = context.sp(value)

        private data class KeyAppearance(
            val backgroundColor: Int,
            val textColor: Int,
            val symbolColor: Int,
            val borderColor: Int,
            val borderWidth: Float,
            val cornerRadius: Float,
            val textSize: Float,
            val symbolTextSize: Float,
        )
    }

    private fun localizedStateLabel(): String {
        val parts = mutableListOf(
            context.getString(
                when (state.orientation) {
                    KeyboardPreviewState.Orientation.BOTH -> R.string.theme_designer_preview_both
                    KeyboardPreviewState.Orientation.PORTRAIT -> R.string.theme_designer_preview_orientation_portrait
                    KeyboardPreviewState.Orientation.LANDSCAPE -> R.string.theme_designer_preview_orientation_landscape
                },
            ),
        )
        if (state.asciiMode) parts += context.getString(R.string.theme_designer_preview_ascii)
        if (state.composing) parts += context.getString(R.string.theme_designer_preview_composing)
        if (state.paging) parts += context.getString(R.string.theme_designer_preview_paging)
        if (state.hasMenu) parts += context.getString(R.string.theme_designer_preview_menu)
        return parts.joinToString(" / ")
    }

    private companion object {
        const val DEFAULT_KEY_HEIGHT = 50
        const val DEFAULT_KEYBOARD_HEIGHT = 220
        const val LABEL_LIMIT = 8
        const val HINT_LIMIT = 5
    }
}
