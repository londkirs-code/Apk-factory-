package com.londkirs.apkbridge

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The terminal's conversations (like chats in WhatsApp). Each chat keeps its messages under
 * "chat_<id>" in the "terminal" preferences; "chats" is the index (title, dates, size).
 * The single conversation of versions before 1.8 ("chat") becomes the first chat.
 */
object TerminalChats {
    class Chat(val id: Long, var title: String, var custom: Boolean, val created: Long, var updated: Long, var count: Int)

    private fun prefs(context: Context) = context.getSharedPreferences("terminal", Context.MODE_PRIVATE)

    /** All chats, the most recently used first. */
    fun list(context: Context): MutableList<Chat> {
        migrate(context)
        val arr = runCatching { JSONArray(prefs(context).getString("chats", "[]")) }.getOrDefault(JSONArray())
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map { o ->
            Chat(o.getLong("id"), o.optString("title"), o.optBoolean("custom"), o.optLong("created"), o.optLong("updated"), o.optInt("count"))
        }.sortedByDescending { it.updated }.toMutableList()
    }

    private fun write(context: Context, chats: List<Chat>) {
        val arr = JSONArray()
        chats.forEach {
            arr.put(JSONObject().put("id", it.id).put("title", it.title).put("custom", it.custom)
                .put("created", it.created).put("updated", it.updated).put("count", it.count))
        }
        prefs(context).edit().putString("chats", arr.toString()).apply()
    }

    private fun migrate(context: Context) {
        val p = prefs(context)
        if (p.contains("chats")) return
        val now = System.currentTimeMillis()
        val old = p.getString("chat", null)
        val chat = Chat(now, "", false, now, now, 0)
        val edit = p.edit()
        if (old != null) {
            edit.putString(key(chat.id), old).remove("chat")
            chat.count = runCatching { JSONArray(old).length() }.getOrDefault(0)
        }
        edit.putLong("chat_current", chat.id)
        edit.putString("chats", JSONArray().put(JSONObject().put("id", chat.id).put("title", "").put("custom", false)
            .put("created", now).put("updated", now).put("count", chat.count)).toString())
        edit.apply()
    }

    private fun key(id: Long) = "chat_$id"

    /** The open chat (created when there is none). */
    fun currentId(context: Context): Long {
        val chats = list(context)
        val id = prefs(context).getLong("chat_current", -1)
        return if (chats.any { it.id == id }) id else (chats.firstOrNull() ?: create(context)).id
    }

    fun setCurrent(context: Context, id: Long) = prefs(context).edit().putLong("chat_current", id).apply()

    fun messages(context: Context, id: Long): JSONArray? =
        prefs(context).getString(key(id), null)?.let { runCatching { JSONArray(it) }.getOrNull() }

    /** Saves the messages; the title follows the first command unless the user renamed the chat. */
    fun save(context: Context, id: Long, messages: JSONArray, firstCommand: String?) {
        prefs(context).edit().putString(key(id), messages.toString()).apply()
        val chats = list(context)
        val chat = chats.firstOrNull { it.id == id } ?: return
        if (!chat.custom) chat.title = firstCommand?.take(40).orEmpty()
        if (chat.count != messages.length()) chat.updated = System.currentTimeMillis()
        chat.count = messages.length()
        write(context, chats)
    }

    fun create(context: Context): Chat {
        val now = System.currentTimeMillis()
        val chats = list(context)
        val chat = Chat(maxOf(now, (chats.maxOfOrNull { it.id } ?: 0) + 1), "", false, now, now, 0)
        write(context, chats + chat)
        setCurrent(context, chat.id)
        return chat
    }

    fun rename(context: Context, id: Long, title: String) {
        val chats = list(context)
        chats.firstOrNull { it.id == id }?.let { it.title = title.take(60); it.custom = title.isNotBlank() }
        write(context, chats)
    }

    fun delete(context: Context, id: Long) {
        prefs(context).edit().remove(key(id)).apply()
        write(context, list(context).filter { it.id != id })
    }

    /** What the side panel shows for a chat without a title. */
    fun titleOf(chat: Chat) = chat.title.ifBlank { tr("שיחה חדשה", "New chat") }
}
