/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.designer

import com.osfans.trime.util.yaml.Node
import com.osfans.trime.util.yaml.mapping
import com.osfans.trime.util.yaml.sequence
import com.osfans.trime.util.yaml.string
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class ThemeYamlEditorTest :
    BehaviorSpec({
        Given("a nested mapping") {
            val node = Node.Mapping(
                Node.Scalar("style") to Node.Mapping(
                    Node.Scalar("key_width") to Node.Scalar("10"),
                ),
            )

            When("a nested scalar is updated") {
                val updated = ThemeYamlEditor.updateScalar(node, listOf("style", "key_width"), "12.5")

                Then("only that path changes") {
                    ThemeYamlEditor.valueAt(updated, listOf("style", "key_width"))?.string shouldBe "12.5"
                }
            }

            When("a missing sequence path is updated") {
                val updated = ThemeYamlEditor.updateSequence(
                    node,
                    listOf("style", "keyboards"),
                    listOf("qwerty", "number"),
                )

                Then("intermediate mappings and sequence values are written") {
                    val keyboards = updated["style"]?.mapping?.get("keyboards")?.sequence
                    keyboards?.mapNotNull { it.string } shouldBe listOf("qwerty", "number")
                }
            }

            When("a nested mapping path is removed") {
                val updated = ThemeYamlEditor.remove(node, listOf("style", "key_width"))

                Then("that mapping entry is absent") {
                    ThemeYamlEditor.valueAt(updated, listOf("style", "key_width")) shouldBe null
                }
            }

            When("a key field inside a sequence is updated") {
                val keyboardNode = ThemeYamlEditor.updateNode(
                    node,
                    listOf("preset_keyboards", "qwerty", "keys"),
                    Node.Sequence(
                        listOf(
                            Node.Mapping(Node.Scalar("click") to Node.Scalar("a")),
                            Node.Mapping(Node.Scalar("click") to Node.Scalar("b")),
                        ),
                    ),
                )
                val updated = ThemeYamlEditor.updateSequenceItemMappingScalar(
                    keyboardNode,
                    listOf("preset_keyboards", "qwerty", "keys"),
                    1,
                    "width",
                    "12",
                )

                Then("the requested sequence item receives the new field") {
                    val key = ThemeYamlEditor.valueAt(updated, listOf("preset_keyboards", "qwerty", "keys"))
                        ?.sequence
                        ?.get(1)
                        ?.mapping
                    key?.get("click")?.string shouldBe "b"
                    key?.get("width")?.string shouldBe "12"
                }
            }

            When("a key node field inside a sequence is updated") {
                val keyboardNode = ThemeYamlEditor.updateNode(
                    node,
                    listOf("preset_keyboards", "qwerty", "keys"),
                    Node.Sequence(
                        listOf(
                            Node.Mapping(Node.Scalar("click") to Node.Scalar("a")),
                        ),
                    ),
                )
                val updated = ThemeYamlEditor.updateSequenceItemMappingNode(
                    keyboardNode,
                    listOf("preset_keyboards", "qwerty", "keys"),
                    0,
                    "popup",
                    Node.Sequence(listOf(Node.Scalar("one"), Node.Scalar("two"))),
                )

                Then("the requested sequence item receives the structured field") {
                    val popup = ThemeYamlEditor.valueAt(updated, listOf("preset_keyboards", "qwerty", "keys"))
                        ?.sequence
                        ?.get(0)
                        ?.mapping
                        ?.get("popup")
                        ?.sequence
                    popup?.mapNotNull { it.string } shouldBe listOf("one", "two")
                }
            }

            When("a nested field inside a sequence mapping is updated") {
                val toolbarNode = ThemeYamlEditor.updateNode(
                    node,
                    listOf("tool_bar", "buttons"),
                    Node.Sequence(
                        listOf(
                            Node.Mapping(
                                Node.Scalar("foreground") to Node.Mapping(
                                    Node.Scalar("normal") to Node.Scalar("old"),
                                ),
                            ),
                        ),
                    ),
                )
                val updated = ThemeYamlEditor.updateSequenceItemMappingPath(
                    toolbarNode,
                    listOf("tool_bar", "buttons"),
                    0,
                    listOf("foreground", "normal"),
                    Node.Scalar("new"),
                )

                Then("the requested nested path is updated") {
                    val value = ThemeYamlEditor.valueAt(updated, listOf("tool_bar", "buttons"))
                        ?.sequence
                        ?.get(0)
                        ?.mapping
                        ?.get("foreground")
                        ?.mapping
                        ?.get("normal")
                        ?.string
                    value shouldBe "new"
                }
            }

            When("sequence items are inserted copied moved and bulk updated") {
                val keyboardNode = ThemeYamlEditor.updateNode(
                    node,
                    listOf("preset_keyboards", "qwerty", "keys"),
                    Node.Sequence(
                        listOf(
                            Node.Mapping(Node.Scalar("click") to Node.Scalar("a")),
                            Node.Mapping(Node.Scalar("click") to Node.Scalar("b")),
                        ),
                    ),
                )
                val inserted = ThemeYamlEditor.insertSequenceItem(
                    keyboardNode,
                    listOf("preset_keyboards", "qwerty", "keys"),
                    1,
                    Node.Mapping(Node.Scalar("click") to Node.Scalar("x")),
                )
                val copied = ThemeYamlEditor.copySequenceItem(
                    inserted,
                    listOf("preset_keyboards", "qwerty", "keys"),
                    0,
                )
                val moved = ThemeYamlEditor.moveSequenceItem(
                    copied,
                    listOf("preset_keyboards", "qwerty", "keys"),
                    0,
                    2,
                )
                val resized = ThemeYamlEditor.updateAllSequenceMappingScalars(
                    moved,
                    listOf("preset_keyboards", "qwerty", "keys"),
                    mapOf("width" to "11", "height" to "44"),
                )

                Then("the sequence operation results are reflected") {
                    val keys = ThemeYamlEditor.valueAt(resized, listOf("preset_keyboards", "qwerty", "keys"))?.sequence!!
                    keys.size shouldBe 4
                    keys.map { it.mapping?.get("width")?.string }.distinct() shouldBe listOf("11")
                    keys.map { it.mapping?.get("height")?.string }.distinct() shouldBe listOf("44")
                }
            }

            When("sequence items are reordered by index list") {
                val keyboardNode = ThemeYamlEditor.updateNode(
                    node,
                    listOf("preset_keyboards", "qwerty", "keys"),
                    Node.Sequence(
                        listOf(
                            Node.Mapping(Node.Scalar("click") to Node.Scalar("a")),
                            Node.Mapping(Node.Scalar("click") to Node.Scalar("b")),
                            Node.Mapping(Node.Scalar("click") to Node.Scalar("c")),
                        ),
                    ),
                )

                val reordered = ThemeYamlEditor.reorderSequenceItems(
                    keyboardNode,
                    listOf("preset_keyboards", "qwerty", "keys"),
                    listOf(2, 0, 1),
                )

                Then("the requested order is applied without changing the items") {
                    val keys = ThemeYamlEditor.valueAt(reordered, listOf("preset_keyboards", "qwerty", "keys"))
                        ?.sequence
                    keys?.map { it.mapping?.get("click")?.string } shouldBe listOf("c", "a", "b")
                }
            }
        }
    })
