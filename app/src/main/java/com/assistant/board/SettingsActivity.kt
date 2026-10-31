package com.assistant.board

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private val glassFill = Color.parseColor("#24FFFFFF")
    private val glassStroke = Color.parseColor("#40FFFFFF")
    private val accent = Color.parseColor("#4A9EFF")

    private lateinit var tokenField: EditText
    private lateinit var modelField: EditText
    private lateinit var tokenLabel: TextView
    private lateinit var detectBtn: Button
    private lateinit var detectInfo: TextView
    private lateinit var spinner: Spinner
    private var current = "GEMINI"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(Summarizer.PREFS, MODE_PRIVATE)
        current = prefs.getString("provider", "GEMINI") ?: "GEMINI"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#0C0C0F"), Color.parseColor("#141418"), Color.parseColor("#1B1B22"))
            )
            setPadding(dp(18), dp(20), dp(18), dp(20))
        }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        window.statusBarColor = Color.parseColor("#0C0C0F")
        window.navigationBarColor = Color.parseColor("#0C0C0F")
        title = "Settings"

        root.addView(TextView(this).apply {
            text = "AI Summarizer"
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
        })
        root.addView(TextView(this).apply {
            text = "Choose a provider and paste its API token. Summaries run on-device " +
                "using this provider — no laptop needed."
            setTextColor(Color.parseColor("#9CA3AF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(4), 0, dp(18))
        })

        root.addView(label("Provider"))
        spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@SettingsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                Summarizer.PROVIDERS.map { Summarizer.providerLabel(it) }
            )
            setSelection(Summarizer.PROVIDERS.indexOf(current).coerceAtLeast(0))
            background = glass(12)
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        root.addView(spinner, rowParams())

        tokenLabel = label("API token")
        root.addView(tokenLabel)
        tokenField = EditText(this).apply {
            hint = "Paste token here"
            setHintTextColor(Color.parseColor("#808894"))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            background = glass(12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        root.addView(tokenField, rowParams())

        root.addView(label("Model (optional)"))
        modelField = EditText(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setHintTextColor(Color.parseColor("#808894"))
            background = glass(12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        root.addView(modelField, rowParams())

        detectBtn = Button(this).apply {
            text = "Detect models"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = glass(12)
            stateListAnimator = null
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            visibility = View.GONE
            setOnClickListener { detectModels() }
        }
        root.addView(detectBtn, rowParams().apply { topMargin = dp(8) })
        detectInfo = TextView(this).apply {
            setTextColor(Color.parseColor("#9CA3AF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(6), 0, 0)
        }
        root.addView(detectInfo)

        // Load fields for the initially-selected provider, and on change.
        loadProvider(current)
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                saveCurrentFields()
                current = Summarizer.PROVIDERS[pos]
                loadProvider(current)
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }

        root.addView(Button(this).apply {
            text = "Save"
            isAllCaps = false
            setTextColor(Color.parseColor("#0C0C0F"))
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(accent); cornerRadius = dp(12).toFloat()
            }
            stateListAnimator = null
            setPadding(dp(18), dp(12), dp(18), dp(12))
            setOnClickListener { saveCurrentFields(); saveProviderChoice(); finish() }
        }, rowParams().apply { topMargin = dp(20) })

        root.addView(TextView(this).apply {
            text = "Where to get tokens:\n" +
                "• Gemini: aistudio.google.com/apikey\n" +
                "• Grok: console.x.ai\n" +
                "• Hugging Face: huggingface.co/settings/tokens\n" +
                "• Local: on the model phone run Ollama (Termux: OLLAMA_HOST=0.0.0.0 ollama serve), " +
                "then enter http://<that-phone-ip>:11434 and tap Detect."
            setTextColor(Color.parseColor("#808894"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(20), 0, 0)
        })
    }

    private fun detectModels() {
        val url = tokenField.text.toString().trim()
        if (url.isBlank()) {
            detectInfo.text = "Enter the server URL first (e.g. http://192.168.1.50:11434)"
            return
        }
        detectInfo.text = "Detecting models at $url\u2026"
        Thread {
            val res = try {
                "OK:" + Summarizer.detectModels(url).joinToString(", ")
            } catch (e: Exception) {
                "ERR:" + (e.message ?: "not reachable")
            }
            runOnUiThread {
                if (res.startsWith("OK:")) {
                    val models = res.removePrefix("OK:")
                    detectInfo.text = "Found: $models"
                    if (modelField.text.toString().isBlank())
                        modelField.setText(models.substringBefore(",").trim())
                } else {
                    detectInfo.text = "Not reachable: " + res.removePrefix("ERR:") +
                        "\nEnsure the server is running and both phones are on the same Wi-Fi."
                }
            }
        }.start()
    }

    private fun loadProvider(p: String) {
        val prefs = getSharedPreferences(Summarizer.PREFS, MODE_PRIVATE)
        tokenField.setText(prefs.getString("token_$p", ""))
        modelField.setText(prefs.getString("model_$p", Summarizer.defaultModel(p)))
        modelField.hint = Summarizer.defaultModel(p)
        if (Summarizer.isLocal(p)) {
            tokenLabel.text = "Server URL"
            tokenField.hint = "http://192.168.1.50:11434"
            tokenField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            detectBtn.visibility = View.VISIBLE
            detectInfo.text = "Tap Detect to list models on your local server."
        } else {
            tokenLabel.text = "API token"
            tokenField.hint = "Paste token here"
            tokenField.inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            detectBtn.visibility = View.GONE
            detectInfo.text = ""
        }
    }

    private fun saveCurrentFields() {
        getSharedPreferences(Summarizer.PREFS, MODE_PRIVATE).edit()
            .putString("token_$current", tokenField.text.toString().trim())
            .putString("model_$current", modelField.text.toString().trim())
            .apply()
    }

    private fun saveProviderChoice() {
        getSharedPreferences(Summarizer.PREFS, MODE_PRIVATE).edit()
            .putString("provider", current)
            .apply()
        Toast.makeText(this, "Saved — using ${Summarizer.providerLabel(current)}", Toast.LENGTH_SHORT).show()
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.parseColor("#C8CFDC"))
        setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setPadding(0, dp(12), 0, dp(6))
    }

    private fun rowParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun glass(radius: Int) = GradientDrawable().apply {
        setColor(glassFill)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), glassStroke)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
