package com.londkirs.antigravityremote

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/** A chat in the list. [cascadeId] is the hub conversation id. */
data class Chat(val id: String, val cascadeId: String, var title: String, var preview: String, var updated: Long)

object ChatStore {
    private const val PREFS = "chats"
    private const val KEY = "list"

    fun all(ctx: Context): MutableList<Chat> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return mutableListOf()
        val out = mutableListOf<Chat>()
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out += Chat(o.getString("id"), o.getString("cascadeId"), o.optString("title"), o.optString("preview"), o.optLong("updated"))
            }
        }
        return out.sortedByDescending { it.updated }.toMutableList()
    }

    fun save(ctx: Context, chats: List<Chat>) {
        val arr = JSONArray()
        chats.forEach {
            arr.put(JSONObject().put("id", it.id).put("cascadeId", it.cascadeId).put("title", it.title)
                .put("preview", it.preview).put("updated", it.updated))
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    fun get(ctx: Context, id: String) = all(ctx).firstOrNull { it.id == id }

    fun create(ctx: Context): Chat {
        val c = Chat(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "", "", System.currentTimeMillis())
        save(ctx, all(ctx) + c)
        return c
    }

    fun update(ctx: Context, chat: Chat) {
        val list = all(ctx)
        val i = list.indexOfFirst { it.id == chat.id }
        if (i >= 0) list[i] = chat else list += chat
        save(ctx, list)
    }

    fun delete(ctx: Context, id: String) = save(ctx, all(ctx).filterNot { it.id == id })
}

/** One row on the chat screen. */
data class ChatItem(
    val kind: Int,
    val text: String,
    val time: String = "",
    val stepIndex: Int = -1,
    val interaction: JSONObject? = null,
    val step: JSONObject? = null,
) {
    companion object {
        const val USER = 0
        const val AGENT = 1
        const val TOOL = 2
        const val ERROR = 3
        const val APPROVAL = 4
        const val TYPING = 5
    }
}

/** Turns hub trajectory steps (proto3 JSON) into chat rows. */
object StepMapper {
    private val busy = setOf(
        "CORTEX_STEP_STATUS_PENDING", "CORTEX_STEP_STATUS_RUNNING",
        "CORTEX_STEP_STATUS_GENERATING", "CORTEX_STEP_STATUS_QUEUED"
    )
    private val hidden = listOf(
        "CHECKPOINT", "EPHEMERAL", "CONVERSATION_HISTORY", "MEMORY", "KNOWLEDGE", "BRAIN", "DUMMY",
        "PLAN_INPUT", "TASK_BOUNDARY", "SUGGESTED_RESPONSES", "SYSTEM", "MANAGER_FEEDBACK", "TRAJECTORY_CHOICE",
        "TOOL_CALL_CHOICE", "TOOL_CALL_PROPOSAL", "USER_ANNOTATIONS", "CODE_ACKNOWLEDGEMENT"
    )
    private val known = setOf("type", "status", "metadata", "subtrajectory", "requestedInteraction", "error", "userAnnotations")
    private val pathKeys = listOf(
        "command", "commandLine", "absolutePathUri", "absoluteUri", "targetFile", "filePath", "path",
        "directoryPath", "uri", "url", "query", "searchQuery", "pattern", "toolName", "serverName"
    )
    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    private val hhmm = SimpleDateFormat("HH:mm", Locale.getDefault())

    data class Result(val items: List<ChatItem>, val busy: Boolean, val lastUserText: String?, val lastAgentText: String?)

    fun map(steps: JSONArray): Result {
        val items = mutableListOf<ChatItem>()
        var isBusy = false
        var lastUser: String? = null
        var lastAgent: String? = null
        for (i in 0 until steps.length()) {
            val s = steps.optJSONObject(i) ?: continue
            val type = s.optString("type")
            val status = s.optString("status")
            if (status in busy) isBusy = true
            if (status == "CORTEX_STEP_STATUS_CLEARED" || status == "CORTEX_STEP_STATUS_INVALID") continue
            val time = time(s)

            val u = s.optJSONObject("userInput")
            if (u != null) {
                val text = userText(u)
                if (text.isNotBlank()) { items += ChatItem(ChatItem.USER, text, time, i); lastUser = text }
                continue
            }

            val p = s.optJSONObject("plannerResponse")
            if (p != null) {
                val text = p.optString("modifiedResponse").ifBlank { p.optString("response") }.trim()
                if (text.isNotEmpty()) { items += ChatItem(ChatItem.AGENT, text, time, i); lastAgent = text }
                addInteraction(items, s, i, status, time)
                continue
            }

            val n = s.optJSONObject("notifyUser")
            if (n != null) {
                val text = n.optString("notificationContent").trim()
                if (text.isNotEmpty()) { items += ChatItem(ChatItem.AGENT, text, time, i); lastAgent = text }
                continue
            }

            val err = s.optJSONObject("errorMessage")?.optJSONObject("error") ?: s.optJSONObject("error")
            if (err != null && (type.contains("ERROR") || status == "CORTEX_STEP_STATUS_ERROR")) {
                val msg = err.optString("userErrorMessage").ifBlank { err.optString("shortError") }.ifBlank { err.optString("modelErrorMessage") }
                if (msg.isNotBlank()) items += ChatItem(ChatItem.ERROR, msg, time, i, step = s)
                continue
            }

            if (hidden.any { type.contains(it) }) continue
            val label = toolLabel(s, type, status)
            if (label != null) items += ChatItem(ChatItem.TOOL, label, time, i, step = s)
            addInteraction(items, s, i, status, time)
        }
        return Result(items, isBusy, lastUser, lastAgent)
    }

    private fun addInteraction(items: MutableList<ChatItem>, s: JSONObject, i: Int, status: String, time: String) {
        val req = s.optJSONObject("requestedInteraction") ?: return
        if (status != "CORTEX_STEP_STATUS_WAITING") return
        items += ChatItem(ChatItem.APPROVAL, toolLabel(s, s.optString("type"), status) ?: "", time, i, interaction = req, step = s)
    }

    private fun userText(u: JSONObject): String {
        val items = u.optJSONArray("items")
        val sb = StringBuilder()
        if (items != null) for (k in 0 until items.length()) {
            val t = items.optJSONObject(k)?.optString("text").orEmpty()
            if (t.isNotBlank()) { if (sb.isNotEmpty()) sb.append('\n'); sb.append(t) }
        }
        return sb.toString().ifBlank { u.optString("query").ifBlank { u.optString("userResponse") } }.trim()
    }

    /** "Ran: ls -la", "View file: /x/y.kt" … built from the step's own object. */
    fun toolLabel(s: JSONObject, type: String, status: String): String? {
        val key = s.keys().asSequence().firstOrNull { it !in known && s.opt(it) is JSONObject && it != "plannerResponse" && it != "userInput" }
        val body = key?.let { s.optJSONObject(it) }
        val name = type.removePrefix("CORTEX_STEP_TYPE_").replace('_', ' ').lowercase().ifBlank { key ?: return null }
            .replaceFirstChar { it.uppercase() }
        var detail = ""
        if (body != null) {
            for (k in pathKeys) {
                val v = body.optString(k)
                if (v.isNotBlank()) { detail = v.removePrefix("file://"); break }
            }
            if (detail.isEmpty()) {
                body.optJSONArray("args")?.let { a -> detail = (0 until a.length()).joinToString(" ") { a.optString(it) } }
            }
        }
        val icon = when {
            type.contains("RUN_COMMAND") || type.contains("COMMAND") -> "⚙️"
            type.contains("WRITE") || type.contains("CODE_ACTION") || type.contains("FILE_CHANGE") || type.contains("PROPOSE") -> "✏️"
            type.contains("VIEW") || type.contains("READ") || type.contains("LIST") -> "📄"
            type.contains("SEARCH") || type.contains("GREP") || type.contains("FIND") -> "🔎"
            type.contains("BROWSER") || type.contains("URL") -> "🌐"
            type.contains("MCP") -> "🧩"
            else -> "•"
        }
        val tail = when (status) {
            "CORTEX_STEP_STATUS_RUNNING", "CORTEX_STEP_STATUS_GENERATING", "CORTEX_STEP_STATUS_PENDING" -> " …"
            "CORTEX_STEP_STATUS_ERROR" -> " ✗"
            "CORTEX_STEP_STATUS_CANCELED", "CORTEX_STEP_STATUS_INTERRUPTED" -> " ⊘"
            else -> ""
        }
        val exit = body?.takeIf { it.has("exitCode") }?.optInt("exitCode")?.takeIf { it != 0 }?.let { " (exit $it)" } ?: ""
        val shortDetail = if (detail.length > 160) detail.take(157) + "…" else detail
        return "$icon $name${if (shortDetail.isNotEmpty()) ": $shortDetail" else ""}$exit$tail"
    }

    /** Output of a command step (for tap-to-expand). */
    fun details(step: JSONObject?): String? {
        step ?: return null
        val rc = step.optJSONObject("runCommand") ?: return null
        val out = listOf("combinedOutput", "stdoutOutput").firstNotNullOfOrNull { k ->
            rc.optJSONObject(k)?.let { o -> o.optString("full").ifBlank { o.optString("text") }.ifBlank { null } }
        } ?: rc.optString("stdout").ifBlank { rc.optString("stdoutBuffer") } + rc.optString("stderr")
        return out.ifBlank { null }
    }

    /** Builds the HandleCascadeUserInteraction payload for an approval row. */
    fun interactionReply(item: ChatItem, allow: Boolean): JSONObject? {
        val req = item.interaction ?: return null
        val base = JSONObject().put("stepIndex", item.stepIndex).put("trajectoryId", "")
        val step = item.step
        return when {
            req.has("runCommand") -> {
                val cmd = step?.optJSONObject("runCommand")?.let { it.optString("commandLine").ifBlank { it.optString("command") } }.orEmpty()
                base.put("runCommand", JSONObject().put("confirm", allow).put("proposedCommandLine", cmd).put("submittedCommandLine", cmd))
            }
            req.has("filePermission") -> base.put("filePermission", JSONObject().put("allow", allow)
                .put("scope", "PERMISSION_SCOPE_CONVERSATION")
                .put("absolutePathUri", req.optJSONObject("filePermission")?.optString("absolutePathUri").orEmpty()))
            req.has("permission") -> base.put("permission", JSONObject().put("allow", allow).put("scope", "PERMISSION_SCOPE_CONVERSATION"))
            req.has("approvalInteraction") -> base.put("approvalInteraction", JSONObject().put("confirm", allow))
            else -> null
        }
    }

    private fun time(s: JSONObject): String {
        val raw = s.optJSONObject("metadata")?.optString("createdAt").orEmpty()
        if (raw.length < 19) return ""
        return runCatching { hhmm.format(iso.parse(raw.substring(0, 19)) ?: Date()) }.getOrDefault("")
    }
}
