package com.example.multibarcode.data

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Minimal Google Drive access using the signed-in account's OAuth token and the Drive REST API.
 * Uses the `drive.file` scope, so the app only ever sees files it created.
 */
object DriveService {

    private val client = OkHttpClient()
    private const val SCOPE = "oauth2:https://www.googleapis.com/auth/drive.file"

    private fun token(context: Context): String? {
        val account = GoogleSignIn.getLastSignedInAccount(context)?.account ?: return null
        return GoogleAuthUtil.getToken(context, account, SCOPE)
    }

    /** Upload JPEG [bytes] as [name]; returns the new Drive file id, or null on failure. */
    suspend fun uploadJpeg(context: Context, name: String, bytes: ByteArray): String? =
        withContext(Dispatchers.IO) {
            try {
                val accessToken = token(context) ?: return@withContext null
                val metadata = JSONObject().put("name", name).toString()
                val body = MultipartBody.Builder()
                    .setType("multipart/related".toMediaType())
                    .addPart(metadata.toRequestBody("application/json; charset=UTF-8".toMediaType()))
                    .addPart(bytes.toRequestBody("image/jpeg".toMediaType()))
                    .build()
                val request = Request.Builder()
                    .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .post(body)
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val id = resp.body?.string()?.let { JSONObject(it).optString("id") }
                    id?.takeIf { it.isNotBlank() }
                }
            } catch (e: Exception) {
                null
            }
        }

    /** Download the raw bytes of a Drive file, or null on failure. */
    suspend fun download(context: Context, fileId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val accessToken = token(context) ?: return@withContext null
                val request = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .get()
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    resp.body?.bytes()
                }
            } catch (e: Exception) {
                null
            }
        }
}
