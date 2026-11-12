/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.migration.impl.migrations

import dev.zacsweers.metro.Inject
import io.element.android.libraries.di.CacheDirectory
import io.element.android.libraries.di.SessionsCacheDirectory
import io.element.android.libraries.sessionstorage.api.SessionStore
import timber.log.Timber
import java.io.File

/**
 * MatriX: move the cache directories of the sessions out of the cache directory of the application, where Android can
 * delete the SDK databases while they are open (the new connections then open empty files, "no such table").
 * It is not a numbered [AppMigration], so that it never takes the number of a future migration of Element X: it is
 * idempotent and runs at each start of the application.
 */
@Inject
class SessionsCacheRelocation(
    private val sessionStore: SessionStore,
    @CacheDirectory private val cacheDirectory: File,
    @SessionsCacheDirectory private val sessionsCacheDirectory: File,
) {
    suspend fun run() {
        val oldSessionsCacheDirectory = File(cacheDirectory, "sessions")
        if (oldSessionsCacheDirectory.canonicalPath == sessionsCacheDirectory.canonicalPath) return
        oldSessionsCacheDirectory.listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .forEach { directory ->
                sessionsCacheDirectory.mkdirs()
                val target = File(sessionsCacheDirectory, directory.name)
                if (target.exists() || !directory.renameTo(target)) {
                    // It only contains cache data, which will be recreated by the SDK
                    Timber.w("Failed to move the cache directory ${directory.name}, deleting it")
                    directory.deleteRecursively()
                }
            }
        val oldPath = oldSessionsCacheDirectory.canonicalPath
        sessionStore.getAllSessions()
            .filter { it.cachePath.isNotEmpty() && File(it.cachePath).parentFile?.canonicalPath == oldPath }
            .forEach { session ->
                val newCachePath = File(sessionsCacheDirectory, File(session.cachePath).name)
                sessionStore.updateData(session.copy(cachePath = newCachePath.absolutePath))
            }
    }
}
