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

        root.addView(label("API token"))
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
                "• Hugging Face: huggingface.co/settings/tokens"
            setTextColor(Color.parseColor("#808894"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(20), 0, 0)
        })
    }

    private fun loadProvider(p: String) {
        val prefs = getSharedPreferences(Summarizer.PREFS, MODE_PRIVATE)
        tokenField.setText(prefs.getString("token_$p", ""))
        modelField.setText(prefs.getString("model_$p", Summarizer.defaultModel(p)))
        modelField.hint = Summarizer.defaultModel(p)
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
