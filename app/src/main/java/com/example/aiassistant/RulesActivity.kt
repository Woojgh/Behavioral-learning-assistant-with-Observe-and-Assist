package com.example.aiassistant

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import kotlinx.coroutines.*

class RulesActivity : Activity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var listView: ListView
    private var rules = mutableListOf<RuleEntity>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Title
        root.addView(TextView(this).apply {
            text = "Manage Rules"
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        })

        // Add rule form
        val formRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }

        val keywordInput = EditText(this).apply {
            hint = "Keyword"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val actionSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@RulesActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("CLICK", "SCROLL_FORWARD", "SCROLL_BACKWARD", "SWIPE", "TYPE")
            )
        }

        val addButton = Button(this).apply {
            text = "Add"
            setOnClickListener {
                val keyword = keywordInput.text.toString().trim()
                if (keyword.isNotEmpty()) {
                    val actionType = actionSpinner.selectedItem.toString()
                    addRule(keyword, actionType)
                    keywordInput.text.clear()
                }
            }
        }

        formRow.addView(keywordInput)
        formRow.addView(actionSpinner)
        formRow.addView(addButton)
        root.addView(formRow)

        // Rules list
        listView = ListView(this)
        root.addView(listView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        setContentView(root)
        loadRules()
    }

    private fun loadRules() {
        scope.launch {
            val db = DatabaseHelper.getDB(this@RulesActivity)
            rules = withContext(Dispatchers.IO) { db.ruleDao().getAll().toMutableList() }
            refreshList()
        }
    }

    private fun addRule(keyword: String, actionType: String) {
        scope.launch {
            val rule = RuleEntity(keyword = keyword, actionType = actionType)
            withContext(Dispatchers.IO) {
                DatabaseHelper.getDB(this@RulesActivity).ruleDao().insert(rule)
            }
            loadRules()
        }
    }

    private fun deleteRule(rule: RuleEntity) {
        scope.launch {
            withContext(Dispatchers.IO) {
                DatabaseHelper.getDB(this@RulesActivity).ruleDao().delete(rule)
            }
            loadRules()
        }
    }

    private fun refreshList() {
        listView.adapter = object : BaseAdapter() {
            override fun getCount() = rules.size
            override fun getItem(pos: Int) = rules[pos]
            override fun getItemId(pos: Int) = rules[pos].id.toLong()

            override fun getView(pos: Int, convertView: View?, parent: ViewGroup?): View {
                val rule = rules[pos]
                val row = LinearLayout(this@RulesActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(8, 12, 8, 12)
                    gravity = Gravity.CENTER_VERTICAL
                }

                row.addView(TextView(this@RulesActivity).apply {
                    text = "${rule.keyword}  →  ${rule.actionType}"
                    textSize = 16f
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })

                row.addView(Button(this@RulesActivity).apply {
                    text = "X"
                    setOnClickListener { deleteRule(rule) }
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
