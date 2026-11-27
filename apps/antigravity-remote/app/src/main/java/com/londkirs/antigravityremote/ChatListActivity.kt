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
    private fun dp(v: Int) = ChatUi.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = ChatUi.HEADER_DARK
        val root = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(ChatUi.HEADER)
            setPadding(dp(16), 0, dp(4), 0)
        }
        header.addView(TextView(this).apply {
            text = "Antigravity"
            textSize = 21f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(this).apply {
            text = "⋮"; textSize = 24f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setOnClickListener { showMenu(it) }
        }, LinearLayout.LayoutParams(dp(48), dp(56)))
        column.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)))

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(column)

        val fab = TextView(this).apply {
            text = "+"; textSize = 30f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = ChatUi.rounded(ChatUi.SEND, dp(18).toFloat())
            elevation = dp(6).toFloat()
            contentDescription = getString(R.string.new_chat)
            setOnClickListener { openChat(ChatStore.create(this@ChatListActivity).id) }
        }
        root.addView(fab, FrameLayout.LayoutParams(dp(58), dp(58), Gravity.BOTTOM or Gravity.END).apply {
            setMargins(0, 0, dp(18), dp(22))
        })
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        list.removeAllViews()
        val chats = ChatStore.all(this)
        if (chats.isEmpty()) {
            list.addView(TextView(this).apply {
                setText(R.string.chat_empty)
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
                setPadding(dp(14), dp(10), dp(14), dp(10))
                isClickable = true
                setBackgroundResource(android.R.drawable.list_selector_background)
                setOnClickListener { openChat(c.id) }
                setOnLongClickListener { confirmDelete(c); true }
            }
            row.addView(ChatUi.avatar(this, "AG", 50))
            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), 0, 0, 0)
            }
            val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            top.addView(TextView(this).apply {
                text = c.title.ifBlank { getString(R.string.new_chat) }
                textSize = 17f; setTextColor(ChatUi.TEXT); setTypeface(typeface, Typeface.BOLD)
                setSingleLine(); ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            top.addView(TextView(this).apply {
                text = DateUtils.getRelativeTimeSpanString(c.updated, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
                textSize = 12f; setTextColor(ChatUi.MUTED); setPadding(dp(8), 0, 0, 0)
            })
            texts.addView(top)
            texts.addView(TextView(this).apply {
                text = c.preview.ifBlank { "…" }.replace('\n', ' ')
                textSize = 14f; setTextColor(ChatUi.MUTED)
                setSingleLine(); ellipsize = TextUtils.TruncateAt.END
            })
            row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            list.addView(row)
            list.addView(View(this).apply { setBackgroundColor(Color.parseColor("#E9EDEF")) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { leftMargin = dp(78) })
        }
    }

    private fun openChat(id: String) {
        startActivity(Intent(this, ChatActivity::class.java).putExtra(ChatActivity.EXTRA_ID, id))
    }

    private fun confirmDelete(c: Chat) {
        AlertDialog.Builder(this)
            .setMessage(R.string.chat_delete_confirm)
            .setPositiveButton(R.string.delete) { _, _ -> ChatStore.delete(this, c.id); render() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showMenu(anchor: View) {
        val m = PopupMenu(this, anchor)
        m.menu.add(0, 1, 0, R.string.visual_ui)
        m.menu.add(0, 2, 1, R.string.terminal)
        m.menu.add(0, 3, 2, R.string.settings_screen)
        m.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> HubClient.ensureRunning(this) { ok ->
                    if (ok) startActivity(Intent(this, WebActivity::class.java).putExtra(WebActivity.EXTRA_URL, TerminalService.HUB_URL))
                    else startActivity(Intent(this, TerminalActivity::class.java))
                }
                2 -> startActivity(Intent(this, TerminalActivity::class.java))
                3 -> startActivity(Intent(this, MainActivity::class.java))
            }
            true
        }
        m.show()
    }
}
