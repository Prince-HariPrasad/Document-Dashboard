package com.assistant.board

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateUtils
import android.text.format.Formatter
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Doc(
    val name: String,
    val path: String,
    val type: String,
    val size: Long,
    val summary: String
)

class MainActivity : AppCompatActivity() {

    private lateinit var tabBar: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var hintView: TextView
    private lateinit var listHeader: LinearLayout
    private lateinit var recycler: RecyclerView
    private lateinit var boardAdapter: BoardAdapter
    private lateinit var overviewScroll: ScrollView
    private lateinit var listCol: LinearLayout
    private var docs: MutableList<Doc> = mutableListOf()
    private var currentTab: String = "Overview"
    private var query: String = ""
    private var sortMode: Int = 0 // 0 = Name, 1 = Size, 2 = Date
    private var selectionMode: Boolean = false
    private val selected: MutableSet<String> = mutableSetOf()
    private lateinit var searchBox: EditText
    private val docExts = setOf("pdf", "pptx", "ppt", "docx", "doc", "txt", "md", "xlsx", "xls")

    private val accent = Color.parseColor("#4A9EFF")
    private val glassFill = Color.parseColor("#24FFFFFF")
    private val glassStroke = Color.parseColor("#40FFFFFF")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = appGradient()
            setPadding(0, dp(18), 0, 0)
        }

        val title = TextView(this).apply {
            text = "Document Dashboard"
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            setPadding(dp(16), 0, dp(16), dp(2))
            setOnClickListener { loadData(); buildTabs(); renderContent(toast = true) }
        }
        hintView = TextView(this).apply {
            setTextColor(Color.parseColor("#9CA3AF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(16), 0, dp(16), dp(12))
            setOnClickListener { loadData(); buildTabs(); renderContent(toast = true) }
        }

        val tabScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isHorizontalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(36))
        }
        tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), 0, dp(10), dp(8))
        }
        tabScroll.addView(tabBar)

        overviewScroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(6), dp(16), dp(24))
        }
        overviewScroll.addView(content)

        listHeader = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(6), dp(16), 0)
        }
        recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            setPadding(dp(16), 0, dp(16), dp(24))
            clipToPadding = false
            setItemViewCacheSize(16)
        }
        boardAdapter = BoardAdapter(emptyList())
        recycler.adapter = boardAdapter
        listCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(listHeader)
            addView(
                recycler,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            )
        }
        val contentContainer = FrameLayout(this).apply {
            addView(overviewScroll)
            addView(listCol)
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), dp(8))
        }
        searchBox = EditText(this).apply {
            hint = "Search name or summary\u2026"
            setHintTextColor(Color.parseColor("#808894"))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            background = glass(14, glassFill, glassStroke)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            compoundDrawablePadding = dp(6)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnFocusChangeListener { _, focused ->
                background = glass(14, glassFill, if (focused) accent else glassStroke)
            }
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString()?.trim() ?: ""
                    val clear = if (query.isEmpty()) null else
                        ContextCompat.getDrawable(this@MainActivity,
                            android.R.drawable.ic_menu_close_clear_cancel)
                    setCompoundDrawablesWithIntrinsicBounds(null, null, clear, null)
                    renderContent()
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_UP && compoundDrawables[2] != null &&
                    e.x > width - totalPaddingRight - dp(8)
                ) {
                    setText(""); clearFocus(); true
                } else false
            }
        }
        val sortBtn = Button(this).apply {
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = glass(14, Color.parseColor("#2EFFFFFF"), glassStroke)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            stateListAnimator = null
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            text = "\u21C5 Name"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT
            ).apply { setMargins(dp(8), 0, 0, 0) }
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                sortMode = (sortMode + 1) % 3
                text = "\u21C5 " + sortLabel()
                renderContent()
            }
        }
        controls.addView(searchBox)
        controls.addView(sortBtn)

        val gear = TextView(this).apply {
            text = "\u2699"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 23f)
            setPadding(dp(12), dp(2), dp(16), dp(2))
            setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        title.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        titleRow.addView(title)
        titleRow.addView(gear)
        root.addView(titleRow)
        root.addView(hintView)
        root.addView(tabScroll)
        root.addView(controls)
        root.addView(
            contentContainer,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        setContentView(root)
        window.statusBarColor = Color.parseColor("#0C0C0F")
        window.navigationBarColor = Color.parseColor("#0C0C0F")

        // First launch: ask for storage access so scanning works.
        val p = getSharedPreferences(Summarizer.PREFS, MODE_PRIVATE)
        if (!p.getBoolean("asked_storage", false)) {
            p.edit().putBoolean("asked_storage", true).apply()
            if (!Environment.isExternalStorageManager()) ensureStorage()
        }
    }

    override fun onResume() {
        super.onResume()
        loadData()
        buildTabs()
        renderContent()
    }

    // Internal store = source of truth (always writable, so edits/deletes persist).
    private fun internalDataFile() = File(filesDir, "summary.json")

    // External file is where board.py pushes; imported when it is newer.
    private fun externalDataFile(): File? =
        getExternalFilesDir(null)?.let { File(it, "summary.json") }

    private fun loadData() {
        docs = mutableListOf()
        val internal = internalDataFile()
        val external = externalDataFile()
        // Import a newer PC-pushed file into our internal store (one-way sync).
        if (external != null && external.exists() &&
            (!internal.exists() || external.lastModified() > internal.lastModified())
        ) {
            try { internal.writeText(external.readText()) } catch (_: Exception) {}
        }
        val f = internal
        if (!f.exists()) {
            hintView.text = "No data yet — run board.py or summarize in-app"
            return
        }
        try {
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                docs.add(
                    Doc(
                        name = o.optString("name"),
                        path = o.optString("path"),
                        type = o.optString("type", "OTHER").uppercase(),
                        size = o.optLong("size", 0),
                        summary = o.optString("summary")
                    )
                )
            }
            hintView.text =
                "${docs.size} documents  \u2022  updated " +
                DateUtils.getRelativeTimeSpanString(f.lastModified()) + "   \u27F3"
        } catch (e: Exception) {
            hintView.text = "Could not read data: ${e.message}"
        }
    }

    private fun typesSorted(): List<String> =
        docs.groupBy { it.type }.entries
            .sortedByDescending { it.value.size }
            .map { it.key }

    private fun buildTabs() {
        tabBar.removeAllViews()
        val tabs = mutableListOf("Overview")
        tabs.addAll(typesSorted())
        tabs.add("Unscanned")
        if (currentTab !in tabs) currentTab = "Overview"

        for (t in tabs) {
            val selected = t == currentTab
            val label = when (t) {
                "Overview", "Unscanned" -> t
                else -> "$t (${docs.count { it.type == t }})"
            }
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(dp(4), 0, dp(4), 0) }
            }
            val btn = Button(this).apply {
                text = label
                isAllCaps = false
                setTextColor(if (selected) Color.WHITE else Color.parseColor("#9AA0AC"))
                setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
                background = glass(
                    18,
                    if (selected) Color.parseColor("#2EFFFFFF") else Color.parseColor("#12FFFFFF"),
                    if (selected) glassStroke else Color.parseColor("#26FFFFFF")
                )
                setPadding(dp(16), dp(9), dp(16), dp(9))
                minHeight = dp(44)
                minimumHeight = dp(44)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                stateListAnimator = null
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    currentTab = t; buildTabs(); renderContent()
                }
            }
            val underline = View(this).apply {
                background = GradientDrawable().apply {
                    setColor(if (selected) accent else Color.TRANSPARENT)
                    cornerRadius = dp(2).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(3)
                ).apply { setMargins(dp(12), dp(5), dp(12), 0) }
            }
            cell.addView(btn)
            cell.addView(underline)
            tabBar.addView(cell)
        }
    }

    private fun renderContent(toast: Boolean = false) {
        if (toast) Toast.makeText(this, "Reloaded \u2014 ${docs.size} docs", Toast.LENGTH_SHORT).show()
        when (currentTab) {
            "Unscanned" -> {
                // Always scans, even with an empty board (that's how you add files).
                showOverview(); content.removeAllViews()
                if (selectionMode && selected.isNotEmpty()) content.addView(selectionBar())
                renderUnscanned()
            }
            "Overview" -> {
                showOverview(); content.removeAllViews()
                if (docs.isEmpty()) content.addView(emptyBoard()) else renderOverview()
            }
            else -> {
                if (docs.isEmpty()) {
                    showOverview(); content.removeAllViews(); content.addView(emptyBoard())
                } else {
                    renderTypeList(currentTab)
                }
            }
        }
    }

    private fun showOverview() {
        overviewScroll.visibility = View.VISIBLE
        listCol.visibility = View.GONE
    }

    private fun showList() {
        overviewScroll.visibility = View.GONE
        listCol.visibility = View.VISIBLE
    }

    private fun renderOverview() {
        content.addView(sectionLabel("File type distribution"))
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(20, glassFill, glassStroke)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val groups = docs.groupBy { it.type }.entries
            .sortedByDescending { it.value.size }
        val maxRoot = kotlin.math.sqrt(groups.maxOf { it.value.size }.toDouble()).toFloat()

        for (g in groups) {
            val count = g.value.size
            val root = kotlin.math.sqrt(count.toDouble()).toFloat()
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(8), 0, dp(8))
            }
            val head = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            head.addView(View(this).apply {
                background = GradientDrawable().apply {
                    setColor(typeColor(g.key)); cornerRadius = dp(3).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(dp(10), dp(10))
                    .apply { setMargins(0, 0, dp(8), 0) }
            })
            head.addView(TextView(this).apply {
                text = g.key
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                layoutParams = LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            head.addView(TextView(this).apply {
                text = "$count"
                setTextColor(Color.parseColor("#C8CFDC"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            })
            row.addView(head)
            val barWrap = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(5), 0, 0)
            }
            barWrap.addView(View(this).apply {
                background = GradientDrawable().apply {
                    setColor(withAlpha(typeColor(g.key), 0xE6))
                    cornerRadius = dp(6).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(0, dp(12), root)
            })
            barWrap.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(12), maxRoot - root)
            })
            row.addView(barWrap)
            panel.addView(row)
        }

        val totalSize = docs.sumOf { it.size }
        content.addView(panel)

        content.addView(sectionLabel("Insights"))
        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(20, glassFill, glassStroke)
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }
        val mostCommon = groups.firstOrNull()
        val bySize = docs.groupBy { it.type }.mapValues { e -> e.value.sumOf { it.size } }
        val largestType = bySize.maxByOrNull { it.value }
        val biggestFile = docs.maxByOrNull { it.size }
        statRow(stats, "Total documents", "${docs.size}")
        statRow(stats, "Total size", Formatter.formatShortFileSize(this, totalSize))
        if (mostCommon != null)
            statRow(stats, "Most common", "${mostCommon.key} (${mostCommon.value.size})")
        if (largestType != null)
            statRow(stats, "Largest by size",
                "${largestType.key} (${Formatter.formatShortFileSize(this, largestType.value)})")
        if (biggestFile != null)
            statRow(stats, "Biggest single file",
                Formatter.formatShortFileSize(this, biggestFile.size))
        content.addView(stats)
    }

    private fun statRow(parent: LinearLayout, label: String, value: String) {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(6))
        }
        r.addView(TextView(this).apply {
            text = label
            setTextColor(Color.parseColor("#9CA3AF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            layoutParams = LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        r.addView(TextView(this).apply {
            text = value
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        })
        parent.addView(r)
    }

    private fun currentTypeList(type: String): List<Doc> {
        var list = docs.filter { it.type == type }
        if (query.isNotBlank()) {
            list = list.filter {
                it.name.contains(query, true) || it.summary.contains(query, true)
            }
        }
        return when (sortMode) {
            1 -> list.sortedByDescending { it.size }
            2 -> list.sortedByDescending { File(it.path).lastModified() }
            else -> list.sortedBy { it.name.lowercase() }
        }
    }

    private fun renderTypeList(type: String) {
        showList()
        val list = currentTypeList(type)
        refreshListHeader(type, list.size)
        boardAdapter.setItems(list)
        recycler.scrollToPosition(0)
    }

    private fun refreshListHeader(type: String = currentTab, count: Int = -1) {
        val n = if (count >= 0) count else currentTypeList(type).size
        listHeader.removeAllViews()
        if (selectionMode && selected.isNotEmpty()) listHeader.addView(selectionBar())
        val header = if (query.isBlank()) "$type  \u2022  $n files"
        else "$type  \u2022  $n results"
        listHeader.addView(sectionLabel(header))
    }

    inner class BoardAdapter(private var items: List<Doc>) :
        RecyclerView.Adapter<BoardAdapter.VH>() {

        fun setItems(newItems: List<Doc>) {
            items = newItems
            notifyDataSetChanged()
        }

        fun notifyPathChanged(path: String) {
            val idx = items.indexOfFirst { it.path == path }
            if (idx >= 0) notifyItemChanged(idx)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = this@MainActivity
            val badge = TextView(ctx).apply {
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(6), dp(8), dp(6))
                minWidth = dp(46)
            }
            val name = TextView(ctx).apply {
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            }
            val size = TextView(ctx).apply {
                setTextColor(Color.parseColor("#AEB6C6"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                setPadding(0, dp(2), 0, 0)
            }
            val titleCol = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), 0, dp(6), 0)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(name); addView(size)
            }
            val top = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(badge); addView(titleCol)
            }
            val summary = TextView(ctx).apply {
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(8), 0, 0)
            }
            val renameBtn = pillButton("Rename",
                Color.parseColor("#00000000"), Color.parseColor("#5A606C")) {}
                .apply { setTextColor(Color.parseColor("#C9CFDA")) }
            val editBtn = pillButton("Edit",
                Color.parseColor("#1F4A9EFF"), Color.parseColor("#4A9EFF")) {}
                .apply { setTextColor(Color.parseColor("#9CCBFF")) }
            val deleteBtn = pillButton("Delete",
                Color.parseColor("#1FE63946"), Color.parseColor("#E63946")) {}
                .apply { setTextColor(Color.parseColor("#FF8A8A")) }
            val actions = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, 0)
                addView(renameBtn)
                addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(1), 1f)
                })
                addView(editBtn)
                addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(8), dp(1))
                })
                addView(deleteBtn)
            }
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                elevation = dp(4).toFloat()
                setPadding(dp(14), dp(12), dp(12), dp(10))
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp(5), 0, dp(5)) }
                addView(top); addView(summary); addView(actions)
            }
            return VH(card, badge, name, size, summary, actions, renameBtn, editBtn, deleteBtn)
        }

        inner class VH(
            private val card: LinearLayout,
            private val badge: TextView,
            private val name: TextView,
            private val size: TextView,
            private val summary: TextView,
            private val actions: LinearLayout,
            private val renameBtn: Button,
            private val editBtn: Button,
            private val deleteBtn: Button
        ) : RecyclerView.ViewHolder(card) {
            fun bind(doc: Doc) {
                val isSel = selected.contains(doc.path)
                card.background = glass(16,
                    if (isSel) withAlpha(accent, 0x33) else glassFill,
                    if (isSel) accent else glassStroke)
                val c = typeColor(doc.type)
                badge.text = doc.type
                badge.background = GradientDrawable().apply {
                    setColor(withAlpha(c, 0x55))
                    cornerRadius = dp(9).toFloat()
                    setStroke(dp(1), withAlpha(c, 0xC0))
                }
                name.text = doc.name
                name.tooltipText = doc.name
                size.text = Formatter.formatShortFileSize(this@MainActivity, doc.size)
                val noText = doc.summary.startsWith("(")
                summary.text = if (noText) "\u24D8 " + doc.summary.trim('(', ')') else doc.summary
                summary.setTextColor(
                    if (noText) Color.parseColor("#7D8491") else Color.parseColor("#C8CFDC"))
                summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (noText) 11.5f else 12.5f)
                actions.visibility = if (selectionMode) View.GONE else View.VISIBLE
                renameBtn.setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    confirmRename(doc)
                }
                editBtn.setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    editSummary(doc.name, doc.path, doc.type, doc.size, doc.summary)
                }
                deleteBtn.setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    confirmDelete(doc)
                }
                card.setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    if (selectionMode) toggleSelect(doc) else openFile(doc.path)
                }
                card.setOnLongClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    toggleSelect(doc)
                    true
                }
            }
        }
    }

    private fun pillButton(label: String, fill: Int, stroke: Int, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = glass(13, fill, stroke)
            setPadding(dp(18), dp(6), dp(18), dp(6))
            minWidth = 0
            minimumWidth = 0
            minHeight = dp(40)
            minimumHeight = dp(40)
            stateListAnimator = null
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
        }

    private fun typeColor(type: String): Int = when (type.uppercase()) {
        "PDF" -> Color.parseColor("#E63946")
        "DOC", "DOCX" -> Color.parseColor("#4A9EFF")
        "PPT", "PPTX" -> Color.parseColor("#FFB84A")
        "XLS" -> Color.parseColor("#4ECDC4")
        "XLSX" -> Color.parseColor("#34D399")
        "TXT", "MD" -> Color.parseColor("#9CA3AF")
        else -> accent
    }

    private fun typeBadge(type: String): View = TextView(this).apply {
        text = type
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(6), dp(8), dp(6))
        minWidth = dp(46)
        val c = typeColor(type)
        background = GradientDrawable().apply {
            setColor(withAlpha(c, 0x55))
            cornerRadius = dp(9).toFloat()
            setStroke(dp(1), withAlpha(c, 0xC0))
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha shl 24)

    private fun confirmRename(doc: Doc) {
        val input = EditText(this).apply {
            setText(doc.name)
            setSelection(0, doc.name.substringBeforeLast('.', doc.name).length)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle("Rename file")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Rename") { _, _ ->
                renameDoc(doc, input.text.toString().trim())
            }
            .show()
    }

    private fun renameDoc(doc: Doc, newName: String) {
        if (newName.isBlank() || newName == doc.name) return
        if (newName.contains('/')) {
            Toast.makeText(this, "Name can't contain '/'", Toast.LENGTH_SHORT).show()
            return
        }
        if (!ensureStorage()) return
        val old = File(doc.path)
        val target = File(old.parentFile, newName)
        if (target.exists()) {
            Toast.makeText(this, "A file with that name already exists", Toast.LENGTH_SHORT).show()
            return
        }
        val ok = try {
            old.renameTo(target)
        } catch (e: Exception) {
            false
        }
        if (ok) {
            val idx = docs.indexOf(doc)
            if (idx >= 0) {
                docs[idx] = doc.copy(name = newName, path = target.absolutePath)
            }
            persist()
            buildTabs()
            renderContent()
            Toast.makeText(this, "Renamed to $newName", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Rename failed", Toast.LENGTH_LONG).show()
        }
    }

    private fun ensureStorage(): Boolean {
        if (Environment.isExternalStorageManager()) return true
        AlertDialog.Builder(this)
            .setTitle("Permission needed")
            .setMessage("Grant \"All files access\" so the app can delete, rename, " +
                "and manage your documents.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Grant") { _, _ -> requestAllFilesAccess() }
            .show()
        return false
    }

    private fun requestAllFilesAccess() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (_: Exception) {
                Toast.makeText(this,
                    "Open Settings \u2192 Apps \u2192 Assistant Board \u2192 All files access",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmDelete(doc: Doc) {
        AlertDialog.Builder(this)
            .setTitle("Delete file?")
            .setMessage("${doc.name}\n\nThis permanently deletes the file from your phone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ -> deleteDoc(doc) }
            .show()
    }

    private fun deleteDoc(doc: Doc) {
        if (!ensureStorage()) return
        val ok = try {
            File(doc.path).delete()
        } catch (e: Exception) {
            false
        }
        if (ok) {
            docs.remove(doc)
            persist()
            buildTabs()
            renderContent()
            Toast.makeText(this, "Deleted ${doc.name}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to delete ${doc.name}", Toast.LENGTH_LONG).show()
        }
    }

    private fun persist() {
        try {
            val arr = JSONArray()
            for (d in docs) {
                arr.put(JSONObject().apply {
                    put("name", d.name); put("path", d.path)
                    put("type", d.type); put("size", d.size)
                    put("summary", d.summary)
                })
            }
            dataFile_write(arr.toString())
        } catch (_: Exception) {
        }
    }

    private fun dataFile_write(text: String) {
        // Write to internal (reliable); mirror to external so a PC pull sees edits.
        internalDataFile().writeText(text)
        try { externalDataFile()?.writeText(text) } catch (_: Exception) {}
    }

    private fun renderUnscanned() {
        content.addView(sectionLabel("Unscanned files"))
        if (!Environment.isExternalStorageManager()) {
            content.addView(permissionNeededView())
            return
        }
        val loading = TextView(this).apply {
            text = "Scanning storage\u2026"
            setTextColor(Color.parseColor("#DDDDDD"))
            setPadding(0, dp(10), 0, 0)
        }
        content.addView(loading)

        val base = Environment.getExternalStorageDirectory().absolutePath
        fun rel(p: String) = p.removePrefix("/sdcard/").removePrefix("$base/")
        val known = docs.map { rel(it.path) }.toHashSet()

        Thread {
            val found = mutableListOf<File>()
            var scanned = 0
            val walk = Environment.getExternalStorageDirectory().walkTopDown()
                .onEnter { it.name != "Android" }
            for (f in walk) {
                if (!f.isFile) continue
                scanned++
                if (f.extension.lowercase() in docExts && rel(f.absolutePath) !in known) {
                    found.add(f)
                }
                if (scanned % 250 == 0) {
                    val checked = scanned
                    val got = found.size
                    runOnUiThread {
                        if (currentTab == "Unscanned")
                            loading.text = "Scanning\u2026 $checked files checked, $got unscanned found"
                    }
                }
            }
            runOnUiThread {
                if (currentTab != "Unscanned") return@runOnUiThread
                content.removeView(loading)
                var list: List<File> = found
                if (query.isNotBlank()) list = list.filter { it.name.contains(query, true) }
                list = when (sortMode) {
                    1 -> list.sortedByDescending { it.length() }
                    2 -> list.sortedByDescending { it.lastModified() }
                    else -> list.sortedBy { it.name.lowercase() }
                }
                if (list.isEmpty()) {
                    content.addView(centered("All caught up",
                        "Every document on the phone has been summarized."))
                } else {
                    val progress = TextView(this).apply {
                        text = "${list.size} files not summarized yet"
                        setTextColor(Color.parseColor("#BBBBBB"))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    }
                    val bulkRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, 0, 0, dp(8))
                    }
                    bulkRow.addView(progress,
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    bulkRow.addView(pillButton("Summarize all (${list.size})",
                        Color.parseColor("#1F4A9EFF"), Color.parseColor("#4A9EFF")) {
                        summarizeAllUnscanned(list, progress)
                    }.apply { setTextColor(Color.parseColor("#9CCBFF")) })
                    content.addView(bulkRow)
                    for (f in list) content.addView(unscannedCard(f))
                }
            }
        }.start()
    }

    private fun editSummary(name: String, path: String, type: String, size: Long, existing: String) {
        val input = EditText(this).apply {
            setText(if (existing.startsWith("(") || existing.isBlank()) "" else existing)
            hint = "Type a summary, or tap Generate with AI"
            setPadding(dp(16), dp(12), dp(16), dp(12))
            minLines = 2
        }
        val progress = TextView(this).apply {
            setTextColor(Color.parseColor("#9CA3AF"))
            setPadding(dp(16), 0, dp(16), dp(4))
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(input); addView(progress)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(name)
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Generate with AI", null)
            .setPositiveButton("Save", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                if (!Summarizer.hasToken(this)) {
                    progress.text = "No API token set — open Settings (\u2699)"
                    return@setOnClickListener
                }
                progress.text = "Summarizing with " +
                    Summarizer.providerLabel(Summarizer.currentProvider(this)) + "\u2026"
                Thread {
                    val result = try {
                        Summarizer.summarizeFile(this, File(path))
                    } catch (e: Exception) {
                        "ERR:" + (e.message ?: "failed")
                    }
                    runOnUiThread {
                        if (result.startsWith("ERR:")) progress.text = result.removePrefix("ERR:")
                        else { input.setText(result); progress.text = "" }
                    }
                }.start()
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = input.text.toString().trim()
                if (text.isEmpty()) { progress.text = "Enter a summary first"; return@setOnClickListener }
                upsertDoc(name, path, type, size, text)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun summarizeAllUnscanned(files: List<File>, progress: TextView) {
        if (!Summarizer.hasToken(this)) {
            Toast.makeText(this,
                "Set an API token in Settings (\u2699) first", Toast.LENGTH_LONG).show()
            return
        }
        progress.text = "Starting\u2026"
        Thread {
            var done = 0
            var ok = 0
            for (f in files) {
                val summary = try {
                    val s = Summarizer.summarizeFile(this, f)
                    if (!s.startsWith("(")) ok++
                    s
                } catch (e: Exception) {
                    "(error: ${e.message?.take(40)})"
                }
                done++
                val d = done
                val okc = ok
                val name = f.name
                val path = f.absolutePath
                val type = f.extension.uppercase()
                val size = f.length()
                runOnUiThread {
                    val idx = docs.indexOfFirst { it.path == path }
                    if (idx >= 0) docs[idx] = docs[idx].copy(summary = summary)
                    else docs.add(Doc(name, path, type, size, summary))
                    progress.text = "Summarizing\u2026 $d/${files.size}  ($okc done)"
                    if (d % 10 == 0) persist()
                }
                Thread.sleep(500) // gentle pacing for free-tier rate limits
            }
            runOnUiThread {
                persist()
                buildTabs()
                Toast.makeText(this,
                    "Summarized $ok of ${files.size}", Toast.LENGTH_LONG).show()
                if (currentTab == "Unscanned") renderContent()
            }
        }.start()
    }

    private fun upsertDoc(name: String, path: String, type: String, size: Long, summary: String) {
        val idx = docs.indexOfFirst { it.path == path }
        if (idx >= 0) docs[idx] = docs[idx].copy(summary = summary)
        else docs.add(Doc(name, path, type.uppercase(), size, summary))
        persist()
        buildTabs()
        renderContent()
        Toast.makeText(this, "Summary saved", Toast.LENGTH_SHORT).show()
    }

    private fun unscannedCard(f: File): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(16, glassFill, glassStroke)
            elevation = dp(3).toFloat()
            setPadding(dp(14), dp(12), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(5), 0, dp(5)) }
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                openFile(f.absolutePath)
            }
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(typeBadge(f.extension.uppercase()))
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, dp(6), 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        col.addView(TextView(this).apply {
            text = f.name
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        })
        col.addView(TextView(this).apply {
            text = "Not summarized \u2022 " +
                Formatter.formatShortFileSize(this@MainActivity, f.length())
            setTextColor(Color.parseColor("#AEB6C6"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(2), 0, 0)
        })
        top.addView(col)
        card.addView(top)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(10), 0, 0)
        }
        actions.addView(pillButton("Summarize / Edit",
            Color.parseColor("#1F4A9EFF"), Color.parseColor("#4A9EFF")) {
            editSummary(f.name, f.absolutePath, f.extension, f.length(), "")
        }.apply { setTextColor(Color.parseColor("#9CCBFF")) })
        card.addView(actions)
        return card
    }

    private fun openFile(path: String) {
        // PDFs open in our built-in viewer (the phone has no PDF reader).
        if (path.substringAfterLast('.', "").lowercase() == "pdf") {
            startActivity(Intent(this, PdfViewerActivity::class.java).putExtra("path", path))
            return
        }
        try {
            val uri: Uri = FileProvider.getUriForFile(
                this, "$packageName.fileprovider", File(path)
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeOf(path))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(this,
                "No app installed to open this file type", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Can't open: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun mimeOf(name: String): String =
        when (name.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "txt", "md" -> "text/plain"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            else -> "*/*"
        }

    private fun sortLabel(): String = when (sortMode) {
        1 -> "Size"
        2 -> "Date"
        else -> "Name"
    }

    private fun toggleSelect(doc: Doc) {
        val entering = !selectionMode
        if (!selected.add(doc.path)) selected.remove(doc.path)
        if (selected.isEmpty()) {
            // leaving selection mode — show action buttons again, keep scroll
            selectionMode = false
            refreshListHeader()
            boardAdapter.notifyDataSetChanged()
            return
        }
        selectionMode = true
        refreshListHeader()
        if (entering) {
            // first selection: rebind visible cards to hide action buttons, keep scroll
            boardAdapter.notifyDataSetChanged()
        } else {
            boardAdapter.notifyPathChanged(doc.path)
        }
    }

    private fun selectionBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = glass(14, withAlpha(accent, 0x2E), accent)
            setPadding(dp(14), dp(8), dp(10), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(8)) }
        }
        bar.addView(TextView(this).apply {
            text = "${selected.size} selected"
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            layoutParams = LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        bar.addView(pillButton("Cancel",
            Color.parseColor("#00000000"), Color.parseColor("#5A606C")) {
            selectionMode = false; selected.clear(); renderContent()
        }.apply { setTextColor(Color.parseColor("#C9CFDA")) })
        bar.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(1))
        })
        bar.addView(pillButton("Delete (${selected.size})",
            Color.parseColor("#1FE63946"), Color.parseColor("#E63946")) {
            confirmDeleteSelected()
        }.apply { setTextColor(Color.parseColor("#FF8A8A")) })
        return bar
    }

    private fun confirmDeleteSelected() {
        if (selected.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Delete ${selected.size} files?")
            .setMessage("This permanently deletes the selected files from your phone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ -> deleteSelected() }
            .show()
    }

    private fun deleteSelected() {
        if (!ensureStorage()) return
        var ok = 0
        var fail = 0
        val targets = docs.filter { selected.contains(it.path) }
        for (d in targets) {
            val deleted = try {
                File(d.path).delete()
            } catch (e: Exception) {
                false
            }
            if (deleted) {
                docs.remove(d); ok++
            } else {
                fail++
            }
        }
        selected.clear()
        selectionMode = false
        persist()
        buildTabs()
        renderContent()
        val msg = "Deleted $ok file(s)" + if (fail > 0) ", $fail failed" else ""
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun sectionLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setPadding(0, dp(12), 0, dp(10))
    }

    private fun emptyBoard(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(48), dp(24), dp(48))
        }
        col.addView(TextView(this).apply {
            text = "No documents yet"
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
        })
        col.addView(TextView(this).apply {
            text = "Scan your phone to find documents, then summarize them " +
                "with AI or by hand."
            setTextColor(Color.parseColor("#9CA3AF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(20))
        })
        col.addView(pillButton("Scan my documents",
            withAlpha(accent, 0x33), accent) {
            currentTab = "Unscanned"
            buildTabs()
            renderContent()
        }.apply { setTextColor(Color.WHITE) })
        col.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(1), dp(10))
        })
        col.addView(pillButton("AI Settings (\u2699)",
            Color.parseColor("#22FFFFFF"), glassStroke) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }.apply { setTextColor(Color.parseColor("#C9CFDA")) })
        return col
    }

    private fun permissionNeededView(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(48), dp(24), dp(48))
        }
        col.addView(TextView(this).apply {
            text = "Storage permission needed"
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            gravity = Gravity.CENTER
        })
        col.addView(TextView(this).apply {
            text = "Grant \"All files access\" so the app can find and manage your documents."
            setTextColor(Color.parseColor("#9CA3AF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(18))
        })
        col.addView(pillButton("Grant permission",
            withAlpha(accent, 0x33), accent) { requestAllFilesAccess() }
            .apply { setTextColor(Color.WHITE) })
        return col
    }

    private fun centered(title: String, subtitle: String): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(50), dp(20), dp(50))
            addView(TextView(this@MainActivity).apply {
                text = title
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                gravity = Gravity.CENTER
            })
            addView(TextView(this@MainActivity).apply {
                text = subtitle
                setTextColor(Color.parseColor("#888888"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, 0)
            })
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun glass(radiusDp: Int, fill: Int, stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(1), stroke)
        }

    private fun appGradient(): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.parseColor("#0C0C0F"),
                Color.parseColor("#141418"),
                Color.parseColor("#1B1B22")
            )
        )
}
