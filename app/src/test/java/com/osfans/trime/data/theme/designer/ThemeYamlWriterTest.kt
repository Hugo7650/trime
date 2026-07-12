/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.designer

import com.osfans.trime.util.yaml.Node
import com.osfans.trime.util.yaml.Yaml
import com.osfans.trime.util.yaml.get
import com.osfans.trime.util.yaml.mapping
import com.osfans.trime.util.yaml.string
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

class ThemeYamlWriterTest :
    BehaviorSpec({
        Given("a theme-like YAML node") {
            val node = Node.Mapping(
                Node.Scalar("name") to Node.Scalar("Trime / 可视化副本"),
                Node.Scalar("style") to Node.Mapping(
                    Node.Scalar("key_text_color") to Node.Scalar("#ffffff"),
                    Node.Scalar("keyboards") to Node.Sequence(
                        Node.Scalar("qwerty"),
                        Node.Scalar(".default"),
                    ),
                ),
            )

            When("written and parsed again") {
                val parsed = Yaml.parseToYamlNode(ThemeYamlWriter.write(node)).mapping!!

                Then("scalar and sequence values are preserved") {
                    parsed["name"]?.string shouldBe "Trime / 可视化副本"
                    parsed["style"]?.mapping?.get("key_text_color")?.string shouldBe "#ffffff"
                    parsed["style"]?.mapping?.get("keyboards")?.get("0")?.string shouldBe "qwerty"
                }
            }
        }

        Given("manual source text") {
            val source = """
                name: Source Test
                style:
                  keyboards:
                    - qwerty
                  key_width: 10
                  key_height: 50
                  keyboard_height: 220
                preset_keyboards:
                  qwerty:
                    width: 10
                    height: 50
                    keys:
                      - {click: q, width: 10}
                preset_color_schemes: {}
            """.trimIndent()

            When("parsed and normalized") {
                val node = Yaml.parseToYamlNode(source).mapping!!
                val normalized = ThemeYamlWriter.write(node)
                val parsed = Yaml.parseToYamlNode(normalized).mapping!!

                Then("the normalized source remains parseable") {
                    parsed["name"]?.string shouldBe "Source Test"
                    parsed["preset_keyboards"]?.mapping?.get("qwerty")?.mapping?.get("keys")?.get("0")?.mapping?.get("click")?.string shouldBe "q"
                }
            }
        }

        Given("scalar values beginning with YAML reserved indicators") {
            val node = Node.Mapping(
                Node.Scalar("percent") to Node.Scalar("%s"),
                Node.Scalar("colon") to Node.Scalar(":"),
            )

            When("written and parsed again") {
                val text = ThemeYamlWriter.write(node)
                val parsed = Yaml.parseToYamlNode(text).mapping!!

                Then("the writer quotes the values and preserves them") {
                    text.lines() shouldContainInOrder listOf("colon: \":\"", "percent: \"%s\"")
                    parsed["percent"]?.string shouldBe "%s"
                    parsed["colon"]?.string shouldBe ":"
                }
            }
        }

        Given("an unsorted theme-like node") {
            val node = Node.Mapping(
                Node.Scalar("preset_keyboards") to Node.Mapping(
                    Node.Scalar("qwerty") to Node.Mapping(
                        Node.Scalar("keys") to Node.Sequence(
                            Node.Mapping(
                                Node.Scalar("swipe_up") to Node.Scalar("1"),
                                Node.Scalar("click") to Node.Scalar("q"),
                                Node.Scalar("unknown_key_field") to Node.Scalar("x"),
                                Node.Scalar("width") to Node.Scalar("10"),
                            ),
                        ),
                        Node.Scalar("height") to Node.Scalar("50"),
                        Node.Scalar("width") to Node.Scalar("10"),
                    ),
                ),
                Node.Scalar("style") to Node.Mapping(
                    Node.Scalar("unknown_style_field") to Node.Scalar("x"),
                    Node.Scalar("key_height") to Node.Scalar("50"),
                    Node.Scalar("key_width") to Node.Scalar("10"),
                ),
                Node.Scalar("name") to Node.Scalar("Sorted Test"),
                Node.Scalar("config_version") to Node.Scalar("1.0"),
            )

            When("written") {
                val lines = ThemeYamlWriter.write(node).lines().filter { it.isNotBlank() }

                Then("known theme fields are emitted in stable semantic order") {
                    lines.shouldContainInOrder(
                        listOf(
                            "config_version: 1.0",
                            "name: \"Sorted Test\"",
                            "style:",
                            "  key_width: 10",
                            "  key_height: 50",
                            "  unknown_style_field: x",
                            "preset_keyboards:",
                            "  qwerty:",
                            "    width: 10",
                            "    height: 50",
                            "    keys:",
                            "      -",
                            "        click: q",
                            "        width: 10",
                            "        swipe_up: 1",
                            "        unknown_key_field: x",
                        ),
                    )
                }
            }
        }

        Given("a source node with include and patch directives") {
            val node = Node.Mapping(
                Node.Scalar("name") to Node.Scalar("Directive Test"),
                Node.Scalar("__include") to Node.Scalar("base:/"),
                Node.Scalar("style") to Node.Mapping(
                    Node.Scalar("__patch") to Node.Mapping(
                        Node.Scalar("key_width") to Node.Scalar("10"),
                    ),
                    Node.Scalar("unknown_style_field") to Node.Scalar("kept"),
                ),
            )

            When("written") {
                val text = ThemeYamlWriter.write(node)
                val parsed = Yaml.parseToYamlNode(text).mapping!!

                Then("include and patch directives are omitted but ordinary unknown fields remain") {
                    text shouldNotContain "__include"
                    text shouldNotContain "__patch"
                    parsed["style"]?.mapping?.get("unknown_style_field")?.string shouldBe "kept"
                }
            }
        }

        Given("a source node with an alias node") {
            val node = Node.Mapping(
                Node.Scalar("name") to Node.Scalar("Alias Test"),
                Node.Scalar("style") to Node.Mapping(
                    Node.Scalar("key_text_color") to Node.Alias("text_color"),
                ),
            )

            When("written") {
                val text = ThemeYamlWriter.write(node)
                val parsed = Yaml.parseToYamlNode(text).mapping!!

                Then("alias syntax is normalized to plain scalar text") {
                    text shouldNotContain "*text_color"
                    parsed["style"]?.mapping?.get("key_text_color")?.string shouldBe "text_color"
                }
            }
        }

        Given("a source node with root extension sections") {
            val node = Node.Mapping(
                Node.Scalar("name") to Node.Scalar("Extension Test"),
                Node.Scalar("android_keys") to Node.Mapping(
                    Node.Scalar("KEYCODE_TEST") to Node.Scalar("1"),
                ),
                Node.Scalar("style") to Node.Mapping(
                    Node.Scalar("key_width") to Node.Scalar("10"),
                ),
            )

            When("written") {
                val parsed = Yaml.parseToYamlNode(ThemeYamlWriter.write(node)).mapping!!

                Then("ordinary root extension sections are preserved for the advanced source page") {
                    parsed["android_keys"]?.mapping?.get("KEYCODE_TEST")?.string shouldBe "1"
                }
            }
        }
    })
