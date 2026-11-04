package com.londkirs.ptaktsaf

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val prefsName = "floating_note"
    private val noteKey = "note_text"
    private val modeKey = "checklist_mode"

    private lateinit var preferences: android.content.SharedPreferences
    private lateinit var root: View
    private lateinit var freeText: EditText
    private lateinit var checklistScroll: ScrollView
    private lateinit var checklistContainer: LinearLayout
    private lateinit var checklistButton: TextView

    private var checklistMode = false
    private var rebuilding = false
    private val items = mutableListOf<NoteItem>()

    private data class NoteItem(var text: String, var checked: Boolean)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        setContentView(R.layout.activity_note)

        root = findViewById(R.id.root)
        root.setOnClickListener { finish() }

        val card = findViewById<View>(R.id.card)
        card.setOnClickListener { /* Consume taps inside the note card. */ }

        freeText = findViewById(R.id.noteText)
        checklistScroll = findViewById(R.id.checklistScroll)
        checklistContainer = findViewById(R.id.checklistContainer)
        checklistButton = findViewById(R.id.btnChecklist)
        preferences = getSharedPreferences(prefsName, Context.MODE_PRIVATE)

        findViewById<TextView>(R.id.btnClose).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnCopy).setOnClickListener { copyNote() }
        findViewById<TextView>(R.id.btnShare).setOnClickListener { shareNote() }
        checklistButton.setOnClickListener { toggleChecklist() }

        checklistMode = preferences.getBoolean(modeKey, false)
        val saved = preferences.getString(noteKey, "").orEmpty()
        if (checklistMode) {
            parseItems(saved)
            showChecklist()
        } else {
            freeText.setText(saved)
            showFreeText()
        }

        freeText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!rebuilding && !checklistMode) {
                    preferences.edit().putString(noteKey, s?.toString().orEmpty()).apply()
                }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    override fun onPause() {
        saveCurrentState()
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing) finish()
    }

    private fun saveCurrentState() {
        if (!::preferences.isInitialized) return
        val value = if (checklistMode) serializeItems() else freeText.text.toString()
        preferences.edit().putString(noteKey, value).putBoolean(modeKey, checklistMode).apply()
    }

    private fun toggleChecklist() {
        if (!checklistMode) {
            parseItems(freeText.text.toString())
            checklistMode = true
            preferences.edit().putBoolean(modeKey, true).putString(noteKey, serializeItems()).apply()
            showChecklist()
            focusChecklistRow(items.lastIndex)
        } else {
            val serialized = serializeItems()
            checklistMode = false
            preferences.edit().putBoolean(modeKey, false).putString(noteKey, serialized).apply()
            rebuilding = true
            freeText.setText(serialized)
            rebuilding = false
            showFreeText()
            freeText.requestFocus()
            freeText.setSelection(freeText.length())
        }
    }

    private fun showFreeText() {
        freeText.visibility = View.VISIBLE
        checklistScroll.visibility = View.GONE
        checklistButton.text = "☑"
        checklistButton.contentDescription = "מעבר לצ'קליסט"
    }

    private fun showChecklist() {
        freeText.visibility = View.GONE
        checklistScroll.visibility = View.VISIBLE
        checklistButton.text = "☰"
        checklistButton.contentDescription = "מעבר לטקסט חופשי"
        renderChecklist()
    }

    private fun parseItems(text: String) {
        items.clear()
        if (text.isEmpty()) {
            items.add(NoteItem("", false))
            return
        }
        text.split("\n").forEach { line ->
            when {
                line.startsWith("[x]", true) -> items.add(NoteItem(stripMarker(line), true))
                line.startsWith("[ ]") -> items.add(NoteItem(stripMarker(line), false))
                else -> items.add(NoteItem(line, false))
            }
        }
        if (items.isEmpty()) items.add(NoteItem("", false))
    }

    private fun stripMarker(line: String): String =
        if (line.length <= 3) "" else line.substring(3).removePrefix(" ")

    private fun serializeItems(): String =
        items.joinToString("\n") { (if (it.checked) "[x] " else "[ ] ") + it.text }

    private fun saveChecklist() {
        if (!rebuilding && checklistMode) {
            preferences.edit().putString(noteKey, serializeItems()).putBoolean(modeKey, true).apply()
        }
    }

    private fun renderChecklist() {
        rebuilding = true
        checklistContainer.removeAllViews()
        items.indices.forEach { addChecklistRow(it) }
        rebuilding = false
    }

    private fun addChecklistRow(index: Int) {
        val item = items[index]
        val row = layoutInflater.inflate(R.layout.item_check, checklistContainer, false)
        val checkBox = row.findViewById<CheckBox>(R.id.cb)
        val itemText = row.findViewById<EditText>(R.id.itemText)

        itemText.setText(item.text)
        checkBox.isChecked = item.checked
        applyCheckedStyle(itemText, item.checked)

        checkBox.setOnCheckedChangeListener { _, checked ->
            item.checked = checked
            applyCheckedStyle(itemText, checked)
            saveChecklist()
        }

        itemText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                item.text = s?.toString().orEmpty()
                saveChecklist()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        itemText.setOnEditorActionListener { _, actionId, event ->
            val enter = actionId == EditorInfo.IME_ACTION_NEXT ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event?.action == KeyEvent.ACTION_DOWN)
            if (enter) {
                val cursor = itemText.selectionStart.coerceAtLeast(0)
                val current = itemText.text.toString()
                val before = current.substring(0, cursor)
                val after = current.substring(cursor)
                item.text = before
                items.add(index + 1, NoteItem(after, false))
                saveChecklist()
                renderChecklist()
                focusChecklistRow(index + 1)
                true
            } else false
        }

        itemText.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN &&
                itemText.text.isEmpty()) {
                if (items.size > 1) {
                    items.removeAt(index)
                    saveChecklist()
                    renderChecklist()
                    focusChecklistRow((index - 1).coerceAtLeast(0))
                }
                true
            } else false
        }

        checklistContainer.addView(row)
    }

    private fun applyCheckedStyle(editText: EditText, checked: Boolean) {
        if (checked) {
            editText.paintFlags = editText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            editText.setTextColor(0xFF999999.toInt())
        } else {
            editText.paintFlags = editText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            editText.setTextColor(0xFF2B2615.toInt())
        }
    }

    private fun focusChecklistRow(index: Int) {
        if (index !in 0 until checklistContainer.childCount) return
        val row = checklistContainer.getChildAt(index)
        val edit = row.findViewById<EditText>(R.id.itemText)
        edit.requestFocus()
        edit.setSelection(edit.length())
    }

    private fun getNoteForTransfer(): String {
        if (!checklistMode) return freeText.text.toString()
        return items.joinToString("\n") { (if (it.checked) "☑ " else "☐ ") + it.text }
    }

    private fun copyNote() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("פתק", getNoteForTransfer()))
        Toast.makeText(this, "הועתק", Toast.LENGTH_SHORT).show()
    }

    private fun shareNote() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, getNoteForTransfer())
        }
        startActivity(Intent.createChooser(send, "שיתוף פתק"))
    }
}
