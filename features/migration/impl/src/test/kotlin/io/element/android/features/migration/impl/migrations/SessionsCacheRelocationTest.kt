/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.migration.impl.migrations

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.sessionstorage.test.InMemorySessionStore
import io.element.android.libraries.sessionstorage.test.aSessionData
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SessionsCacheRelocationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var cacheDirectory: File
    private lateinit var oldSessionsCacheDirectory: File
    private lateinit var sessionsCacheDirectory: File

    @Before
    fun setup() {
        cacheDirectory = temporaryFolder.newFolder("cache")
        oldSessionsCacheDirectory = File(cacheDirectory, "sessions")
        sessionsCacheDirectory = File(temporaryFolder.root, "files/sessions-cache")
    }

    @Test
    fun `session cache directory is moved and the session is updated`() = runTest {
        val oldDirectory = anOldSessionCacheDirectory(A_UUID)
        val session = aSessionData(
            sessionId = A_SESSION_ID.value,
            sessionPath = "/files/sessions/$A_UUID",
            cachePath = oldDirectory.absolutePath,
        )
        val sessionStore = InMemorySessionStore(initialList = listOf(session))

        createRelocation(sessionStore).run()

        val expectedDirectory = File(sessionsCacheDirectory, A_UUID)
        assertThat(oldDirectory.exists()).isFalse()
        assertThat(File(expectedDirectory, A_FILE_NAME).readText()).isEqualTo(A_FILE_CONTENT)
        val storedSession = sessionStore.getSession(A_SESSION_ID.value)!!
        assertThat(storedSession.cachePath).isEqualTo(expectedDirectory.absolutePath)
        assertThat(storedSession.sessionPath).isEqualTo("/files/sessions/$A_UUID")
    }

    @Test
    fun `other files of the cache directory are not moved`() = runTest {
        val logs = File(cacheDirectory, "logs").apply { mkdirs() }

        createRelocation(InMemorySessionStore()).run()

        assertThat(logs.exists()).isTrue()
        assertThat(sessionsCacheDirectory.exists()).isFalse()
    }

    @Test
    fun `session is updated even if its cache directory does not exist`() = runTest {
        val session = aSessionData(cachePath = File(oldSessionsCacheDirectory, A_UUID).absolutePath)
        val sessionStore = InMemorySessionStore(initialList = listOf(session))

        createRelocation(sessionStore).run()

        assertThat(sessionStore.getAllSessions().single().cachePath).isEqualTo(File(sessionsCacheDirectory, A_UUID).absolutePath)
    }

    @Test
    fun `an old directory whose target already exists is deleted`() = runTest {
        val oldDirectory = anOldSessionCacheDirectory(A_UUID)
        val target = File(sessionsCacheDirectory, A_UUID).apply { mkdirs() }

        createRelocation(InMemorySessionStore()).run()

        assertThat(oldDirectory.exists()).isFalse()
        assertThat(target.list().orEmpty()).isEmpty()
    }

    @Test
    fun `relocation is idempotent`() = runTest {
        anOldSessionCacheDirectory(A_UUID)
        val sessionStore = InMemorySessionStore(
            initialList = listOf(aSessionData(cachePath = File(oldSessionsCacheDirectory, A_UUID).absolutePath))
        )
        val relocation = createRelocation(sessionStore)

        relocation.run()
        val sessionsAfterFirstRun = sessionStore.getAllSessions()
        relocation.run()

        assertThat(sessionStore.getAllSessions()).isEqualTo(sessionsAfterFirstRun)
        assertThat(File(sessionsCacheDirectory, "$A_UUID/$A_FILE_NAME").readText()).isEqualTo(A_FILE_CONTENT)
    }

    @Test
    fun `nothing happens when the sessions cache directory did not change`() = runTest {
        val oldDirectory = anOldSessionCacheDirectory(A_UUID)
        val session = aSessionData(cachePath = oldDirectory.absolutePath)
        val sessionStore = InMemorySessionStore(initialList = listOf(session))

        SessionsCacheRelocation(sessionStore, cacheDirectory, oldSessionsCacheDirectory).run()

        assertThat(oldDirectory.exists()).isTrue()
        assertThat(sessionStore.getAllSessions()).containsExactly(session)
    }

    private fun anOldSessionCacheDirectory(name: String) = File(oldSessionsCacheDirectory, name).apply {
        mkdirs()
        File(this, A_FILE_NAME).writeText(A_FILE_CONTENT)
    }

    private fun createRelocation(sessionStore: InMemorySessionStore) = SessionsCacheRelocation(
        sessionStore = sessionStore,
        cacheDirectory = cacheDirectory,
        sessionsCacheDirectory = sessionsCacheDirectory,
    )

    private companion object {
        const val A_UUID = "123e4567-e89b-12d3-a456-426614174000"
        const val A_FILE_NAME = "matrix-sdk-event-cache.sqlite3"
        const val A_FILE_CONTENT = "content"
    }
}
