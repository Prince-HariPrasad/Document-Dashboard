package com.assistant.board

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import kotlin.math.min

class PdfViewerActivity : AppCompatActivity() {

    // Cap pages rendered at once to avoid OOM on a 4 GB device.
    private val maxPages = 40

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val path = intent.getStringExtra("path")
        if (path == null) {
            finish(); return
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#111118"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val scroll = ScrollView(this).apply { addView(column) }
        setContentView(scroll)
        title = File(path).name

        try {
            renderPdf(File(path), column)
        } catch (e: Exception) {
            Toast.makeText(this, "Can't open PDF: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun renderPdf(file: File, column: LinearLayout) {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val total = renderer.pageCount
        val shown = min(total, maxPages)
        val width = resources.displayMetrics.widthPixels - dp(16)

        for (i in 0 until shown) {
            val page = renderer.openPage(i)
            val height = (width.toFloat() * page.height / page.width).toInt()
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            column.addView(ImageView(this).apply {
                setImageBitmap(bmp)
                adjustViewBounds = true
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp(4), 0, dp(4)) }
            })
        }
        renderer.close()
        pfd.close()

        if (total > shown) {
            column.addView(TextView(this).apply {
                text = "Showing first $shown of $total pages"
                setTextColor(Color.parseColor("#AAAAAA"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(dp(8), dp(12), dp(8), dp(16))
            })
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
