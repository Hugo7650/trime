/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.designer

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class ThemeDraftFileTransactionTest :
    BehaviorSpec({
        Given("a draft file transaction") {
            When("an existing draft is written") {
                val dir = Files.createTempDirectory("theme-draft-transaction-existing").toFile()
                val file = dir.resolve("test.designer.trime.yaml")
                file.writeText("name: old")

                val backup = ThemeDraftFileTransaction.writeWithBackup(file, "name: new")

                Then("the old draft is backed up and the new draft is written") {
                    file.readText() shouldBe "name: new"
                    backup?.readText() shouldBe "name: old"
                    backup?.name shouldBe "test.designer.trime.yaml.bak"
                    dir.resolve(".test.designer.trime.yaml.tmp").exists() shouldBe false
                }
            }

            When("a new draft is written") {
                val dir = Files.createTempDirectory("theme-draft-transaction-new").toFile()
                val file = dir.resolve("test.designer.trime.yaml")

                val backup = ThemeDraftFileTransaction.writeWithBackup(file, "name: new")

                Then("the file is written without creating a backup") {
                    file.readText() shouldBe "name: new"
                    backup shouldBe null
                    ThemeDraftFileStrategy.backupFile(file).exists() shouldBe false
                    dir.resolve(".test.designer.trime.yaml.tmp").exists() shouldBe false
                }
            }

            When("a backup is restored") {
                val dir = Files.createTempDirectory("theme-draft-transaction-restore").toFile()
                val file = dir.resolve("test.designer.trime.yaml")
                file.writeText("name: broken")
                ThemeDraftFileStrategy.backupFile(file).writeText("name: valid")

                val restored = ThemeDraftFileTransaction.restoreBackup(file)

                Then("the backup replaces the current draft") {
                    restored shouldBe true
                    file.readText() shouldBe "name: valid"
                }
            }

            When("no backup exists") {
                val dir = Files.createTempDirectory("theme-draft-transaction-missing").toFile()
                val file = dir.resolve("test.designer.trime.yaml")
                file.writeText("name: current")

                val restored = ThemeDraftFileTransaction.restoreBackup(file)

                Then("restore is skipped and the current draft remains") {
                    restored shouldBe false
                    file.readText() shouldBe "name: current"
                }
            }

            When("an invalid current draft must not replace the valid backup") {
                val dir = Files.createTempDirectory("theme-draft-transaction-preserve-backup").toFile()
                val file = dir.resolve("test.designer.trime.yaml")
                val backup = ThemeDraftFileStrategy.backupFile(file)
                file.writeText("name: invalid current")
                backup.writeText("name: last valid")

                ThemeDraftFileTransaction.writeWithBackup(
                    file,
                    "name: repaired draft",
                    updateBackup = false,
                )

                Then("the new draft is written while the last valid backup is preserved") {
                    file.readText() shouldBe "name: repaired draft"
                    backup.readText() shouldBe "name: last valid"
                }
            }

            When("multiple invalid saves occur after a valid version") {
                val dir = Files.createTempDirectory("theme-draft-transaction-last-valid").toFile()
                val file = dir.resolve("test.designer.trime.yaml")
                file.writeText("name: valid deployed")

                ThemeDraftFileTransaction.writeWithBackup(file, "name: invalid one")
                ThemeDraftFileTransaction.writeWithBackup(
                    file,
                    "name: invalid two",
                    updateBackup = false,
                )
                val restored = ThemeDraftFileTransaction.restoreBackup(file)

                Then("rollback restores the last valid content rather than the previous invalid save") {
                    restored shouldBe true
                    file.readText() shouldBe "name: valid deployed"
                }
            }
        }
    })
