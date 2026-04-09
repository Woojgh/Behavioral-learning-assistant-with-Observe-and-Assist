package com.example.aiassistant

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class LogActivity : Activity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var listView: ListView
    private var logs = listOf<LogEntity>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Title row
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 16)
        }

        titleRow.addView(TextView(this).apply {
            text = "Action History"
            textSize = 22f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })

        titleRow.addView(Button(this).apply {
            text = "Clear All"
            setOnClickListener { clearLogs() }
        })

        root.addView(titleRow)

        // List
        listView = ListView(this)
        root.addView(listView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        setContentView(root)
        loadLogs()
    }

    private fun loadLogs() {
        scope.launch {
            logs = withContext(Dispatchers.IO) {
                DatabaseHelper.getDB(this@LogActivity).logDao().getAll()
            }
            refreshList()
        }
    }

    private fun clearLogs() {
        scope.launch {
            withContext(Dispatchers.IO) {
                DatabaseHelper.getDB(this@LogActivity).logDao().clearAll()
            }
            logs = emptyList()
            refreshList()
        }
    }

    private fun refreshList() {
        if (logs.isEmpty()) {
            listView.adapter = null
            return
        }

        val dateFormat = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())

        listView.adapter = object : BaseAdapter() {
            override fun getCount() = logs.size
            override fun getItem(pos: Int) = logs[pos]
            override fun getItemId(pos: Int) = logs[pos].id.toLong()

            override fun getView(pos: Int, convertView: View?, parent: ViewGroup?): View {
                val log = logs[pos]
                val layout = LinearLayout(this@LogActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(8, 12, 8, 12)
                }

                val status = if (log.success) "✓" else "✗"
                val time = dateFormat.format(Date(log.timestamp))

                layout.addView(TextView(this@LogActivity).apply {
                    text = "$status  ${log.actionType}: ${log.actionDetail}"
                    textSize = 15f
                })

                layout.addView(TextView(this@LogActivity).apply {
                    text = "$time  |  ${log.packageName}"
                    textSize = 12f
                    setTextColor(0xFF888888.toInt())
                })

                return layout
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
