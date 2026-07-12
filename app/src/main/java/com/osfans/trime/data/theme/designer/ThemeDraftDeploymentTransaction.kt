/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.designer

object ThemeDraftDeploymentTransaction {
    fun deployWithRollback(
        themeId: String,
        deploy: (String) -> Boolean,
        restoreBackup: (String) -> Boolean,
        activate: (String) -> Unit = {},
    ): Boolean = try {
        if (!deploy(themeId)) {
            rollback(themeId, deploy, restoreBackup, activate)
            false
        } else {
            activate(themeId)
            true
        }
    } catch (error: Throwable) {
        rollback(themeId, deploy, restoreBackup, activate)
        throw error
    }

    private fun rollback(
        themeId: String,
        deploy: (String) -> Boolean,
        restoreBackup: (String) -> Boolean,
        activate: (String) -> Unit,
    ) {
        if (!restoreBackup(themeId)) return
        runCatching {
            if (deploy(themeId)) activate(themeId)
        }
    }
}
