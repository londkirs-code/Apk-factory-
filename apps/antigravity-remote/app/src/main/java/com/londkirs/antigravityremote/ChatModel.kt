package com.londkirs.antigravityremote

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * A conversation in the list. [cascadeId] is the hub conversation id. Fields added in 4.0 are
 * optional in the stored JSON, so chats saved by older versions load unchanged.
 */
class Chat(
    val id: String,
    val cascadeId: String,
    var title: String,
    var preview: String,
    var updated: Long,
) {
    var pinned = 0L                 // pin time; 0 = not pinned
    var archived = false
    var muted = false
    var favorite = false
    var lists = mutableSetOf<String>()
    var draft = ""
    var unread = 0                  // agent replies not yet seen
    var markedUnread = false
    var emoji = ""
    var wallpaper = ""
    var agentCount = 0              // agent messages known
    var seenCount = 0               // agent messages the user has seen
    var busy = false
    var waiting = false             // agent waits for an approval
    var lastFromMe = false
    var replied = false             // last own message has an answer
    var workDir = ""

    fun name(ctx: Context) = title.ifBlank { ctx.tr("שיחה חדשה", "New chat") }
    val isUnread get() = unread > 0 || markedUnread

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("cascadeId", cascadeId).put("title", title).put("preview", preview).put("updated", updated)
        .put("pinned", pinned).put("archived", archived).put("muted", muted).put("favorite", favorite)
        .put("lists", JSONArray(lists.toList())).put("draft", draft).put("unread", unread).put("markedUnread", markedUnread)
        .put("emoji", emoji).put("wallpaper", wallpaper).put("agentCount", agentCount).put("seenCount", seenCount)
        .put("busy", busy).put("waiting", waiting).put("lastFromMe", lastFromMe).put("replied", replied).put("workDir", workDir)

    companion object {
        fun from(o: JSONObject) = Chat(o.getString("id"), o.getString("cascadeId"), o.optString("title"), o.optString("preview"), o.optLong("updated")).apply {
            pinned = o.optLong("pinned"); archived = o.optBoolean("archived"); muted = o.optBoolean("muted")
            favorite = o.optBoolean("favorite"); draft = o.optString("draft"); unread = o.optInt("unread")
            markedUnread = o.optBoolean("markedUnread"); emoji = o.optString("emoji"); wallpaper = o.optString("wallpaper")
            agentCount = o.optInt("agentCount"); seenCount = o.optInt("seenCount"); busy = o.optBoolean("busy")
            waiting = o.optBoolean("waiting"); lastFromMe = o.optBoolean("lastFromMe"); replied = o.optBoolean("replied")
            workDir = o.optString("workDir")
            o.optJSONArray("lists")?.let { a -> for (i in 0 until a.length()) lists += a.optString(i) }
        }
    }
}

object ChatStore {
    private const val PREFS = "chats"
    private const val KEY = "list"
    private val lock = Any()
    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(l: () -> Unit) { synchronized(listeners) { listeners += l } }
    fun removeListener(l: () -> Unit) { synchronized(listeners) { listeners -= l } }
    private fun notifyChanged() { synchronized(listeners) { listeners.toList() }.forEach { it() } }

    fun all(ctx: Context): MutableList<Chat> = synchronized(lock) {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return mutableListOf()
        val out = mutableListOf<Chat>()
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) runCatching { out += Chat.from(arr.getJSONObject(i)) }
        }
        out.sortedByDescending { it.updated }.toMutableList()
    }

    fun save(ctx: Context, chats: List<Chat>) {
        synchronized(lock) {
            val arr = JSONArray()
            chats.forEach { arr.put(it.toJson()) }
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).commit()
        }
        notifyChanged()
    }

    fun get(ctx: Context, id: String) = all(ctx).firstOrNull { it.id == id }

    fun create(ctx: Context): Chat {
        val c = Chat(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "", "", System.currentTimeMillis())
        save(ctx, all(ctx) + c)
        return c
    }

    fun update(ctx: Context, chat: Chat) = edit(ctx, chat.id) { copyInto(chat, it) }

    /** Read-modify-write of one chat, so concurrent updates (watcher + screen) do not clobber each other. */
    fun edit(ctx: Context, id: String, change: (Chat) -> Unit): Chat? {
        var result: Chat? = null
        var changed = false
        synchronized(lock) {
            val list = all(ctx)
            val c = list.firstOrNull { it.id == id } ?: return null
            val before = c.toJson().toString()
            change(c); result = c
            changed = c.toJson().toString() != before
            if (changed) {
                val arr = JSONArray(); list.forEach { arr.put(it.toJson()) }
                ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).commit()
            }
        }
        if (changed) notifyChanged()
        return result
    }

    fun editMany(ctx: Context, ids: Collection<String>, change: (Chat) -> Unit) {
        synchronized(lock) {
            val list = all(ctx)
            list.filter { it.id in ids }.forEach(change)
            val arr = JSONArray(); list.forEach { arr.put(it.toJson()) }
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).commit()
        }
        notifyChanged()
    }

    private fun copyInto(src: Chat, dst: Chat) {
        dst.title = src.title; dst.preview = src.preview; dst.updated = src.updated
        dst.pinned = src.pinned; dst.archived = src.archived; dst.muted = src.muted; dst.favorite = src.favorite
        dst.lists = src.lists.toMutableSet(); dst.draft = src.draft; dst.unread = src.unread; dst.markedUnread = src.markedUnread
        dst.emoji = src.emoji; dst.wallpaper = src.wallpaper; dst.agentCount = src.agentCount; dst.seenCount = src.seenCount
        dst.busy = src.busy; dst.waiting = src.waiting; dst.lastFromMe = src.lastFromMe; dst.replied = src.replied; dst.workDir = src.workDir
    }

    fun delete(ctx: Context, id: String) = deleteSelected(ctx, setOf(id))

    fun deleteSelected(ctx: Context, ids: Set<String>) {
        save(ctx, all(ctx).filterNot { it.id in ids })
        ids.forEach { ChatCache.delete(ctx, it); MsgMeta.delete(ctx, it) }
    }

    fun isStarted(ctx: Context, chat: Chat) =
        ctx.getSharedPreferences("chat_state", Context.MODE_PRIVATE).getBoolean("started_${chat.id}", false)

    fun setStarted(ctx: Context, chat: Chat) =
        ctx.getSharedPreferences("chat_state", Context.MODE_PRIVATE).edit().putBoolean("started_${chat.id}", true).apply()
}

/** User-made chat lists ("Development", "Tests"…), shown as chips above the chats. */
object ChatLists {
    data class ChatList(val id: String, val name: String)
    private fun p(ctx: Context) = ctx.getSharedPreferences("lists", Context.MODE_PRIVATE)

    fun all(ctx: Context): List<ChatList> = runCatching {
        val a = JSONArray(p(ctx).getString("lists", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> ChatList(o.getString("id"), o.getString("name")) } }
    }.getOrDefault(emptyList())

    fun save(ctx: Context, lists: List<ChatList>) {
        val a = JSONArray(); lists.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name)) }
        p(ctx).edit().putString("lists", a.toString()).apply()
    }

    fun add(ctx: Context, name: String): ChatList = ChatList(UUID.randomUUID().toString(), name).also { save(ctx, all(ctx) + it) }
    fun rename(ctx: Context, id: String, name: String) = save(ctx, all(ctx).map { if (it.id == id) it.copy(name = name) else it })
    fun delete(ctx: Context, id: String) {
        save(ctx, all(ctx).filterNot { it.id == id })
        ChatStore.editMany(ctx, ChatStore.all(ctx).filter { id in it.lists }.map { it.id }) { it.lists.remove(id) }
    }
}

/** Per-message extras that live only on this phone: stars, pins, "deleted for me", voice notes. */
class MsgMeta private constructor(private val ctx: Context, val chatId: String, o: JSONObject) {
    data class Star(val key: String, val text: String, val millis: Long, val kind: Int)

    val stars = LinkedHashMap<String, Star>()
    val pins = mutableListOf<String>()
    val hidden = mutableSetOf<String>()
    /** Transcript text → recorded audio file. */
    val voice = HashMap<String, String>()

    init {
        o.optJSONObject("stars")?.let { s ->
            s.keys().forEach { k -> s.optJSONObject(k)?.let { v -> stars[k] = Star(k, v.optString("t"), v.optLong("ms"), v.optInt("k")) } }
        }
        o.optJSONArray("pins")?.let { a -> for (i in 0 until a.length()) pins += a.optString(i) }
        o.optJSONArray("hidden")?.let { a -> for (i in 0 until a.length()) hidden += a.optString(i) }
        o.optJSONObject("voice")?.let { v -> v.keys().forEach { k -> voice[k] = v.optString(k) } }
    }

    fun save() {
        val s = JSONObject()
        stars.values.forEach { s.put(it.key, JSONObject().put("t", it.text).put("ms", it.millis).put("k", it.kind)) }
        val v = JSONObject(); voice.forEach { (k, p) -> v.put(k, p) }
        val o = JSONObject().put("stars", s).put("pins", JSONArray(pins)).put("hidden", JSONArray(hidden.toList())).put("voice", v)
        p(ctx).edit().putString(chatId, o.toString()).apply()
    }

    fun voiceFor(text: String): String? {
        voice[text]?.let { return it }
        return voice.entries.firstOrNull { text.endsWith(it.key) && it.key.isNotBlank() }?.value
    }

    companion object {
        private fun p(ctx: Context) = ctx.getSharedPreferences("msgmeta", Context.MODE_PRIVATE)
        fun load(ctx: Context, chatId: String) =
            MsgMeta(ctx.applicationContext, chatId, runCatching { JSONObject(p(ctx).getString(chatId, "{}")!!) }.getOrDefault(JSONObject()))
        fun delete(ctx: Context, chatId: String) = p(ctx).edit().remove(chatId).apply()
        fun allChats(ctx: Context): Set<String> = p(ctx).all.keys
    }
}

/** Last known conversation, for instant display, offline reading and searching inside messages. */
object ChatCache {
    private fun dir(ctx: Context) = File(ctx.filesDir, "chatcache").apply { mkdirs() }
    private fun file(ctx: Context, id: String) = File(dir(ctx), "$id.json")

    private val lastSaved = HashMap<String, Int>()

    fun save(ctx: Context, chatId: String, items: List<ChatItem>) {
        val a = JSONArray()
        items.filter { it.kind != ChatItem.TYPING }.forEach { a.put(it.toJson()) }
        val text = a.toString()
        synchronized(lastSaved) { if (lastSaved[chatId] == text.hashCode()) return; lastSaved[chatId] = text.hashCode() }
        runCatching {
            val tmp = File(dir(ctx), "$chatId.tmp")
            tmp.writeText(text); tmp.renameTo(file(ctx, chatId))
        }
    }

    fun load(ctx: Context, chatId: String): List<ChatItem> = runCatching {
        val a = JSONArray(file(ctx, chatId).readText())
        (0 until a.length()).map { ChatItem.from(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun delete(ctx: Context, chatId: String) { file(ctx, chatId).delete() }
}

/** A picture or video shown in the chat. [path] is a file on this phone. */
data class MediaRef(val path: String, val mime: String) {
    val isVideo get() = mime.startsWith("video/")
    val isImage get() = mime.startsWith("image/")
    val name get() = path.substringAfterLast('/')

    companion object {
        fun mimeOf(path: String): String? = when (path.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "webp" -> "image/webp"; "gif" -> "image/gif"
            "mp4", "m4v" -> "video/mp4"; "webm" -> "video/webm"; "mov" -> "video/quicktime"; "3gp" -> "video/3gpp"
            else -> null
        }
    }
}

/** One row on the chat screen. */
data class ChatItem(
    val kind: Int,
    val text: String,
    val time: String = "",
    val stepIndex: Int = -1,
    val interaction: JSONObject? = null,
    val step: JSONObject? = null,
    val millis: Long = 0L,
    val media: List<MediaRef> = emptyList(),
    val sub: Int = 0,
    val outgoing: Boolean = false,
) {
    /** Stable id of this row inside its conversation (used for stars, pins, replies). */
    val key get() = "$kind:$stepIndex:$sub"

    fun toJson(): JSONObject = JSONObject().put("k", kind).put("t", text).put("tm", time).put("s", stepIndex).put("ms", millis)
        .put("sub", sub).put("out", outgoing)
        .put("m", JSONArray().apply { media.forEach { put(JSONObject().put("p", it.path).put("t", it.mime)) } })

    companion object {
        const val USER = 0
        const val AGENT = 1
        const val TOOL = 2
        const val ERROR = 3
        const val APPROVAL = 4
        const val TYPING = 5
        const val MEDIA = 6

        fun from(o: JSONObject): ChatItem {
            val m = o.optJSONArray("m")
            val media = if (m == null) emptyList() else (0 until m.length()).mapNotNull { i ->
                m.optJSONObject(i)?.let { MediaRef(it.optString("p"), it.optString("t")) }
            }
            return ChatItem(o.optInt("k"), o.optString("t"), o.optString("tm"), o.optInt("s", -1), millis = o.optLong("ms"),
                media = media, sub = o.optInt("sub"), outgoing = o.optBoolean("out"))
        }
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
    /** Steps that only look at files: a picture they read is not something the agent "sent". */
    private val lookOnly = listOf("VIEW", "READ", "LIST", "SEARCH", "GREP", "FIND")
    private val known = setOf("type", "status", "metadata", "subtrajectory", "requestedInteraction", "error", "userAnnotations")
    private val pathKeys = listOf(
        "command", "commandLine", "absolutePathUri", "absoluteUri", "targetFile", "filePath", "path",
        "directoryPath", "uri", "url", "query", "searchQuery", "pattern", "toolName", "serverName"
    )
    private val mediaPath = Regex("""(?:file://)?(/[^\s"'<>()\[\]`]+\.(?:png|jpe?g|webp|gif|mp4|m4v|webm|mov|3gp))\b""", RegexOption.IGNORE_CASE)
    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    private val hhmm = SimpleDateFormat("HH:mm", Locale.getDefault())

    data class Result(
        val items: List<ChatItem>, val busy: Boolean, val lastUserText: String?, val lastAgentText: String?,
        val agentCount: Int = 0, val waiting: Boolean = false, val lastFromMe: Boolean = false, val busyLabel: String? = null,
    )

    /** [mediaDir] receives pictures that arrive inline (base64) so they can be shown and shared. */
    fun map(steps: JSONArray, mediaDir: File? = null): Result {
        val items = mutableListOf<ChatItem>()
        var isBusy = false
        var lastUser: String? = null
        var lastAgent: String? = null
        var busyLabel: String? = null
        val seen = HashSet<String>()
        for (i in 0 until steps.length()) {
            val s = steps.optJSONObject(i) ?: continue
            val type = s.optString("type")
            val status = s.optString("status")
            if (status in busy) isBusy = true
            if (status == "CORTEX_STEP_STATUS_CLEARED" || status == "CORTEX_STEP_STATUS_INVALID") continue
            val time = time(s)
            val ms = millis(s)

            val u = s.optJSONObject("userInput")
            if (u != null) {
                val text = userText(u)
                val media = mutableListOf<MediaRef>()
                u.optJSONArray("media")?.let { a -> for (k in 0 until a.length()) a.optJSONObject(k)?.let { inline(it, mediaDir)?.let { m -> media += m } } }
                findPaths(text, media, seen)
                if (text.isNotBlank() || media.isNotEmpty()) {
                    items += ChatItem(ChatItem.USER, text, time, i, millis = ms, media = media, outgoing = true)
                    lastUser = text.ifBlank { "📷" }
                }
                continue
            }

            val p = s.optJSONObject("plannerResponse")
            if (p != null) {
                val text = p.optString("modifiedResponse").ifBlank { p.optString("response") }.trim()
                if (text.isNotEmpty()) { items += ChatItem(ChatItem.AGENT, text, time, i, millis = ms); lastAgent = text }
                val media = mutableListOf<MediaRef>(); findPaths(text, media, seen)
                if (media.isNotEmpty()) items += ChatItem(ChatItem.MEDIA, "", time, i, millis = ms, media = media, sub = 1)
                addInteraction(items, s, i, status, time, ms)
                continue
            }

            val n = s.optJSONObject("notifyUser")
            if (n != null) {
                val text = n.optString("notificationContent").trim()
                if (text.isNotEmpty()) { items += ChatItem(ChatItem.AGENT, text, time, i, millis = ms); lastAgent = text }
                val media = mutableListOf<MediaRef>(); findPaths(text, media, seen)
                if (media.isNotEmpty()) items += ChatItem(ChatItem.MEDIA, "", time, i, millis = ms, media = media, sub = 1)
                continue
            }

            val err = s.optJSONObject("errorMessage")?.optJSONObject("error") ?: s.optJSONObject("error")
            if (err != null && (type.contains("ERROR") || status == "CORTEX_STEP_STATUS_ERROR")) {
                val msg = err.optString("userErrorMessage").ifBlank { err.optString("shortError") }.ifBlank { err.optString("modelErrorMessage") }
                if (msg.isNotBlank()) items += ChatItem(ChatItem.ERROR, msg, time, i, step = s, millis = ms)
                continue
            }

            if (hidden.any { type.contains(it) }) continue
            val label = toolLabel(s, type, status)
            if (label != null) {
                items += ChatItem(ChatItem.TOOL, label, time, i, step = s, millis = ms)
                if (status in busy) busyLabel = label
            }
            if (lookOnly.none { type.contains(it) }) {
                val media = mutableListOf<MediaRef>()
                scan(s, media, seen, mediaDir, 0)
                if (media.isNotEmpty()) items += ChatItem(ChatItem.MEDIA, "", time, i, millis = ms, media = media.take(8), sub = 1)
            }
            addInteraction(items, s, i, status, time, ms)
        }
        val agentCount = items.count { it.kind == ChatItem.AGENT || it.kind == ChatItem.MEDIA }
        val waiting = items.any { it.kind == ChatItem.APPROVAL }
        val lastMain = items.lastOrNull { it.kind == ChatItem.USER || it.kind == ChatItem.AGENT || it.kind == ChatItem.MEDIA }
        return Result(items, isBusy, lastUser, lastAgent, agentCount, waiting, lastMain?.kind == ChatItem.USER, busyLabel)
    }

    /** Looks for media file paths in free text (e.g. the agent's answer). */
    private fun findPaths(text: String, out: MutableList<MediaRef>, seen: MutableSet<String>) {
        for (m in mediaPath.findAll(text)) {
            val path = android.net.Uri.decode(m.groupValues[1])
            if (path in seen) continue
            val f = File(path)
            val mime = MediaRef.mimeOf(path) ?: continue
            if (f.isFile && f.length() > 0) { seen += path; out += MediaRef(path, mime) }
        }
    }

    /** Walks a step's JSON for inline media and file paths of pictures / videos. */
    private fun scan(v: Any?, out: MutableList<MediaRef>, seen: MutableSet<String>, mediaDir: File?, depth: Int) {
        if (depth > 6 || out.size >= 8) return
        when (v) {
            is JSONObject -> {
                inline(v, mediaDir)?.let { if (seen.add(it.path)) out += it; return }
                v.keys().forEach { k -> scan(v.opt(k), out, seen, mediaDir, depth + 1) }
            }
            is JSONArray -> for (i in 0 until minOf(v.length(), 50)) scan(v.opt(i), out, seen, mediaDir, depth + 1)
            is String -> if (v.length < 4000 && v.contains('/')) findPaths(v, out, seen)
        }
    }

    /** {mimeType, inlineData(base64)} or {mimeType, uri} → a file we can show. */
    private fun inline(o: JSONObject, mediaDir: File?): MediaRef? {
        val mime = o.optString("mimeType")
        if (!mime.startsWith("image/") && !mime.startsWith("video/")) return null
        val data = o.optString("inlineData").ifBlank { o.optString("data") }
        if (data.isNotBlank() && mediaDir != null) {
            val ext = mime.substringAfter('/').substringBefore(';').replace("jpeg", "jpg")
            val f = File(mediaDir.apply { mkdirs() }, "in_${data.hashCode().toUInt()}_${data.length}.$ext")
            if (!f.isFile) runCatching { f.writeBytes(android.util.Base64.decode(data, android.util.Base64.DEFAULT)) }
            if (f.isFile) return MediaRef(f.absolutePath, mime)
        }
        val uri = o.optString("uri").removePrefix("file://")
        if (uri.startsWith("/") && File(uri).isFile) return MediaRef(uri, mime)
        return null
    }

    private fun addInteraction(items: MutableList<ChatItem>, s: JSONObject, i: Int, status: String, time: String, ms: Long) {
        val req = s.optJSONObject("requestedInteraction") ?: return
        if (status != "CORTEX_STEP_STATUS_WAITING") return
        items += ChatItem(ChatItem.APPROVAL, toolLabel(s, s.optString("type"), status) ?: "", time, i, interaction = req, step = s, millis = ms, sub = 2)
    }

    private fun userText(u: JSONObject): String {
        val items = u.optJSONArray("items")
        val sb = StringBuilder()
        if (items != null) for (k in 0 until items.length()) {
            val it = items.optJSONObject(k) ?: continue
            val slash = it.optJSONObject("item")?.optJSONObject("slashCommand")?.optJSONObject("info")?.optString("name")
            val t = if (!slash.isNullOrBlank()) "/$slash" else it.optString("text")
            if (t.isNotBlank()) { if (sb.isNotEmpty()) sb.append(if (sb.endsWith("/$slash")) " " else "\n"); sb.append(t) }
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
        val tail = when (status) {
            "CORTEX_STEP_STATUS_RUNNING", "CORTEX_STEP_STATUS_GENERATING", "CORTEX_STEP_STATUS_PENDING" -> " …"
            "CORTEX_STEP_STATUS_ERROR" -> " ✗"
            "CORTEX_STEP_STATUS_CANCELED", "CORTEX_STEP_STATUS_INTERRUPTED" -> " ⊘"
            else -> ""
        }
        val exit = body?.takeIf { it.has("exitCode") }?.optInt("exitCode")?.takeIf { it != 0 }?.let { " (exit $it)" } ?: ""
        val shortDetail = if (detail.length > 160) detail.take(157) + "…" else detail
        return "$name${if (shortDetail.isNotEmpty()) ": $shortDetail" else ""}$exit$tail"
    }

    /** Icon for a tool row, from the step type. */
    fun toolIcon(step: JSONObject?): Int {
        val type = step?.optString("type").orEmpty()
        return when {
            type.contains("COMMAND") -> R.drawable.ic_terminal
            type.contains("WRITE") || type.contains("CODE_ACTION") || type.contains("FILE_CHANGE") || type.contains("PROPOSE") || type.contains("EDIT") -> R.drawable.ic_edit
            type.contains("VIEW") || type.contains("READ") || type.contains("LIST") -> R.drawable.ic_file
            type.contains("SEARCH") || type.contains("GREP") || type.contains("FIND") -> R.drawable.ic_search
            type.contains("BROWSER") || type.contains("URL") -> R.drawable.ic_globe
            type.contains("MCP") -> R.drawable.ic_bolt
            else -> R.drawable.ic_sparkle
        }
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

    private fun millis(s: JSONObject): Long {
        val raw = s.optJSONObject("metadata")?.optString("createdAt").orEmpty()
        if (raw.length < 19) return 0L
        return runCatching { synchronized(iso) { iso.parse(raw.substring(0, 19))?.time ?: 0L } }.getOrDefault(0L)
    }

    private fun time(s: JSONObject): String {
        val ms = millis(s)
        if (ms == 0L) return ""
        return synchronized(hhmm) { hhmm.format(Date(ms)) }
    }
}
