package com.londkirs.antigravityremote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Talking to the agent for one chat: used by the chat screen, the watcher and notification replies. */
object ChatEngine {
    @Volatile var models: List<HubClient.ModelChoice> = emptyList()

    fun loadModels(force: Boolean = false): List<HubClient.ModelChoice> {
        if (models.isEmpty() || force) models = runCatching { HubClient.models() }.getOrDefault(models)
        return models
    }

    /** The user's pick if still offered, else the account default (first in the list). */
    fun currentModel(ctx: Context): HubClient.ModelChoice? {
        val saved = Settings.model(ctx)
        return models.firstOrNull { it.value.toString() == saved } ?: models.firstOrNull()
    }

    fun mediaDir(ctx: Context) = File(ctx.filesDir, "media").apply { mkdirs() }

    fun defaultWorkDir(ctx: Context) = File(AgyInstaller.homeDir(ctx), "projects").absolutePath

    fun workDir(ctx: Context, chat: Chat) = chat.workDir.ifBlank { defaultWorkDir(ctx) }

    /** Folder for files the user attaches (videos, documents), inside the agent's workspace. */
    fun inbox(ctx: Context) = File(defaultWorkDir(ctx), "inbox").apply { mkdirs() }

    /** Blocking send. [images] are (mime, base64) pairs. */
    fun send(ctx: Context, chat: Chat, text: String, slash: JSONObject? = null, images: List<Pair<String, String>> = emptyList()) {
        loadModels()
        val model = currentModel(ctx)?.value
        val policy = Settings.autoPolicy(ctx)
        if (!ChatStore.isStarted(ctx, chat)) {
            File(workDir(ctx, chat)).mkdirs()
            HubClient.startCascade(chat.cascadeId, workDir(ctx, chat), model)
            ChatStore.setStarted(ctx, chat)
        }
        val items = JSONArray()
        if (slash != null) items.put(JSONObject().put("item", JSONObject().put("slashCommand", JSONObject().put("info", slash))))
        if (text.isNotEmpty()) items.put(JSONObject().put("text", text))
        var media: JSONArray? = null
        if (images.isNotEmpty()) {
            media = JSONArray()
            images.forEach { (mime, b64) ->
                val uri = runCatching { HubClient.saveMedia(mime, b64) }.getOrDefault("")
                media.put(JSONObject().put("mimeType", mime).put("inlineData", b64).apply { if (uri.isNotBlank()) put("uri", uri) })
            }
        }
        HubClient.sendMessage(chat.cascadeId, items, UUID.randomUUID().toString(), model, policy, media)
        ChatStore.edit(ctx, chat.id) { it.busy = true; it.lastFromMe = true; it.replied = false; it.updated = System.currentTimeMillis() }
        ChatWatcher.poke()
    }

    fun fetch(ctx: Context, chat: Chat): StepMapper.Result = StepMapper.map(HubClient.steps(chat.cascadeId), mediaDir(ctx))

    fun previewOf(ctx: Context, r: StepMapper.Result): String? {
        val last = r.items.lastOrNull { it.kind == ChatItem.USER || it.kind == ChatItem.AGENT || it.kind == ChatItem.MEDIA } ?: return null
        return when {
            last.text.isNotBlank() -> last.text
            last.media.any { it.isVideo } -> "🎬 " + ctx.tr("סרטון", "Video")
            last.media.isNotEmpty() -> "📷 " + ctx.tr("תמונה", "Photo")
            else -> null
        }
    }

    /**
     * Stores what a fetch learned about the chat (counts, unread, busy, preview). Returns the new
     * agent rows since the last known count, for notifications.
     */
    fun apply(ctx: Context, chatId: String, r: StepMapper.Result, viewing: Boolean): List<ChatItem> {
        var fresh = emptyList<ChatItem>()
        ChatStore.edit(ctx, chatId) { c ->
            val agentRows = r.items.filter { it.kind == ChatItem.AGENT || it.kind == ChatItem.MEDIA }
            if (r.agentCount > c.agentCount) {
                fresh = agentRows.drop(c.agentCount)
                c.updated = System.currentTimeMillis()
            }
            if (c.seenCount > r.agentCount) c.seenCount = r.agentCount
            c.agentCount = r.agentCount
            if (viewing) { c.seenCount = r.agentCount; c.markedUnread = false }
            c.unread = maxOf(0, c.agentCount - c.seenCount)
            c.busy = r.busy
            c.waiting = r.waiting
            c.lastFromMe = r.lastFromMe
            c.replied = !r.lastFromMe || r.items.lastOrNull { it.kind == ChatItem.USER } == null
            previewOf(ctx, r)?.let { c.preview = it.take(300) }
        }
        ChatCache.save(ctx, chatId, r.items)
        return fresh
    }
}

/** Keeps the chat list fresh and raises notifications while agents work in the background. */
object ChatWatcher {
    @Volatile var openChatId: String? = null
    @Volatile private var thread: Thread? = null
    private val lock = Object()

    fun start(ctx: Context) {
        if (thread?.isAlive == true) return
        val app = ctx.applicationContext
        thread = Thread({ loop(app) }, "chat-watcher").apply { isDaemon = true; start() }
    }

    fun poke() { synchronized(lock) { lock.notifyAll() } }

    private fun loop(ctx: Context) {
        HubClient.attach(ctx)
        while (true) {
            var anyActive = false
            try {
                if (HubClient.isAlive()) {
                    val now = System.currentTimeMillis()
                    for (c in ChatStore.all(ctx)) {
                        if (c.id == openChatId || !ChatStore.isStarted(ctx, c)) continue
                        val active = c.busy || c.waiting || (c.lastFromMe && !c.replied) || now - c.updated < 5 * 60_000
                        if (!active) continue
                        anyActive = anyActive || c.busy || (c.lastFromMe && !c.replied)
                        val r = runCatching { ChatEngine.fetch(ctx, c) }.getOrNull() ?: continue
                        val fresh = ChatEngine.apply(ctx, c.id, r, viewing = false)
                        val chat = ChatStore.get(ctx, c.id) ?: continue
                        if ((fresh.isNotEmpty() && !r.busy) || (r.waiting && !c.waiting)) Notifier.notify(ctx, chat, r)
                    }
                }
            } catch (_: Throwable) {}
            synchronized(lock) { runCatching { lock.wait(if (anyActive) 2500L else 8000L) } }
        }
    }
}

/** Message notifications, grouped per chat, with reply and approve / deny actions. */
object Notifier {
    private const val CHANNEL = "messages"
    const val ACTION_REPLY = "com.londkirs.antigravityremote.REPLY"
    const val ACTION_ALLOW = "com.londkirs.antigravityremote.ALLOW"
    const val ACTION_DENY = "com.londkirs.antigravityremote.DENY"
    const val ACTION_READ = "com.londkirs.antigravityremote.READ"
    const val EXTRA_CHAT = "chat_id"
    const val KEY_REPLY = "reply"

    private fun nm(ctx: Context) = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    fun idFor(chatId: String) = 1000 + Math.floorMod(chatId.hashCode(), 100000)

    private fun channel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26 && nm(ctx).getNotificationChannel(CHANNEL) == null) {
            nm(ctx).createNotificationChannel(NotificationChannel(CHANNEL, ctx.tr("הודעות מהסוכן", "Agent messages"), NotificationManager.IMPORTANCE_HIGH))
        }
    }

    private fun flags(mutable: Boolean = false) = PendingIntent.FLAG_UPDATE_CURRENT or
        (if (Build.VERSION.SDK_INT >= 31 && mutable) PendingIntent.FLAG_MUTABLE else if (Build.VERSION.SDK_INT >= 23 && !mutable) PendingIntent.FLAG_IMMUTABLE else 0)

    private fun action(ctx: Context, action: String, chat: Chat, req: Int, mutable: Boolean = false): PendingIntent =
        PendingIntent.getBroadcast(ctx, idFor(chat.id) * 10 + req,
            Intent(ctx, NotifReceiver::class.java).setAction(action).putExtra(EXTRA_CHAT, chat.id), flags(mutable))

    fun notify(ctx: Context, chat: Chat, r: StepMapper.Result) {
        if (!Settings.notifications(ctx) || chat.muted) return
        channel(ctx)
        val open = PendingIntent.getActivity(ctx, idFor(chat.id),
            Intent(ctx, ChatActivity::class.java).putExtra(ChatActivity.EXTRA_ID, chat.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags())
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(ctx)
        val me = ctx.tr("אני", "Me")
        @Suppress("DEPRECATION")
        val style = Notification.MessagingStyle(me).setConversationTitle(chat.name(ctx))
        val recent = r.items.filter { it.kind == ChatItem.AGENT || it.kind == ChatItem.USER || it.kind == ChatItem.MEDIA || it.kind == ChatItem.APPROVAL }.takeLast(5)
        recent.forEach {
            val text = when (it.kind) {
                ChatItem.APPROVAL -> "⚠️ " + ctx.tr("מחכה לאישור: ", "Needs approval: ") + it.text
                ChatItem.MEDIA -> "📷 " + ctx.tr("תמונה", "Photo")
                else -> it.text.take(500)
            }
            @Suppress("DEPRECATION")
            style.addMessage(text, if (it.millis > 0) it.millis else System.currentTimeMillis(), if (it.kind == ChatItem.USER) null else "Antigravity")
        }
        b.setSmallIcon(R.drawable.ic_stat_agy)
            .setStyle(style)
            .setContentTitle(chat.name(ctx))
            .setContentText(chat.preview)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setColor(0xFF1F6FEB.toInt())
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setLargeIcon(BitmapFactory.decodeResource(ctx.resources, R.drawable.agy_avatar))
        if (Build.VERSION.SDK_INT < 26) @Suppress("DEPRECATION") b.setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL)
        if (r.waiting) {
            b.addAction(Notification.Action.Builder(null, ctx.tr("אישור", "Allow"), action(ctx, ACTION_ALLOW, chat, 2)).build())
            b.addAction(Notification.Action.Builder(null, ctx.tr("דחייה", "Deny"), action(ctx, ACTION_DENY, chat, 3)).build())
        }
        val input = RemoteInput.Builder(KEY_REPLY).setLabel(ctx.tr("תשובה…", "Reply…")).build()
        b.addAction(Notification.Action.Builder(null, ctx.tr("תשובה", "Reply"), action(ctx, ACTION_REPLY, chat, 1, mutable = true))
            .addRemoteInput(input).build())
        if (!r.waiting) b.addAction(Notification.Action.Builder(null, ctx.tr("סמן כנקרא", "Mark as read"), action(ctx, ACTION_READ, chat, 4)).build())
        runCatching { nm(ctx).notify(idFor(chat.id), b.build()) }
    }

    fun status(ctx: Context, chat: Chat, text: String) {
        channel(ctx)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(ctx)
        b.setSmallIcon(R.drawable.ic_stat_agy).setContentTitle(chat.name(ctx)).setContentText(text).setTimeoutAfterCompat(4000)
            .setColor(0xFF1F6FEB.toInt()).setOnlyAlertOnce(true)
        runCatching { nm(ctx).notify(idFor(chat.id), b.build()) }
    }

    private fun Notification.Builder.setTimeoutAfterCompat(ms: Long) = apply { if (Build.VERSION.SDK_INT >= 26) setTimeoutAfter(ms) }

    fun cancel(ctx: Context, chatId: String) = runCatching { nm(ctx).cancel(idFor(chatId)) }
}

/** Handles reply / approve / deny / read from a notification without opening the app. */
class NotifReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        val chatId = intent.getStringExtra(Notifier.EXTRA_CHAT) ?: return
        val pending = goAsync()
        val main = Handler(Looper.getMainLooper())
        Thread {
            try {
                HubClient.attach(ctx)
                val chat = ChatStore.get(ctx, chatId) ?: return@Thread
                when (intent.action) {
                    Notifier.ACTION_REPLY -> {
                        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifier.KEY_REPLY)?.toString()?.trim()
                        if (!text.isNullOrEmpty()) {
                            val ok = runCatching { ChatEngine.send(ctx, chat, text) }.isSuccess
                            Notifier.status(ctx, chat, if (ok) ctx.tr("נשלח: ", "Sent: ") + text else ctx.tr("השליחה נכשלה. פתחו את השיחה.", "Sending failed. Open the chat."))
                        }
                    }
                    Notifier.ACTION_ALLOW, Notifier.ACTION_DENY -> {
                        val allow = intent.action == Notifier.ACTION_ALLOW
                        val r = ChatEngine.fetch(ctx, chat)
                        val item = r.items.lastOrNull { it.kind == ChatItem.APPROVAL }
                        val reply = item?.let { StepMapper.interactionReply(it, allow) }
                        if (reply != null) {
                            runCatching { HubClient.respond(chat.cascadeId, reply) }
                            Notifier.status(ctx, chat, if (allow) ctx.tr("אושר", "Allowed") else ctx.tr("נדחה", "Denied"))
                        } else Notifier.cancel(ctx, chat.id)
                        ChatWatcher.poke()
                    }
                    Notifier.ACTION_READ -> {
                        ChatStore.edit(ctx, chat.id) { it.seenCount = it.agentCount; it.unread = 0; it.markedUnread = false }
                        Notifier.cancel(ctx, chat.id)
                    }
                }
            } finally {
                main.post { pending.finish() }
            }
        }.start()
    }
}
