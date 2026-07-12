/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.designer

import com.osfans.trime.util.yaml.Node

object ThemeYamlEditor {
    fun valueAt(
        node: Node.Mapping,
        path: List<String>,
    ): Node? = path.fold(node as Node?) { current, key ->
        (current as? Node.Mapping)?.get(key)
    }

    fun updateScalar(
        node: Node.Mapping,
        path: List<String>,
        value: String,
    ): Node.Mapping = update(node, path, Node.Scalar(value))

    fun updateSequence(
        node: Node.Mapping,
        path: List<String>,
        values: List<String>,
    ): Node.Mapping = update(node, path, Node.Sequence(values.map { Node.Scalar(it) }))

    fun updateNode(
        node: Node.Mapping,
        path: List<String>,
        value: Node,
    ): Node.Mapping = update(node, path, value)

    fun remove(
        node: Node.Mapping,
        path: List<String>,
    ): Node.Mapping {
        require(path.isNotEmpty()) { "Path must not be empty" }
        val key = Node.Scalar(path.first())
        if (path.size == 1) {
            return Node.Mapping(node.pairs - key)
        }
        val child = node[key] as? Node.Mapping ?: return node
        return Node.Mapping(node.pairs + (key to remove(child, path.drop(1))))
    }

    fun updateSequenceItemMappingScalar(
        node: Node.Mapping,
        path: List<String>,
        index: Int,
        key: String,
        value: String,
    ): Node.Mapping = updateSequenceItemMappingNode(node, path, index, key, Node.Scalar(value))

    fun updateSequenceItemMappingNode(
        node: Node.Mapping,
        path: List<String>,
        index: Int,
        key: String,
        value: Node,
    ): Node.Mapping = updateSequenceItemMappingPath(node, path, index, listOf(key), value)

    fun updateSequenceItemMappingPath(
        node: Node.Mapping,
        path: List<String>,
        index: Int,
        itemPath: List<String>,
        value: Node,
    ): Node.Mapping {
        require(itemPath.isNotEmpty()) { "Item path must not be empty" }
        val sequence = valueAt(node, path) as? Node.Sequence ?: return node
        if (index !in sequence.indices) return node
        val current = sequence[index] as? Node.Mapping ?: Node.Mapping(emptyMap())
        val updatedItem = update(current, itemPath, value)
        val updatedSequence = Node.Sequence(
            sequence.mapIndexed { itemIndex, item ->
                if (itemIndex == index) updatedItem else item
            },
        )
        return update(node, path, updatedSequence)
    }

    fun insertSequenceItem(
        node: Node.Mapping,
        path: List<String>,
        index: Int,
        item: Node,
    ): Node.Mapping {
        val sequence = valueAt(node, path) as? Node.Sequence ?: Node.Sequence(emptyList())
        val insertIndex = index.coerceIn(0, sequence.size)
        val nodes = sequence.toMutableList().apply {
            add(insertIndex, item)
        }
        return update(node, path, Node.Sequence(nodes))
    }

    fun removeSequenceItem(
        node: Node.Mapping,
        path: List<String>,
        index: Int,
    ): Node.Mapping {
        val sequence = valueAt(node, path) as? Node.Sequence ?: return node
        if (index !in sequence.indices) return node
        val nodes = sequence.toMutableList().apply {
            removeAt(index)
        }
        return update(node, path, Node.Sequence(nodes))
    }

    fun copySequenceItem(
        node: Node.Mapping,
        path: List<String>,
        index: Int,
    ): Node.Mapping {
        val sequence = valueAt(node, path) as? Node.Sequence ?: return node
        if (index !in sequence.indices) return node
        val nodes = sequence.toMutableList().apply {
            add(index + 1, sequence[index])
        }
        return update(node, path, Node.Sequence(nodes))
    }

    fun moveSequenceItem(
        node: Node.Mapping,
        path: List<String>,
        fromIndex: Int,
        toIndex: Int,
    ): Node.Mapping {
        val sequence = valueAt(node, path) as? Node.Sequence ?: return node
        if (fromIndex !in sequence.indices || toIndex !in sequence.indices) return node
        val nodes = sequence.toMutableList()
        val item = nodes.removeAt(fromIndex)
        nodes.add(toIndex, item)
        return update(node, path, Node.Sequence(nodes))
    }

    fun reorderSequenceItems(
        node: Node.Mapping,
        path: List<String>,
        order: List<Int>,
    ): Node.Mapping {
        val sequence = valueAt(node, path) as? Node.Sequence ?: return node
        require(order.size == sequence.size) { "Order must contain ${sequence.size} item(s)" }
        require(order.toSet().size == sequence.size) { "Order contains duplicate item index" }
        require(order.all { it in sequence.indices }) { "Order contains item index outside 0..${sequence.lastIndex}" }
        return update(node, path, Node.Sequence(order.map { sequence[it] }))
    }

    fun updateAllSequenceMappingScalars(
        node: Node.Mapping,
        path: List<String>,
        values: Map<String, String>,
    ): Node.Mapping {
        val sequence = valueAt(node, path) as? Node.Sequence ?: return node
        val nodes = sequence.map { item ->
            val mapping = item as? Node.Mapping ?: return@map item
            Node.Mapping(mapping.pairs + values.mapKeys { Node.Scalar(it.key) }.mapValues { Node.Scalar(it.value) })
        }
        return update(node, path, Node.Sequence(nodes))
    }

    private fun update(
        node: Node.Mapping,
        path: List<String>,
        value: Node,
    ): Node.Mapping {
        require(path.isNotEmpty()) { "Path must not be empty" }
        val key = Node.Scalar(path.first())
        val updatedValue = if (path.size == 1) {
            value
        } else {
            val child = node[key] as? Node.Mapping ?: Node.Mapping(emptyMap())
            update(child, path.drop(1), value)
        }
        return Node.Mapping(node.pairs + (key to updatedValue))
    }
}
