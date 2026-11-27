package com.londkirs.antigravityremote

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView

/** WhatsApp-style list of Antigravity conversations running on this phone. */
class ChatListActivity : Activity() {

    private lateinit var list: LinearLayout
    private lateinit var searchField: android.widget.EditText
    private var filter = ""
    private fun dp(v: Int) = ChatUi.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(ChatUi.SURFACE)
            setPaddingRelative(dp(20), 0, dp(6), 0)
        }
        header.addView(TextView(this).apply {
            text = "Antigravity"
            textSize = 30f
            setTextColor(ChatUi.SEND)
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(ChatUi.icon(this, R.drawable.ic_chat_more, R.string.more).apply {
            setOnClickListener { showMenu(it) }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        column.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)))
        searchField = android.widget.EditText(this).apply {
            setHint(R.string.chat_search_chats); setSingleLine(); textSize = 16f
            background = ChatUi.rounded(ChatUi.FIELD, dp(24).toFloat())
            setPadding(dp(16), dp(8), dp(16), dp(8))
            minimumHeight = dp(48)
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_chat_search, 0, 0, 0)
            compoundDrawableTintList = android.content.res.ColorStateList.valueOf(ChatUi.MUTED)
            compoundDrawablePadding = dp(10)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(e: android.text.Editable?) { filter = e?.toString().orEmpty().trim(); render() }
            })
        }
        column.addView(searchField, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(16), dp(8), dp(16), dp(12))
        })

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(ScrollView(this).apply { addView(list); clipToPadding = false; setPadding(0, 0, 0, dp(84)) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(column)

        val fab = ChatUi.icon(this, R.drawable.ic_chat_compose, R.string.new_chat, Color.WHITE).apply {
            background = ChatUi.rounded(ChatUi.SEND, dp(18).toFloat())
            elevation = dp(6).toFloat()
            contentDescription = getString(R.string.new_chat)
            setOnClickListener { openChat(ChatStore.create(this@ChatListActivity).id) }
        }
        root.addView(fab, FrameLayout.LayoutParams(dp(58), dp(58), Gravity.BOTTOM or Gravity.END).apply {
            marginEnd = dp(18); bottomMargin = dp(20)
        })
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        list.removeAllViews()
        val chats = ChatStore.all(this).filter {
            filter.isEmpty() || it.title.contains(filter, true) || it.preview.contains(filter, true)
        }
        if (chats.isEmpty()) {
            list.addView(TextView(this).apply {
                setText(if (filter.isEmpty()) R.string.chat_empty else R.string.chat_no_matches)
                textSize = 15f
                setTextColor(ChatUi.MUTED)
                gravity = Gravity.CENTER
                setPadding(dp(32), dp(80), dp(32), dp(32))
            })
            return
        }
        chats.forEach { c ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(13), dp(16), dp(13))
                isClickable = true
                setBackgroundResource(android.R.drawable.list_selector_background)
                setOnClickListener { openChat(c.id) }
                setOnLongClickListener { chatMenu(c); true }
            }
            row.addView(ChatUi.avatarImage(this, 50))
            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPaddingRelative(dp(14), 0, 0, 0)
            }
            val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            top.addView(TextView(this).apply {
                text = c.title.ifBlank { getString(R.string.new_chat) }
                textSize = 17f; setTextColor(ChatUi.TEXT); setTypeface(typeface, Typeface.BOLD)
                setSingleLine(); ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            top.addView(TextView(this).apply {
                text = DateUtils.getRelativeTimeSpanString(c.updated, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
                textSize = 12f; setTextColor(ChatUi.MUTED); setPaddingRelative(dp(8), 0, 0, 0)
            })
            texts.addView(top, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            texts.addView(TextView(this).apply {
                text = c.preview.ifBlank { "…" }.replace('\n', ' ')
                textSize = 14f; setTextColor(ChatUi.MUTED)
                setSingleLine(); ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            list.addView(row)
            list.addView(View(this).apply { setBackgroundColor(Color.parseColor("#E9EDEF")) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { marginStart = dp(78) })
        }
    }

    private fun openChat(id: String) {
        startActivity(Intent(this, ChatActivity::class.java).putExtra(ChatActivity.EXTRA_ID, id))
    }

    private fun chatMenu(c: Chat) {
        val items = arrayOf(getString(R.string.chat_rename), getString(R.string.delete))
        AlertDialog.Builder(this).setTitle(c.title.ifBlank { getString(R.string.new_chat) })
            .setItems(items) { _, which -> if (which == 0) rename(c) else confirmDelete(c) }
            .show()
    }

    private fun rename(c: Chat) {
        val field = android.widget.EditText(this).apply { setText(c.title); setSingleLine(); setSelection(text.length) }
        AlertDialog.Builder(this).setTitle(R.string.chat_rename).setView(field)
            .setPositiveButton(R.string.save) { _, _ ->
                c.title = field.text.toString().trim(); ChatStore.update(this, c); render()
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun confirmDelete(c: Chat) {
        AlertDialog.Builder(this)
            .setMessage(R.string.chat_delete_confirm)
            .setPositiveButton(R.string.delete) { _, _ -> ChatStore.delete(this, c.id); render() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun selectChats() {
        val chats = ChatStore.all(this)
        if (chats.isEmpty()) return
        val checked = BooleanArray(chats.size)
        val titles = chats.map { it.title.ifBlank { it.preview.ifBlank { getString(R.string.new_chat) } } }
        fun updateButtons(dialog: AlertDialog) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = checked.any { it }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                .setText(if (checked.all { it }) R.string.clear_selection else R.string.select_all_chats)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.select_chats)
            .setMultiChoiceItems(titles.toTypedArray(), checked) { activeDialog, index, selected ->
                checked[index] = selected
                updateButtons(activeDialog as AlertDialog)
            }
            .setNeutralButton(R.string.select_all_chats, null)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete, null)
            .create()
        dialog.setOnShowListener {
            val delete = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val toggle = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            toggle.setOnClickListener {
                val select = !checked.all { it }
                checked.fill(select)
                checked.indices.forEach { dialog.listView.setItemChecked(it, select) }
                updateButtons(dialog)
            }
            delete.setOnClickListener {
                val ids = chats.filterIndexed { i, _ -> checked[i] }.map { it.id }.toSet()
                AlertDialog.Builder(this)
                    .setMessage(getString(R.string.confirm_delete_chats, ids.size))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        ChatStore.deleteSelected(this, ids)
                        dialog.dismiss()
                        render()
                    }.show()
            }
            updateButtons(dialog)
        }
        dialog.show()
    }

    private fun showMenu(anchor: View) {
        val m = PopupMenu(this, anchor)
        m.menu.add(0, 1, 0, R.string.visual_ui)
        m.menu.add(0, 2, 1, R.string.terminal)
        m.menu.add(0, 3, 2, R.string.settings_screen)
        m.menu.add(0, 4, 3, R.string.select_chats).isEnabled = ChatStore.all(this).isNotEmpty()
        m.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> HubClient.ensureRunning(this) { ok ->
                    if (ok) startActivity(Intent(this, WebActivity::class.java).putExtra(WebActivity.EXTRA_URL, TerminalService.hubUiUrl ?: TerminalService.HUB_URL))
                    else startActivity(Intent(this, TerminalActivity::class.java))
                }
                2 -> startActivity(Intent(this, TerminalActivity::class.java))
                3 -> startActivity(Intent(this, MainActivity::class.java))
                4 -> selectChats()
            }
            true
        }
        m.show()
    }
}
