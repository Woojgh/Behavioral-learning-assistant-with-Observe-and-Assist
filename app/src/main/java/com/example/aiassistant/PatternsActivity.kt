package com.example.aiassistant

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Shows all learned user-behaviour patterns with per-item delete and a Clear All option.
 */
class PatternsActivity : Activity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var listView: ListView
    private lateinit var statsText: TextView
    private var patterns = listOf<UserPatternEntity>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Title row with Clear All button
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 8)
        }
        titleRow.addView(TextView(this).apply {
            text = "Learned Patterns"
            textSize = 22f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        titleRow.addView(Button(this).apply {
            text = "Clear All"
            setOnClickListener { confirmClearAll() }
        })
        root.addView(titleRow)

        // Stats line
        statsText = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFF888888.toInt())
            setPadding(0, 0, 0, 12)
        }
        root.addView(statsText)

        // Pattern list
        listView = ListView(this)
        root.addView(
            listView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        setContentView(root)
        loadPatterns()
    }

    private fun loadPatterns() {
        scope.launch {
            patterns = withContext(Dispatchers.IO) {
                DatabaseHelper.getDB(this@PatternsActivity).userPatternDao().getAll()
            }
            refreshList()
        }
    }

    private fun confirmClearAll() {
        if (patterns.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Clear All Patterns")
            .setMessage("Delete all ${patterns.size} learned pattern(s)? This cannot be undone.")
            .setPositiveButton("Delete All") { _, _ -> clearAll() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearAll() {
        scope.launch {
            withContext(Dispatchers.IO) {
                DatabaseHelper.getDB(this@PatternsActivity).userPatternDao().deleteAll()
            }
            patterns = emptyList()
            refreshList()
        }
    }

    private fun deletePattern(pattern: UserPatternEntity) {
        AlertDialog.Builder(this)
            .setTitle("Delete Pattern")
            .setMessage("Remove \"${pattern.actionText}\" (seen ${pattern.count}×)?")
            .setPositiveButton("Delete") { _, _ ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        DatabaseHelper.getDB(this@PatternsActivity).userPatternDao().delete(pattern)
                    }
                    loadPatterns()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshList() {
        val appCount = patterns.map { it.packageName }.distinct().size
        statsText.text = if (patterns.isEmpty()) {
            "No patterns learned yet."
        } else {
            "${patterns.size} pattern(s) across $appCount app(s)"
        }

        if (patterns.isEmpty()) {
            listView.adapter = null
            return
        }

        val dateFmt = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

        listView.adapter = object : BaseAdapter() {
            override fun getCount() = patterns.size
            override fun getItem(pos: Int) = patterns[pos]
            override fun getItemId(pos: Int) = patterns[pos].id.toLong()

            override fun getView(pos: Int, convertView: View?, parent: ViewGroup?): View {
                val p = patterns[pos]

                val row = LinearLayout(this@PatternsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(8, 12, 8, 12)
                }

                // Info column
                val info = LinearLayout(this@PatternsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                    )
                }
                info.addView(TextView(this@PatternsActivity).apply {
                    text = "[${p.actionType}]  ${p.actionText}"
                    textSize = 15f
                })
                info.addView(TextView(this@PatternsActivity).apply {
                    text = "${p.packageName}  •  seen ${p.count}×  •  ${dateFmt.format(Date(p.lastSeen))}"
                    textSize = 11f
                    setTextColor(0xFF888888.toInt())
                    setPadding(0, 2, 0, 0)
                })

                row.addView(info)

                // Delete button
                row.addView(Button(this@PatternsActivity).apply {
                    text = "✕"
                    textSize = 13f
                    setPadding(16, 8, 16, 8)
                    setOnClickListener { deletePattern(p) }
                })

                return row
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
