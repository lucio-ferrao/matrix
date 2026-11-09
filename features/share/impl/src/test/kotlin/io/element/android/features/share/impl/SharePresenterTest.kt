/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.share.impl

import android.net.Uri
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.element.android.features.share.api.ConversationShortcutTarget
import io.element.android.features.share.api.ShareIntentData
import io.element.android.features.share.api.UriToShare
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.core.mimetype.MimeTypes
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.test.AN_EXCEPTION
import io.element.android.libraries.matrix.test.A_MESSAGE
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.A_SESSION_ID_2
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.FakeMatrixClientProvider
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.lambda.any
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.lambda.value
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class SharePresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - initial state`() = runTest {
        val presenter = createSharePresenter()
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            val initialState = awaitItem()
            assertThat(initialState.shareAction.isUninitialized()).isTrue()
        }
    }

    @Test
    fun `present - on room selected error then clear error`() = runTest {
        val presenter = createSharePresenter(
            shareDataSender = FakeShareDataSender { _, _, _ -> Result.failure(AN_EXCEPTION) },
        )
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            val initialState = awaitItem()
            assertThat(initialState.shareAction.isUninitialized()).isTrue()
            presenter.onRoomSelected(A_SESSION_ID, listOf(A_ROOM_ID))
            assertThat(awaitItem().shareAction.isLoading()).isTrue()
            val failure = awaitItem()
            assertThat(failure.shareAction).isEqualTo(AsyncAction.Failure(AN_EXCEPTION))
            failure.eventSink.invoke(ShareEvent.ClearError)
            assertThat(awaitItem().shareAction.isUninitialized()).isTrue()
        }
    }

    @Test
    fun `present - on room selected ok`() = runTest {
        val client = FakeMatrixClient(sessionId = A_SESSION_ID)
        val shareIntentData = ShareIntentData.PlainText(A_MESSAGE)
        val sendResult = lambdaRecorder<MatrixClient, ShareIntentData, List<RoomId>, Result<Unit>> { _, _, _ -> Result.success(Unit) }
        val presenter = createSharePresenter(
            shareIntentData = shareIntentData,
            matrixClientProvider = FakeMatrixClientProvider { Result.success(client) },
            shareDataSender = FakeShareDataSender(sendResult),
        )
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            val initialState = awaitItem()
            assertThat(initialState.shareAction.isUninitialized()).isTrue()
            presenter.onRoomSelected(A_SESSION_ID, listOf(A_ROOM_ID))
            assertThat(awaitItem().shareAction.isLoading()).isTrue()
            val success = awaitItem()
            assertThat(success.shareAction).isEqualTo(AsyncAction.Success(ShareResult(A_SESSION_ID, listOf(A_ROOM_ID))))
            sendResult.assertions().isCalledOnce().with(value(client), value(shareIntentData), value(listOf(A_ROOM_ID)))
        }
    }

    @Test
    fun `present - media shared to a single room are previewed there, not sent`() = runTest {
        val uri: Uri = mockk("sharedUri")
        val media = listOf(UriToShare(uri, MimeTypes.Jpeg))
        val sendResult = lambdaRecorder<MatrixClient, ShareIntentData, List<RoomId>, Result<Unit>> { _, _, _ -> Result.success(Unit) }
        val presenter = createSharePresenter(
            shareIntentData = ShareIntentData.Uris(text = null, uris = media),
            shareDataSender = FakeShareDataSender(sendResult),
        )
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            presenter.onRoomSelected(A_SESSION_ID, listOf(A_ROOM_ID))
            assertThat(awaitItem().shareAction).isEqualTo(AsyncAction.Success(ShareResult(A_SESSION_ID, listOf(A_ROOM_ID), mediaToPreview = media)))
            sendResult.assertions().isNeverCalled()
        }
    }

    @Test
    fun `present - on room selected from another session ok`() = runTest {
        val getClient = lambdaRecorder<SessionId, Result<MatrixClient>> { sessionId -> Result.success(FakeMatrixClient(sessionId = sessionId)) }
        val sendResult = lambdaRecorder<MatrixClient, ShareIntentData, List<RoomId>, Result<Unit>> { client, _, _ ->
            assertThat(client.sessionId).isEqualTo(A_SESSION_ID_2)
            Result.success(Unit)
        }
        val presenter = createSharePresenter(
            matrixClientProvider = FakeMatrixClientProvider { getClient(it) },
            shareDataSender = FakeShareDataSender(sendResult),
        )
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            presenter.onRoomSelected(A_SESSION_ID_2, listOf(A_ROOM_ID))
            assertThat(awaitItem().shareAction.isLoading()).isTrue()
            val success = awaitItem()
            assertThat(success.shareAction).isEqualTo(AsyncAction.Success(ShareResult(A_SESSION_ID_2, listOf(A_ROOM_ID))))
            getClient.assertions().isCalledOnce().with(value(A_SESSION_ID_2))
            sendResult.assertions().isCalledOnce()
        }
    }

    @Test
    fun `present - on room selected, failure to get the client`() = runTest {
        val sendResult = lambdaRecorder<MatrixClient, ShareIntentData, List<RoomId>, Result<Unit>> { _, _, _ -> Result.success(Unit) }
        val presenter = createSharePresenter(
            matrixClientProvider = FakeMatrixClientProvider { Result.failure(AN_EXCEPTION) },
            shareDataSender = FakeShareDataSender(sendResult),
        )
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            presenter.onRoomSelected(A_SESSION_ID_2, listOf(A_ROOM_ID))
            assertThat(awaitItem().shareAction.isLoading()).isTrue()
            assertThat(awaitItem().shareAction).isEqualTo(AsyncAction.Failure(AN_EXCEPTION))
            sendResult.assertions().isNeverCalled()
        }
    }

    @Test
    fun `present - on room selected while sharing is ignored`() = runTest {
        val completable = CompletableDeferred<Result<Unit>>()
        val sendResult = lambdaRecorder<MatrixClient, ShareIntentData, List<RoomId>, Result<Unit>> { _, _, _ -> Result.success(Unit) }
        val presenter = createSharePresenter(
            shareDataSender = object : ShareDataSender {
                override suspend fun send(client: MatrixClient, shareIntentData: ShareIntentData, roomIds: List<RoomId>): Result<Unit> {
                    sendResult(client, shareIntentData, roomIds)
                    return completable.await()
                }
            },
        )
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            presenter.onRoomSelected(A_SESSION_ID, listOf(A_ROOM_ID))
            assertThat(awaitItem().shareAction.isLoading()).isTrue()
            presenter.onRoomSelected(A_SESSION_ID_2, listOf(A_ROOM_ID))
            completable.complete(Result.success(Unit))
            val success = awaitItem()
            // The second selection has been ignored
            assertThat(success.shareAction).isEqualTo(AsyncAction.Success(ShareResult(A_SESSION_ID, listOf(A_ROOM_ID))))
            sendResult.assertions().isCalledOnce()
        }
    }

    @Test
    fun `present - a room picked in the share sheet is shared to without selecting one`() = runTest {
        val shareIntentData = ShareIntentData.PlainText(
            content = A_MESSAGE,
            shortcutTarget = ConversationShortcutTarget(A_SESSION_ID_2, A_ROOM_ID),
        )
        val sendResult = lambdaRecorder<MatrixClient, ShareIntentData, List<RoomId>, Result<Unit>> { client, _, _ ->
            assertThat(client.sessionId).isEqualTo(A_SESSION_ID_2)
            Result.success(Unit)
        }
        val presenter = createSharePresenter(
            sessionId = A_SESSION_ID_2,
            shareIntentData = shareIntentData,
            matrixClientProvider = FakeMatrixClientProvider { Result.success(FakeMatrixClient(sessionId = it)) },
            shareDataSender = FakeShareDataSender(sendResult),
        )
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            assertThat(awaitItem().shareAction.isUninitialized()).isTrue()
            assertThat(awaitItem().shareAction.isLoading()).isTrue()
            assertThat(awaitItem().shareAction).isEqualTo(AsyncAction.Success(ShareResult(A_SESSION_ID_2, listOf(A_ROOM_ID))))
            sendResult.assertions().isCalledOnce().with(any(), value(shareIntentData), value(listOf(A_ROOM_ID)))
        }
    }

    @Test
    fun `present - a room picked for another session than the one handling the share is not shared to`() = runTest {
        val sendResult = lambdaRecorder<MatrixClient, ShareIntentData, List<RoomId>, Result<Unit>> { _, _, _ -> Result.success(Unit) }
        val presenter = createSharePresenter(
            sessionId = A_SESSION_ID,
            shareIntentData = ShareIntentData.PlainText(
                content = A_MESSAGE,
                // Picked for A_SESSION_ID_2, which is not logged in anymore, so A_SESSION_ID is handling the share.
                shortcutTarget = ConversationShortcutTarget(A_SESSION_ID_2, A_ROOM_ID),
            ),
            shareDataSender = FakeShareDataSender(sendResult),
        )
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            assertThat(awaitItem().shareAction.isUninitialized()).isTrue()
            expectNoEvents()
            sendResult.assertions().isNeverCalled()
        }
    }
}

internal fun TestScope.createSharePresenter(
    sessionId: SessionId = A_SESSION_ID,
    shareIntentData: ShareIntentData = ShareIntentData.PlainText(A_MESSAGE),
    matrixClientProvider: FakeMatrixClientProvider = FakeMatrixClientProvider(),
    shareDataSender: ShareDataSender = FakeShareDataSender(),
): SharePresenter {
    return SharePresenter(
        sessionId = sessionId,
        shareIntentData = shareIntentData,
        appCoroutineScope = this,
        matrixClientProvider = matrixClientProvider,
        shareDataSender = shareDataSender,
    )
}
