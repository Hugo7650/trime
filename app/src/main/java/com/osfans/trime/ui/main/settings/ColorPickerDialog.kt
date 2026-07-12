// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.main.settings

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import com.osfans.trime.R
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.util.ColorUtils
import kotlinx.coroutines.launch
import splitties.dimensions.dp

object ColorPickerDialog {
    fun build(
        scope: LifecycleCoroutineScope,
        context: Context,
        afterConfirm: (suspend () -> Unit)? = null,
    ): AlertDialog {
        val presetSchemes = ThemeManager.activeTheme.colorSchemes
        val currentScheme = ColorManager.activeColorScheme
        val currentIndex = presetSchemes.indexOfFirst { it.id == currentScheme.id }
        return AlertDialog
            .Builder(context)
            .apply {
                setTitle(R.string.normal_mode_color)
                if (presetSchemes.isEmpty()) {
                    setMessage(R.string.no_color_to_select)
                } else {
                    setSingleChoiceItems(
                        presetSchemes.map { it.colors["name"] }.toTypedArray(),
                        currentIndex,
                    ) { dialog, which ->
                        scope.launch {
                            afterConfirm?.invoke()
                            if (which != currentIndex) {
                                val newScheme = presetSchemes[which]
                                ColorManager.setColorScheme(newScheme)
                            }
                            dialog.dismiss()
                        }
                    }
                }
                setNegativeButton(android.R.string.cancel, null)
            }.create()
    }

    fun buildColorValue(
        context: Context,
        title: String,
        value: String,
        onConfirm: (String) -> Unit,
    ): AlertDialog {
        val initialColor = parseInitialColor(value)
        val preview = View(context).apply {
            setBackgroundColor(initialColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                context.dp(40),
            )
        }
        val input = EditText(context).apply {
            setText(value.ifBlank { formatColor(initialColor) })
            selectAll()
        }
        val red = colorSeekBar(context, Color.red(initialColor))
        val green = colorSeekBar(context, Color.green(initialColor))
        val blue = colorSeekBar(context, Color.blue(initialColor))
        var syncing = false

        fun updateFromSliders() {
            if (syncing) return
            syncing = true
            val color = Color.rgb(red.progress, green.progress, blue.progress)
            preview.setBackgroundColor(color)
            input.setText(formatColor(color))
            input.setSelection(input.text.length)
            syncing = false
        }

        fun updateFromText(text: String) {
            if (syncing) return
            val color = runCatching { ColorUtils.parseColor(text) }.getOrNull() ?: return
            syncing = true
            red.progress = Color.red(color)
            green.progress = Color.green(color)
            blue.progress = Color.blue(color)
            preview.setBackgroundColor(color)
            syncing = false
        }

        val seekChangeListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean,
            ) {
                if (fromUser) updateFromSliders()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
        red.setOnSeekBarChangeListener(seekChangeListener)
        green.setOnSeekBarChangeListener(seekChangeListener)
        blue.setOnSeekBarChangeListener(seekChangeListener)
        input.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {
                    updateFromText(s?.toString().orEmpty())
                }

                override fun afterTextChanged(s: Editable?) = Unit
            },
        )

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(20), context.dp(12), context.dp(20), 0)
            addView(preview)
            addView(input)
            addView(colorRow(context, "R", red))
            addView(colorRow(context, "G", green))
            addView(colorRow(context, "B", blue))
        }

        return AlertDialog.Builder(context)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton(R.string.ok) { _, _ -> onConfirm(input.text.toString()) }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    private fun colorSeekBar(
        context: Context,
        progress: Int,
    ): SeekBar = SeekBar(context).apply {
        max = 255
        this.progress = progress
    }

    private fun colorRow(
        context: Context,
        label: String,
        seekBar: SeekBar,
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(
            TextView(context).apply {
                text = label
                layoutParams = LinearLayout.LayoutParams(context.dp(24), LinearLayout.LayoutParams.WRAP_CONTENT)
            },
        )
        addView(
            seekBar.apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            },
        )
    }

    private fun parseInitialColor(value: String): Int = runCatching { ColorUtils.parseColor(value) }.getOrDefault(Color.WHITE)

    private fun formatColor(color: Int): String = "0x%02x%02x%02x".format(Color.red(color), Color.green(color), Color.blue(color))
}
