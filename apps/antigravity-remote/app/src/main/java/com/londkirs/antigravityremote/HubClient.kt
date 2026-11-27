package com.londkirs.antigravityremote

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch

/**
 * Minimal client for the local Antigravity hub (`agy --hub`). The hub speaks Connect-RPC; unary
 * calls accept plain JSON (proto3 JSON field names) at
 * /exa.language_server_pb.LanguageServerService/<Method>, protected by a CSRF token that the hub
 * serves on its root page. Protocol facts were learned from public community projects; this is an
 * independent implementation.
 */
object HubClient {
    private const val BASE = "http://127.0.0.1:${TerminalService.HUB_PORT}"
    private const val SERVICE = "/exa.language_server_pb.LanguageServerService/"
    @Volatile private var csrf: String? = null
    @Volatile private var tokenFile: java.io.File? = null

    private fun file(ctx: Context) = java.io.File(ctx.filesDir, "hub.csrf").also { tokenFile = it }

    /** Generates and stores the token the next hub start will use. */
    fun newToken(ctx: Context): String {
        val bytes = ByteArray(24).also { java.security.SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        val f = file(ctx)
        f.writeText(token)
        f.setReadable(false, false); f.setReadable(true, true)
        csrf = token
        return token
    }

    /** Token of the hub we started (null if the hub was not started by this app). */
    private fun storedToken(): String? = runCatching { tokenFile?.readText()?.trim() }.getOrNull()?.takeIf { it.isNotEmpty() }

    fun token(ctx: Context): String? { attach(ctx); return csrf ?: storedToken() }

    fun attach(ctx: Context) { file(ctx); if (csrf == null) csrf = storedToken() }

    fun isAlive(): Boolean = try {
        val c = URL("$BASE/").openConnection() as HttpURLConnection
        c.connectTimeout = 800; c.readTimeout = 1500
        val code = c.responseCode
        c.disconnect()
        code in 100..599
    } catch (e: Exception) { false }

    private fun fetchCsrf(): String? {
        val c = URL("$BASE/").openConnection() as HttpURLConnection
        c.connectTimeout = 2000; c.readTimeout = 4000
        try {
            (c.getHeaderField("x-antigravity-csrf-token") ?: c.getHeaderField("x-codeium-csrf-token"))?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            val body = (if (c.responseCode < 400) c.inputStream else c.errorStream)?.use { s ->
                val buf = ByteArray(256 * 1024); var n = 0
                while (n < buf.size) { val r = s.read(buf, n, buf.size - n); if (r < 0) break; n += r }
                String(buf, 0, n)
            } ?: return null
            Regex(""""csrfToken"\s*:\s*"([^"]+)"""").find(body)?.let { return it.groupValues[1] }
            Regex("""(?i)csrf[_-]?token["']?\s*[:=]\s*["']([^"']+)["']""").find(body)?.let { return it.groupValues[1] }
            return null
        } finally { c.disconnect() }
    }

    /** Blocking unary call. Throws IOException with the hub's error text on failure. */
    fun call(method: String, body: JSONObject, timeoutMs: Int = 30_000): JSONObject {
        for (attempt in 0..1) {
            if (csrf == null) csrf = storedToken()
            if (csrf == null || attempt == 1) csrf = storedToken()?.takeIf { attempt == 0 } ?: runCatching { fetchCsrf() }.getOrNull() ?: csrf
            val c = URL(BASE + SERVICE + method).openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 3000
            c.readTimeout = timeoutMs
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Connect-Protocol-Version", "1")
            csrf?.let {
                c.setRequestProperty("x-antigravity-csrf-token", it)
                c.setRequestProperty("x-codeium-csrf-token", it)
            }
            try {
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = c.responseCode
                val text = (if (code < 400) c.inputStream else c.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                if ((code == 401 || code == 403) && attempt == 0) continue
                if (code >= 400) {
                    val msg = runCatching { JSONObject(text).optString("message") }.getOrNull()?.takeIf { it.isNotBlank() } ?: text.take(300)
                    throw IOException("$method: HTTP $code ${msg}")
                }
                return if (text.isBlank()) JSONObject() else JSONObject(text)
            } finally { c.disconnect() }
        }
        throw IOException("$method: unauthorized")
    }

    fun hasValidAuth(): Boolean =
        call("GetAuthStatus", JSONObject()).optJSONObject("authResult")?.optBoolean("hasValidAuth") == true

    /** Asks the hub to start Google sign-in; it opens the URL through $BROWSER (our open-url bridge). */
    /** Blocks until the browser round-trip finishes: the hub keeps its OAuth callback listener only while this call is open. */
    fun login() { call("Login", JSONObject().put("isGcpTos", false), 10 * 60_000) }

    data class ModelChoice(val value: Any, val name: String, val quota: Double? = null, val images: Boolean = true)

    /** Models as the web UI lists them (GetUserStatus → clientModelConfigs), with remaining quota. */
    fun models(): List<ModelChoice> {
        val out = mutableListOf<ModelChoice>()
        runCatching {
            val cfgs = call("GetUserStatus", JSONObject()).optJSONObject("userStatus")
                ?.optJSONObject("cascadeModelConfigData")?.optJSONArray("clientModelConfigs")
            if (cfgs != null) for (i in 0 until cfgs.length()) {
                val c = cfgs.optJSONObject(i) ?: continue
                if (c.optBoolean("disabled")) continue
                val value = c.optJSONObject("modelOrAlias")?.opt("model") ?: continue
                if (out.any { it.value == value }) continue
                val q = c.optJSONObject("quotaInfo")?.takeIf { it.has("remainingFraction") }?.optDouble("remainingFraction")
                out += ModelChoice(value, c.optString("label").ifBlank { value.toString() }, q, c.optBoolean("supportsImages", true))
            }
        }
        if (out.isNotEmpty()) return out
        // Fallback: GetAvailableModels.
        val r = call("GetAvailableModels", JSONObject()).optJSONObject("response") ?: return emptyList()
        val map = r.optJSONObject("models") ?: return emptyList()
        val order = mutableListOf<String>()
        r.optString("defaultAgentModelId").takeIf { it.isNotBlank() }?.let { order += it }
        map.keys().forEach { if (it !in order) order += it }
        for (key in order) {
            val d = map.optJSONObject(key) ?: continue
            if (d.optBoolean("disabled") || d.optBoolean("isInternal")) continue
            val value = d.opt("model") ?: continue
            if (value == "MODEL_UNSPECIFIED" || out.any { it.value == value }) continue
            out += ModelChoice(value, d.optString("displayName").ifBlank { key })
        }
        return out
    }

    /** Shares the model choice with the web UI (same call it makes). */
    fun rememberModel(model: Any) {
        runCatching { call("JetboxWriteState", JSONObject().put("appState", JSONObject().put("lastSelectedAgentModel", model))) }
    }

    /** Agent config as the hub web UI sends it. */
    fun cascadeConfig(model: Any?, autoPolicy: String, withExecutor: Boolean = true): JSONObject {
        val planner = JSONObject()
            .put("toolConfig", JSONObject()
                .put("runCommand", JSONObject().put("autoCommandConfig", JSONObject().put("autoExecutionPolicy", autoPolicy)))
                .put("antigravityBrowser", JSONObject().put("enableBrowserSubagentWebview", false)))
            .put("useAiCredits", false)
            .put("supportsLatexRendering", false)
        if (model != null) planner.put("planModel", model)
        val cfg = JSONObject().put("plannerConfig", planner).put("conversationHistoryConfig", JSONObject())
        if (withExecutor) cfg.put("executorConfig", JSONObject().put("useCoreDirect", true))
        return cfg
    }

    fun agentSpec(): JSONObject = JSONObject().put("builtinAgent", JSONObject().put("defaultAgent",
        JSONObject().put("isGoogle", false).put("isInteractive", true).put("surface", "AGENT_SURFACE_HUB")))

    data class SlashCommand(val title: String, val description: String, val info: JSONObject)

    fun slashCommands(cascadeId: String?, model: Any?, autoPolicy: String): List<SlashCommand> {
        val req = JSONObject().put("cascadeConfig", cascadeConfig(model, autoPolicy, withExecutor = false)).put("customAgentSpec", agentSpec())
        if (cascadeId != null) req.put("cascadeId", cascadeId)
        val arr = call("GetSlashCommands", req).optJSONArray("commands") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val c = arr.optJSONObject(i) ?: return@mapNotNull null
            val info = c.optJSONObject("info") ?: return@mapNotNull null
            SlashCommand(c.optString("title").ifBlank { info.optString("name") }, c.optString("description"), info)
        }
    }

    /** Uploads an image the way the web UI does; returns the artifact URI. */
    fun saveMedia(mime: String, base64: String): String =
        call("SaveMediaAsArtifact", JSONObject().put("media", JSONObject().put("mimeType", mime).put("inlineData", base64)), 120_000)
            .optString("uri")

    fun startCascade(cascadeId: String, workspaceDir: String, model: Any?) {
        val uri = "file://$workspaceDir"
        call("StartCascade", JSONObject()
            .put("cascadeId", cascadeId)
            .put("source", "CORTEX_TRAJECTORY_SOURCE_CASCADE_CLIENT")
            .put("workspaceUris", org.json.JSONArray().put(uri))
            .put("overrideWorkspaceUris", org.json.JSONArray().put(uri))
            .apply { if (model != null) put("requestedModel", model) })
    }

    fun sendMessage(
        cascadeId: String, items: org.json.JSONArray, clientMessageId: String, model: Any?,
        autoPolicy: String, media: org.json.JSONArray? = null, now: Boolean = false,
    ) {
        val req = JSONObject()
            .put("cascadeId", cascadeId)
            .put("items", items)
            .put("cascadeConfig", cascadeConfig(model, autoPolicy))
            .put("customAgentSpec", agentSpec())
            .put("deliveryStrategy", if (now) "MESSAGE_DELIVERY_STRATEGY_NEXT_INVOCATION" else "MESSAGE_DELIVERY_STRATEGY_WHEN_IDLE")
            .put("clientMessageId", clientMessageId)
        if (media != null && media.length() > 0) req.put("media", media)
        call("SendUserCascadeMessage", req, 120_000)
    }

    fun steps(cascadeId: String): org.json.JSONArray =
        call("GetCascadeTrajectorySteps", JSONObject().put("cascadeId", cascadeId)).optJSONArray("steps") ?: org.json.JSONArray()

    fun cancel(cascadeId: String) {
        call("CancelCascadeInvocation", JSONObject().put("cascadeId", cascadeId).put("killBackgroundTasks", true))
    }

    fun respond(cascadeId: String, interaction: JSONObject) {
        call("HandleCascadeUserInteraction", JSONObject().put("cascadeId", cascadeId).put("interaction", interaction))
    }

    /**
     * Makes sure `agy --hub` is running (starting it in the terminal service if needed), then calls
     * [onResult] on the main thread with true when the hub answers on its port.
     */
    fun ensureRunning(activity: Activity, onResult: (Boolean) -> Unit) {
        val main = Handler(activity.mainLooper)
        attach(activity)
        Thread {
            if (isAlive() && storedToken() != null) { main.post { onResult(true) }; return@Thread }
            val conn = object : ServiceConnection {
                override fun onServiceDisconnected(name: ComponentName?) {}
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    val svc = (binder as TerminalService.LocalBinder).service
                    val self = this
                    Thread {
                        // A hub without our token (e.g. started by an older version) is replaced.
                        if (svc.hubSession() != null && storedToken() == null) {
                            val latch0 = CountDownLatch(1)
                            main.post { svc.hubSession()?.let { svc.remove(it) }; latch0.countDown() }
                            latch0.await(); Thread.sleep(1500)
                        }
                        if (svc.hubSession() == null) {
                            val latch = CountDownLatch(1)
                            main.post {
                                // The PTY process only starts once the terminal has a size.
                                svc.startHub().updateSize(100, 40)
                                latch.countDown()
                            }
                            latch.await()
                        }
                        var ok = false
                        for (i in 0 until 180) { // up to ~90 s on slow phones
                            if (isAlive()) { ok = true; break }
                            if (svc.hubSession() == null && i > 6) break
                            Thread.sleep(500)
                        }
                        main.post {
                            runCatching { activity.unbindService(self) }
                            onResult(ok)
                        }
                    }.start()
                }
            }
            main.post {
                val intent = Intent(activity, TerminalService::class.java)
                activity.startService(intent)
                activity.bindService(intent, conn, Context.BIND_AUTO_CREATE)
            }
        }.start()
    }
}
