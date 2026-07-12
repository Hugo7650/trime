/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.designer

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class ThemeDraftFileStrategyTest :
    BehaviorSpec({
        Given("a user theme directory") {
            When("no derived theme exists") {
                val dir = Files.createTempDirectory("theme-draft-strategy-empty").toFile()
                val id = ThemeDraftFileStrategy.uniqueDerivedThemeId("trime", dir)

                Then("the preferred designer id is used") {
                    id shouldBe "trime.designer.trime"
                }
            }

            When("the preferred derived theme already exists") {
                val dir = Files.createTempDirectory("theme-draft-strategy-one").toFile()
                dir.resolve("trime.designer.trime.yaml").writeText("name: existing")
                val id = ThemeDraftFileStrategy.uniqueDerivedThemeId("trime", dir)

                Then("a numbered designer id is generated") {
                    id shouldBe "trime.designer2.trime"
                }
            }

            When("multiple derived themes already exist") {
                val dir = Files.createTempDirectory("theme-draft-strategy-many").toFile()
                dir.resolve("trime.designer.trime.yaml").writeText("name: existing")
                dir.resolve("trime.designer2.trime.yaml").writeText("name: existing 2")
                val id = ThemeDraftFileStrategy.uniqueDerivedThemeId("trime", dir)

                Then("the next available numbered id is generated") {
                    id shouldBe "trime.designer3.trime"
                }
            }

            When("theme ids are checked") {
                Then("designer ids are recognized") {
                    ThemeDraftFileStrategy.isDerivedThemeId("trime.designer.trime") shouldBe true
                    ThemeDraftFileStrategy.isDerivedThemeId("trime.designer2.trime") shouldBe true
                    ThemeDraftFileStrategy.isDerivedThemeId("trime") shouldBe false
                }
            }

            When("source ids are recovered from designer ids") {
                Then("numbered and preferred ids resolve to the original theme") {
                    ThemeDraftFileStrategy.sourceThemeId("trime.designer.trime") shouldBe "trime"
                    ThemeDraftFileStrategy.sourceThemeId("trime.designer3.trime") shouldBe "trime"
                    ThemeDraftFileStrategy.sourceThemeId("trime") shouldBe "trime"
                }
            }

            When("a backup file is requested") {
                val dir = Files.createTempDirectory("theme-draft-strategy-backup").toFile()
                val backup = ThemeDraftFileStrategy.backupFile(dir.resolve("trime.designer.trime.yaml"))

                Then("the backup sits next to the draft") {
                    backup.name shouldBe "trime.designer.trime.yaml.bak"
                    backup.parentFile shouldBe dir
                }
            }
        }
    })
