/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.designer

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object ThemeDraftFileTransaction {
    fun writeWithBackup(
        file: File,
        text: String,
        updateBackup: Boolean = true,
    ): File? {
        val backup = ThemeDraftFileStrategy.backupFile(file)
        val temp = File(file.parentFile, ".${file.name}.tmp")
        var backupFile: File? = null
        try {
            temp.writeText(text)
            backupFile = if (file.exists() && updateBackup) {
                file.copyTo(backup, overwrite = true)
                backup
            } else {
                null
            }
            moveTempFile(temp, file)
            return backupFile
        } catch (error: Throwable) {
            backupFile?.let {
                runCatching { it.copyTo(file, overwrite = true) }
            }
            throw error
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    fun restoreBackup(file: File): Boolean {
        val backup = ThemeDraftFileStrategy.backupFile(file)
        if (!backup.exists()) return false
        backup.copyTo(file, overwrite = true)
        return true
    }

    private fun moveTempFile(
        temp: File,
        file: File,
    ) {
        try {
            Files.move(
                temp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }
}
