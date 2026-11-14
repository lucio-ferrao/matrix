/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediapickers.api

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Immutable
import io.element.android.libraries.core.mimetype.MimeTypes

// As per MSC4274, the recommended item cap is 60.
private const val MAX_GALLERY_ITEMS = 60

@Immutable
sealed interface PickerType<Input, Output> {
    fun getContract(): ActivityResultContract<Input, Output>
    fun getDefaultRequest(): Input

    data object Image : PickerType<PickVisualMediaRequest, Uri?> {
        override fun getContract() = ActivityResultContracts.PickVisualMedia()
        override fun getDefaultRequest(): PickVisualMediaRequest {
            return PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        }
    }

    data object ImageAndVideo : PickerType<PickVisualMediaRequest, Uri?> {
        override fun getContract() = ActivityResultContracts.PickVisualMedia()
        override fun getDefaultRequest(): PickVisualMediaRequest {
            return PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
        }
    }

    data object ImageAndVideoMulti : PickerType<PickVisualMediaRequest, List<Uri>> {
        override fun getContract() = ActivityResultContracts.PickMultipleVisualMedia(MAX_GALLERY_ITEMS)
        override fun getDefaultRequest(): PickVisualMediaRequest {
            return PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
        }
    }

    object Camera {
        data class Photo(val destUri: Uri) : PickerType<Uri, Boolean> {
            override fun getContract() = TakePictureWithCameraFork()
            override fun getDefaultRequest(): Uri {
                return destUri
            }
        }
    }

    /**
     * MatriX: the photo is taken with the fork of Secure Camera when it is installed, which returns it with its EXIF and
     * the selfies as previewed, without its screen to confirm it (the attachments preview follows); otherwise with the
     * default camera app.
     */
    class TakePictureWithCameraFork : ActivityResultContracts.TakePicture() {
        override fun createIntent(context: Context, input: Uri): Intent {
            val intent = super.createIntent(context, input)
            val forkIntent = Intent(intent).setPackage(CAMERA_FORK_PACKAGE).putExtra(EXTRA_QUICK_CAPTURE, true)
            return if (forkIntent.resolveActivity(context.packageManager) != null) forkIntent else intent
        }

        private companion object {
            const val CAMERA_FORK_PACKAGE = "org.ferrao.camera"
            const val EXTRA_QUICK_CAPTURE = "android.intent.extra.quickCapture"
        }
    }

    data class File(val mimeType: String = MimeTypes.Any) : PickerType<String, Uri?> {
        override fun getContract() = ActivityResultContracts.GetContent()
        override fun getDefaultRequest(): String {
            return mimeType
        }
    }

    data class FileMulti(val mimeType: String = MimeTypes.Any) : PickerType<Array<String>, List<Uri>> {
        override fun getContract(): ActivityResultContract<Array<String>, List<Uri>> {
            return ActivityResultContracts.OpenMultipleDocuments()
        }
        override fun getDefaultRequest(): Array<String> {
            return arrayOf(mimeType)
        }
    }
}
