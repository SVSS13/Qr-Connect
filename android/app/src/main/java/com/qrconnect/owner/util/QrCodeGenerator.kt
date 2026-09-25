package com.qrconnect.owner.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object QrCodeGenerator {

    fun generateQrBitmap(
        content: String,
        width: Int = 600,
        height: Int = 600,
        darkColor: Int = Color.parseColor("#0F172A"),
        lightColor: Int = Color.WHITE
    ): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
                EncodeHintType.MARGIN to 1
            )
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, width, height, hints)
            val matrixWidth = bitMatrix.width
            val matrixHeight = bitMatrix.height
            val pixels = IntArray(matrixWidth * matrixHeight)

            for (y in 0 until matrixHeight) {
                val offset = y * matrixWidth
                for (x in 0 until matrixWidth) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) darkColor else lightColor
                }
            }

            Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, matrixWidth, 0, 0, matrixWidth, matrixHeight)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getQrScanUrl(token: String, context: Context): String {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val mode = prefs.getString(Constants.KEY_QR_HOST_MODE, Constants.MODE_DEV_LOCALHOST)
        val customHost = prefs.getString(Constants.KEY_CUSTOM_QR_HOST, "")?.trim().orEmpty()

        if (customHost.isNotBlank()) {
            val prefix = if (customHost.startsWith("http://") || customHost.startsWith("https://")) customHost else "https://$customHost"
            val cleanPrefix = if (prefix.endsWith("/")) prefix else "$prefix/"
            return "${cleanPrefix}q/$token"
        }

        return when (mode) {
            Constants.MODE_DEV_LOCALHOST -> "${Constants.URL_DEV_LOCALHOST}$token"
            Constants.MODE_PROD_RENDER -> "${Constants.URL_PROD_RENDER}$token"
            Constants.MODE_PROD_VANITY -> "${Constants.URL_PROD_VANITY}$token"
            else -> "${Constants.URL_DEV_LOCALHOST}$token"
        }
    }

    /**
     * Creates a high-res printable Family QR card with template, family photo, and dynamic QR code.
     */
    fun createPrintableQrCard(
        context: Context,
        cardName: String,
        category: String,
        qrBitmap: Bitmap,
        scanUrl: String
    ): Bitmap {
        return try {
            val assetManager = context.assets
            val templateBitmap = BitmapFactory.decodeStream(assetManager.open("template_cutout.png"))
            val familyBitmap = BitmapFactory.decodeStream(assetManager.open("family_photo.png"))
            val pillBitmap = BitmapFactory.decodeStream(assetManager.open("scan_pill.png"))
            val logoBitmap = BitmapFactory.decodeStream(assetManager.open("qr_center_logo.png"))

            val w = 1024
            val h = 1024
            val card = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(card)

            // 1. Clean white backing for photo frame
            val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            val photoBacking = RectF(30f, 200f, 540f, 700f)
            canvas.drawRoundRect(photoBacking, 70f, 70f, whitePaint)

            // 2. Draw family photo into left frame
            val familyDest = RectF(30f, 202f, 545f, 697f)
            canvas.drawBitmap(familyBitmap, null, familyDest, Paint(Paint.FILTER_BITMAP_FLAG))

            // 3. Draw template frame over it (transparent window allows family photo to show through)
            canvas.drawBitmap(templateBitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))

            // 4. Draw SCAN TO CONNECT pill
            val pillDest = RectF(600f, 218f, 905f, 274f)
            canvas.drawBitmap(pillBitmap, null, pillDest, Paint(Paint.FILTER_BITMAP_FLAG))

            // 5. Draw QR code
            val qrDest = RectF(593f, 292f, 938f, 637f)
            canvas.drawBitmap(qrBitmap, null, qrDest, Paint(Paint.FILTER_BITMAP_FLAG))

            // 6. Draw center logo in QR code
            val logoDest = RectF(718f, 417f, 813f, 512f)
            canvas.drawBitmap(logoBitmap, null, logoDest, Paint(Paint.FILTER_BITMAP_FLAG))

            card
        } catch (e: Exception) {
            e.printStackTrace()
            fallbackCard(cardName, category, qrBitmap, scanUrl)
        }
    }

    fun createPrintableQrCard(
        cardName: String,
        category: String,
        qrBitmap: Bitmap,
        maskedUrl: String
    ): Bitmap {
        return fallbackCard(cardName, category, qrBitmap, maskedUrl)
    }

    private fun fallbackCard(
        cardName: String,
        category: String,
        qrBitmap: Bitmap,
        maskedUrl: String
    ): Bitmap {
        val cardWidth = 800
        val cardHeight = 1100
        val cardBitmap = Bitmap.createBitmap(cardWidth, cardHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(cardBitmap)

        val bgPaint = Paint().apply {
            color = Color.parseColor("#0F172A")
            isAntiAlias = true
        }
        val cardRect = RectF(0f, 0f, cardWidth.toFloat(), cardHeight.toFloat())
        canvas.drawRoundRect(cardRect, 48f, 48f, bgPaint)

        val innerPaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
        }
        val qrBox = RectF(90f, 260f, (cardWidth - 90).toFloat(), 880f)
        canvas.drawRoundRect(qrBox, 36f, 36f, innerPaint)

        val qrDest = RectF(120f, 290f, (cardWidth - 120).toFloat(), 850f)
        canvas.drawBitmap(qrBitmap, null, qrDest, Paint(Paint.FILTER_BITMAP_FLAG))

        val brandPaint = Paint().apply {
            color = Color.parseColor("#818CF8")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("QR CONNECT • INSTANT CONTACT", cardWidth / 2f, 90f, brandPaint)

        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 48f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText(cardName, cardWidth / 2f, 160f, titlePaint)

        val catPaint = Paint().apply {
            color = Color.parseColor("#34D399")
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("TAG: ${category.uppercase()}", cardWidth / 2f, 210f, catPaint)

        val footerPaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 24f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("Scan with any mobile camera to contact owner", cardWidth / 2f, 950f, footerPaint)

        val urlPaint = Paint().apply {
            color = Color.parseColor("#818CF8")
            textSize = 22f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText(maskedUrl, cardWidth / 2f, 1010f, urlPaint)

        return cardBitmap
    }

    /**
     * Shares the QR code directly as a PNG image to any app (WhatsApp, Telegram, etc.).
     */
    fun shareQrImage(context: Context, cardName: String, bitmap: Bitmap) {
        try {
            val cachePath = File(context.cacheDir, "qr_codes").apply { mkdirs() }
            val fileName = "QR_${cardName.replace("[^a-zA-Z0-9]".toRegex(), "_")}.png"
            val file = File(cachePath, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "QR Connect - $cardName")
                putExtra(Intent.EXTRA_TEXT, "Scan this QR code to reach me securely without sharing private numbers.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share QR Code Image")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error sharing QR code: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Exports and saves the QR code PNG image to the device's Pictures/QRConnect folder.
     */
    fun exportQrImageToGallery(context: Context, cardName: String, bitmap: Bitmap): Boolean {
        val safeName = "QR_${cardName.replace("[^a-zA-Z0-9]".toRegex(), "_")}_${System.currentTimeMillis()}.png"
        return try {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/QRConnect")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (imageUri != null) {
                resolver.openOutputStream(imageUri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                }
                Toast.makeText(context, "Saved to Pictures/QRConnect!", Toast.LENGTH_LONG).show()
                true
            } else {
                Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Export error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            false
        }
    }
}
