/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.designer

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class ThemeDraftDeploymentTransactionTest :
    BehaviorSpec({
        Given("a draft deployment transaction") {
            When("deployment succeeds") {
                var restored = false
                var deployCount = 0
                var activatedThemeId = ""

                val deployed = ThemeDraftDeploymentTransaction.deployWithRollback(
                    themeId = "theme.designer.trime",
                    deploy = {
                        deployCount += 1
                        true
                    },
                    restoreBackup = {
                        restored = true
                        true
                    },
                    activate = { activatedThemeId = it },
                )

                Then("the draft is not rolled back") {
                    deployed shouldBe true
                    restored shouldBe false
                    deployCount shouldBe 1
                    activatedThemeId shouldBe "theme.designer.trime"
                }
            }

            When("deployment returns false") {
                var restoredThemeId = ""
                var deployCount = 0
                var activationCount = 0

                val deployed = ThemeDraftDeploymentTransaction.deployWithRollback(
                    themeId = "theme.designer.trime",
                    deploy = {
                        deployCount += 1
                        deployCount > 1
                    },
                    restoreBackup = {
                        restoredThemeId = it
                        true
                    },
                    activate = { activationCount += 1 },
                )

                Then("the backup is restored, redeployed, and the original failure is returned") {
                    deployed shouldBe false
                    restoredThemeId shouldBe "theme.designer.trime"
                    deployCount shouldBe 2
                    activationCount shouldBe 1
                }
            }

            When("deployment fails without an available backup") {
                var deployCount = 0

                val deployed = ThemeDraftDeploymentTransaction.deployWithRollback(
                    themeId = "theme.designer.trime",
                    deploy = {
                        deployCount += 1
                        false
                    },
                    restoreBackup = { false },
                )

                Then("no second deployment is attempted") {
                    deployed shouldBe false
                    deployCount shouldBe 1
                }
            }

            When("deployment throws") {
                var restoredThemeId = ""
                var deployCount = 0

                val failure = shouldThrow<IllegalStateException> {
                    ThemeDraftDeploymentTransaction.deployWithRollback(
                        themeId = "theme.designer.trime",
                        deploy = {
                            deployCount += 1
                            if (deployCount == 1) error("native deploy failed")
                            true
                        },
                        restoreBackup = {
                            restoredThemeId = it
                            true
                        },
                    )
                }

                Then("the backup is restored and redeployed before the failure is propagated") {
                    failure.message shouldBe "native deploy failed"
                    restoredThemeId shouldBe "theme.designer.trime"
                    deployCount shouldBe 2
                }
            }

            When("theme activation throws after deployment") {
                var restoredThemeId = ""
                var deployCount = 0
                var activationCount = 0

                val failure = shouldThrow<IllegalStateException> {
                    ThemeDraftDeploymentTransaction.deployWithRollback(
                        themeId = "theme.designer.trime",
                        deploy = {
                            deployCount += 1
                            true
                        },
                        restoreBackup = {
                            restoredThemeId = it
                            true
                        },
                        activate = {
                            activationCount += 1
                            if (activationCount == 1) error("theme activation failed")
                        },
                    )
                }

                Then("the previous file is restored and redeployed") {
                    failure.message shouldBe "theme activation failed"
                    restoredThemeId shouldBe "theme.designer.trime"
                    deployCount shouldBe 2
                    activationCount shouldBe 2
                }
            }

            When("redeploying the restored backup throws") {
                var deployCount = 0

                val failure = shouldThrow<IllegalStateException> {
                    ThemeDraftDeploymentTransaction.deployWithRollback(
                        themeId = "theme.designer.trime",
                        deploy = {
                            deployCount += 1
                            if (deployCount == 1) error("original deploy failed")
                            error("rollback deploy failed")
                        },
                        restoreBackup = { true },
                    )
                }

                Then("the original deployment failure is preserved") {
                    failure.message shouldBe "original deploy failed"
                    deployCount shouldBe 2
                }
            }

            When("a failed deployment is rolled back on disk") {
                val directory = Files.createTempDirectory("theme-deploy-rollback").toFile()
                val file = directory.resolve("theme.designer.trime.yaml")
                file.writeText("name: valid")
                ThemeDraftFileTransaction.writeWithBackup(file, "name: invalid")
                val deployedContents = mutableListOf<String>()

                val deployed = ThemeDraftDeploymentTransaction.deployWithRollback(
                    themeId = "theme.designer.trime",
                    deploy = {
                        val content = file.readText()
                        deployedContents += content
                        content == "name: valid"
                    },
                    restoreBackup = { ThemeDraftFileTransaction.restoreBackup(file) },
                )

                Then("the restored valid file is the version deployed by the rollback") {
                    deployed shouldBe false
                    deployedContents.shouldContainExactly("name: invalid", "name: valid")
                    file.readText() shouldBe "name: valid"
                }
            }
        }
    })
