package com.example.multibarcode.data

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Uploads product images to a SINGLE designated Google Drive account (chosen in Settings),
 * regardless of who is logged in. Uploaded files are made public-by-link, so every user can
 * view them without needing that account on their own device.
 */
object DriveService {

    private val client = OkHttpClient()
    private const val SCOPE = "oauth2:https://www.googleapis.com/auth/drive.file"

    /** Thrown when the chosen storage account still needs to grant Drive consent on this device. */
    class NeedsConsent(val intent: Intent) : Exception()

    /** Public thumbnail URL for a Drive file (works once the file is shared by-link). */
    fun publicThumbUrl(fileId: String, size: Int = 512): String =
        "https://drive.google.com/thumbnail?id=$fileId&sz=w$size"

    private fun tokenFor(context: Context, email: String): String {
        val account = Account(email, "com.google")
        try {
            return GoogleAuthUtil.getToken(context, account, SCOPE)
        } catch (e: UserRecoverableAuthException) {
            throw NeedsConsent(e.intent ?: throw e)
        }
    }

    /** Force the Drive-consent flow for [email]; throws [NeedsConsent] with the intent to launch. */
    suspend fun ensureConsent(context: Context, email: String) = withContext(Dispatchers.IO) {
        tokenFor(context, email) // throws NeedsConsent if not yet granted
        Unit
    }

    /** Upload JPEG [bytes] to [storageEmail]'s Drive, make it public, return the file id (or null). */
    suspend fun uploadJpeg(context: Context, storageEmail: String, name: String, bytes: ByteArray): String? =
        withContext(Dispatchers.IO) {
            try {
                val token = tokenFor(context, storageEmail)
                val metadata = JSONObject().put("name", name).toString()
                val body = MultipartBody.Builder()
                    .setType("multipart/related".toMediaType())
                    .addPart(metadata.toRequestBody("application/json; charset=UTF-8".toMediaType()))
                    .addPart(bytes.toRequestBody("image/jpeg".toMediaType()))
                    .build()
                val request = Request.Builder()
                    .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                    .addHeader("Authorization", "Bearer $token")
                    .post(body)
                    .build()
                val id = client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    resp.body?.string()?.let { JSONObject(it).optString("id") }?.takeIf { it.isNotBlank() }
                } ?: return@withContext null
                makePublic(token, id)
                id
            } catch (e: NeedsConsent) {
                null // consent must be granted from Settings; treated as a failed upload here
            } catch (e: Exception) {
                null
            }
        }

    /** Fetch a public (by-link) Drive image's bytes; no auth needed. */
    suspend fun downloadPublicThumb(fileId: String, size: Int = 512): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(publicThumbUrl(fileId, size)).get().build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.bytes()
                }
            } catch (e: Exception) {
                null
            }
        }

    private fun makePublic(token: String, fileId: String) {
        try {
            val body = JSONObject().put("role", "reader").put("type", "anyone").toString()
                .toRequestBody("application/json; charset=UTF-8".toMediaType())
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId/permissions")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()
            client.newCall(request).execute().close()
        } catch (_: Exception) {
        }
    }
}
