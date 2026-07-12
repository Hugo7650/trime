/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.designer

import com.osfans.trime.util.yaml.Node
import com.osfans.trime.util.yaml.Yaml
import com.osfans.trime.util.yaml.mapping

object RimeDeployedThemeYaml {
    fun parse(source: String): Node.Mapping = Yaml.parseToYamlNode(quoteReservedPlainScalars(source)).mapping
        ?: error("Theme YAML root must be a mapping")

    internal fun quoteReservedPlainScalars(source: String): String = source.lineSequence().joinToString("\n") { line ->
        val separator = line.indexOf(':')
        if (separator < 0) return@joinToString line
        val valueStart = line.indexOfFirstNonWhitespace(separator + 1)
        if (valueStart < 0 || line[valueStart] !in RESERVED_PLAIN_SCALAR_PREFIXES) {
            return@joinToString line
        }
        val valueEnd = line.indexOfLast { !it.isWhitespace() } + 1
        val value = line.substring(valueStart, valueEnd)
        line.substring(0, valueStart) + quote(value) + line.substring(valueEnd)
    }

    private fun String.indexOfFirstNonWhitespace(startIndex: Int): Int {
        for (index in startIndex until length) {
            if (!this[index].isWhitespace()) return index
        }
        return -1
    }

    private fun quote(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                else -> append(character)
            }
        }
        append('"')
    }

    private val RESERVED_PLAIN_SCALAR_PREFIXES = setOf('%', '@', '`', ':')
}
