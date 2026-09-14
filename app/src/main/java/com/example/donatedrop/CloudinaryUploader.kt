package com.example.donatedrop

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

object CloudinaryUploader {
    private val client = OkHttpClient()

    // TODO: replace with your Cloudinary values
    private const val CLOUD_NAME = "dpvst39rd"
    private const val UPLOAD_PRESET = "my_image"

    private const val TAG = "CloudinaryUploader"

    /**
     * Uploads the given image Uri to Cloudinary (unsigned).
     * Returns the secure_url string on success, or null on failure.
     */
    suspend fun uploadUnsigned(context: Context, imageUri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
                if (inputStream == null) {
                    Log.e(TAG, "Could not open input stream for uri: $imageUri")
                    return@withContext null
                }

                // Read all bytes (consider resizing/compressing for production)
                val baos = ByteArrayOutputStream()
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    baos.write(buffer, 0, read)
                }
                inputStream.close()
                val fileBytes = baos.toByteArray()

                // Build multipart body
                val fileRequestBody = RequestBody.create("image/jpeg".toMediaTypeOrNull(), fileBytes)
                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", "upload.jpg", fileRequestBody)
                    .addFormDataPart("upload_preset", UPLOAD_PRESET)
                    .build()

                val url = "https://api.cloudinary.com/v1_1/$CLOUD_NAME/image/upload"
                val request = Request.Builder()
                    .url(url)
                    .post(multipartBody)
                    .build()

                val resp = client.newCall(request).execute()
                val bodyText = resp.body?.string()
                if (!resp.isSuccessful) {
                    Log.e(TAG, "Upload failed HTTP ${resp.code}. Body: $bodyText")
                    return@withContext null
                }

                if (bodyText.isNullOrEmpty()) {
                    Log.e(TAG, "Empty response body from Cloudinary")
                    return@withContext null
                }

                val json = JSONObject(bodyText)
                val secureUrl = json.optString("secure_url", null)
                if (secureUrl.isNullOrEmpty()) {
                    Log.e(TAG, "No secure_url in response: $bodyText")
                }
                return@withContext secureUrl
            } catch (e: Exception) {
                Log.e(TAG, "Exception during upload: ${e.message}", e)
                null
            }
        }
    }
}
