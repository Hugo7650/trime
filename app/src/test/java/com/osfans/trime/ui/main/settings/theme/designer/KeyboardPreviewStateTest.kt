/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.theme.designer

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class KeyboardPreviewStateTest :
    BehaviorSpec({
        Given("a keyboard preview state") {
            When("the preview state is toggled repeatedly") {
                val states = generateSequence(KeyboardPreviewState()) { it.next() }
                    .take(8)
                    .toList()

                Then("portrait, landscape and action-condition previews are all reachable") {
                    states.map { it.label() } shouldBe listOf(
                        "Portrait + landscape",
                        "Portrait",
                        "Landscape",
                        "Portrait + landscape / ASCII",
                        "Portrait + landscape / Composing",
                        "Portrait + landscape / Paging",
                        "Portrait + landscape / Menu",
                        "Portrait + landscape",
                    )
                }
            }
        }
    })
