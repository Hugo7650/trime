// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import com.osfans.trime.BuildConfig
import com.osfans.trime.core.Rime
import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.util.yaml.Node
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File

class GeneralStyleTest :
    BehaviorSpec({
        Given("Correct trime.yaml") {
            val dir = File("src/test/assets")
            Rime.startupRime(
                dir.absolutePath,
                dir.absolutePath,
                BuildConfig.BUILD_VERSION_NAME,
                false,
            )

            When("loaded") {
                val generalStyle = Theme.decodeByConfigId("trime").generalStyle

                Then("it should not be null") {
                    generalStyle shouldNotBe null
                    generalStyle.autoCaps shouldBe "false"

                    generalStyle.candidateFont shouldBe listOf("han.ttf")
                }
            }

            Rime.exitRime()
        }

        Given("Empty trime.yaml") {
            val dir = File("src/test/assets")
            Rime.startupRime(
                dir.absolutePath,
                dir.absolutePath,
                BuildConfig.BUILD_VERSION_NAME,
                false,
            )

            When("loaded") {
                val generalStyle = Theme.decodeByConfigId("incorrect").generalStyle

                Then("with default value without exception") {
                    generalStyle.autoCaps shouldBe ""
                    generalStyle.candidateBorder shouldBe 0
                    generalStyle.candidateFont shouldBe emptyList()
                    generalStyle.commentOnTop shouldBe false
                    generalStyle.commentPosition shouldBe GeneralStyle.CommentPosition.RIGHT
                    generalStyle.enterLabel shouldNotBe null
                    generalStyle.enterLabel.go shouldBe "go"
                    generalStyle.previewFont shouldBe ""
                    generalStyle.previewHeight shouldBe 0
                    generalStyle.previewOffset shouldBe -12
                    generalStyle.previewTextSize shouldBe 0f
                    generalStyle.proximityCorrection shouldBe false
                    generalStyle.verticalCorrection shouldBe -10f
                }
            }

            Rime.exitRime()
        }

        Given("a style with schema auto caps ascii mode") {
            When("loaded") {
                val style = GeneralStyle.decode(
                    Node.Mapping(
                        Node.Scalar("auto_caps") to Node.Scalar("ascii"),
                    ),
                )

                Then("the original scalar value is preserved and treated as enabled") {
                    style.autoCaps shouldBe "ascii"
                    style.isAutoCapsEnabled shouldBe true
                }
            }
        }

        Given("a style with schema-only visual fields") {
            When("loaded") {
                val style = GeneralStyle.decode(
                    Node.Mapping(
                        Node.Scalar("comment_on_top") to Node.Scalar("true"),
                        Node.Scalar("preview_font") to Node.Scalar("latin.ttf"),
                        Node.Scalar("preview_height") to Node.Scalar("60"),
                        Node.Scalar("preview_offset") to Node.Scalar("-16"),
                        Node.Scalar("preview_text_size") to Node.Scalar("42"),
                        Node.Scalar("proximity_correction") to Node.Scalar("true"),
                        Node.Scalar("vertical_correction") to Node.Scalar("-8.5"),
                    ),
                )

                Then("schema-defined values are decoded by the model") {
                    style.commentOnTop shouldBe true
                    style.previewFont shouldBe "latin.ttf"
                    style.previewHeight shouldBe 60
                    style.previewOffset shouldBe -16
                    style.previewTextSize shouldBe 42f
                    style.proximityCorrection shouldBe true
                    style.verticalCorrection shouldBe -8.5f
                }
            }
        }
    })
