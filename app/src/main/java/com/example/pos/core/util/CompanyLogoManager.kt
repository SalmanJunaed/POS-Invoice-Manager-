package com.example.pos.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.R
import java.io.File
import java.io.InputStream

/**
 * Centralized local storage and management for the Company Logo.
 * Ensures 100% offline persistence across app/device restarts.
 */
object CompanyLogoManager {

    private const val TAG = "CompanyLogoManager"
    const val LOGO_FILE_NAME = "company_logo.png"

    /**
     * Returns the dedicated private local file location for the company logo.
     */
    fun getLogoFile(context: Context): File {
        return File(context.filesDir, LOGO_FILE_NAME)
    }

    /**
     * Copies an image from a content Uri (e.g. Photo Picker) into private app storage.
     * Overwrites any previous logo file cleanly to avoid storage duplication.
     * Returns the absolute path of the saved file or null on failure.
     */
    fun copyLogoFromUri(context: Context, sourceUri: Uri): String? {
        return try {
            val inputStream: InputStream = context.contentResolver.openInputStream(sourceUri)
                ?: return null
            val targetFile = getLogoFile(context)
            targetFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            inputStream.close()
            Log.d(TAG, "Logo saved successfully from Uri: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
            targetFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy logo from Uri: $sourceUri", e)
            null
        }
    }

    /**
     * Copies the built-in 512x512 official company logo into private app storage.
     * Provides an immediate offline way to configure or restore the official branding.
     */
    fun copyDefaultOfficialLogo(context: Context): String? {
        return try {
            val inputStream: InputStream = context.resources.openRawResource(R.drawable.logo_original)
            val targetFile = getLogoFile(context)
            targetFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            inputStream.close()
            Log.d(TAG, "Default official logo saved to: ${targetFile.absolutePath}")
            targetFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy default official logo", e)
            null
        }
    }

    /**
     * Removes the currently stored company logo from local disk.
     */
    fun removeLogo(context: Context): Boolean {
        val file = getLogoFile(context)
        return if (file.exists()) {
            val deleted = file.delete()
            Log.d(TAG, "Logo file deleted: $deleted")
            deleted
        } else {
            true
        }
    }

    /**
     * Verifies if a valid, non-empty logo file is present at the given path or in default storage.
     */
    fun getEffectiveLogoFile(context: Context, logoUri: String?): File? {
        if (!logoUri.isNullOrBlank()) {
            val file = File(logoUri)
            if (file.exists() && file.length() > 0) {
                return file
            }
        }
        val defaultFile = getLogoFile(context)
        return if (defaultFile.exists() && defaultFile.length() > 0) defaultFile else null
    }

    /**
     * Safely decodes a scaled down Bitmap without loading massive unneeded byte data into memory.
     */
    fun loadScaledBitmap(context: Context, logoUri: String?, reqWidth: Int = 256, reqHeight: Int = 256): Bitmap? {
        val file = getEffectiveLogoFile(context, logoUri)
        return try {
            if (file != null && file.exists() && file.length() > 0) {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(file.absolutePath, options)

                options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                options.inJustDecodeBounds = false

                BitmapFactory.decodeFile(file.absolutePath, options)
            } else {
                // Read from canonical drawable resource
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeResource(context.resources, R.drawable.logo_original, options)

                options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                options.inJustDecodeBounds = false

                BitmapFactory.decodeResource(context.resources, R.drawable.logo_original, options)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode scaled logo bitmap", e)
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
