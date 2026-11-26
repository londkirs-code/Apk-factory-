package com.londkirs.apkbridge

/**
 * Turns whatever the user typed or pasted (from a computer guide, a chat, a code block…)
 * into a command that runs in the device shell — without doubling "adb shell".
 *
 *  "adb shell pm list packages"         → pm list packages
 *  "adb -s R58M shell 'dumpsys battery'" → dumpsys battery
 *  "adb shell adb shell ls"              → ls
 *  "$ getprop ro.product.model"          → getprop ro.product.model
 *  ```bash … ```                          → fenced code without the fences
 *  "adb uninstall com.x"                 → pm uninstall com.x
 *  "adb reboot recovery"                 → reboot recovery
 *  "adb logcat -d"                       → logcat -d
 *  "adb install x.apk" / push / pull     → explained: needs a file on the computer
 * Multi-line scripts are handled line by line; "#" comment lines are dropped.
 */
object CommandParser {
    data class Parsed(val command: String?, val note: String? = null)

    /** First words that are typical Android shell commands (used to recognise commands in the clipboard). */
    private val KNOWN = setOf(
        "adb", "pm", "am", "cmd", "settings", "dumpsys", "getprop", "setprop", "wm", "input", "svc", "logcat",
        "ls", "cat", "rm", "cp", "mv", "mkdir", "rmdir", "touch", "df", "du", "top", "ps", "kill", "killall", "pidof",
        "reboot", "screencap", "screenrecord", "monkey", "appops", "content", "ip", "ping", "echo", "sh", "su",
        "chmod", "chown", "find", "grep", "which", "uname", "id", "whoami", "date", "uptime", "free", "netstat",
        "ifconfig", "bmgr", "device_config", "telecom", "media", "uiautomator", "run-as", "sm", "dpm",
        "locksettings", "service", "setenforce", "getenforce", "tail", "head", "wc", "sed", "awk", "sort", "uniq",
        "mount", "toybox", "busybox", "stat", "readlink", "ln", "env", "export", "sleep", "printf", "test", "for",
        "while", "if", "notify", "cmd", "pgrep", "nice", "renice", "lsof", "tar", "gzip", "unzip", "md5sum",
        "sha256sum", "base64", "xargs", "tee", "true", "false", "cd", "pwd", "clear", "exit", "shell",
    )

    fun parse(raw: String): Parsed {
        val text = stripFences(raw).replace("\r\n", "\n").trim()
        if (text.isEmpty()) return Parsed(null)
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        if (lines.isEmpty()) return Parsed(null, tr("אין פקודה — רק הערות.", "No command — only comments."))
        val out = mutableListOf<String>()
        val notes = mutableListOf<String>()
        for (line in lines) {
            val p = parseLine(line)
            p.command?.let(out::add)
            p.note?.let(notes::add)
        }
        val command = out.joinToString("\n").ifBlank { null }
        return Parsed(command, notes.distinct().joinToString("\n").ifBlank { null })
    }

    /** Back-compat helper: just the command (or empty). */
    fun normalize(raw: String): String = parse(raw).command.orEmpty()

    private fun parseLine(input: String): Parsed {
        var line = stripPrompt(input)
        // Peel off any number of "adb [global options] [shell]" layers.
        var guard = 0
        while (guard++ < 5) {
            val words = tokens(line)
            if (words.isEmpty()) return Parsed(null)
            when (words[0]) {
                "adb", "adb.exe", "./adb" -> {
                    val rest = skipAdbOptions(words.drop(1))
                    if (rest.isEmpty()) return Parsed(null, tr("\"adb\" לבד אינו פקודה.", "\"adb\" alone isn't a command."))
                    val tail = joinFrom(line, rest)
                    val sub = rest[0]
                    when (sub) {
                        "shell" -> {
                            val cmd = joinFrom(line, rest.drop(1))
                            if (cmd.isBlank()) return Parsed(null, tr("מסוף אינטראקטיבי (adb shell בלי פקודה) לא נתמך — כתוב פקודה מלאה.", "An interactive shell (adb shell without a command) isn't supported — write a full command."))
                            line = unquote(cmd)
                            continue
                        }
                        "wait-for-device" -> { line = joinFrom(line, rest.drop(1)); if (line.isBlank()) return Parsed(null); continue }
                        "logcat" -> return Parsed(tail)
                        "reboot" -> return Parsed(tail)
                        "uninstall" -> return Parsed("pm uninstall " + joinFrom(line, rest.drop(1)))
                        "get-serialno" -> return Parsed("getprop ro.serialno")
                        "get-state" -> return Parsed("echo device")
                        "root" -> return Parsed(null, tr("\"adb root\" עובד רק בגרסאות פיתוח של Android, לא בטלפון רגיל.", "\"adb root\" only works on development builds of Android, not on a regular phone."))
                        "install", "install-multiple", "push", "pull", "sideload" ->
                            return Parsed(null, tr("\"adb $sub\" מעביר קובץ מהמחשב. כאן הקובץ כבר בטלפון: להתקנה השתמש בכפתור \"בחר קבצים\" במסך הראשי.", "\"adb $sub\" transfers a file from a computer. Here the file is already on the phone: to install, use the \"Choose files\" button on the main screen."))
                        "devices", "connect", "disconnect", "pair", "kill-server", "start-server", "version", "tcpip", "usb", "forward", "reverse" ->
                            return Parsed(null, tr("\"adb $sub\" היא פקודה של המחשב, והבוס מטפל בחיבור לבד.", "\"adb $sub\" is a computer command, and The Boss handles the connection by itself."))
                        else -> return Parsed(null, tr("פקודת adb לא מוכרת: \"$sub\". לפקודות טלפון כתוב \"adb shell …\" או את הפקודה עצמה.", "Unknown adb command: \"$sub\". For phone commands write \"adb shell …\" or the command itself."))
                    }
                }
                // "shell ls" — there is no "shell" binary on Android; the user meant adb shell.
                "shell" -> {
                    val cmd = joinFrom(line, words.drop(1))
                    if (cmd.isBlank()) return Parsed(null, tr("מסוף אינטראקטיבי לא נתמך — כתוב פקודה מלאה.", "Interactive shell isn't supported — write a full command."))
                    line = unquote(cmd); continue
                }
                else -> return Parsed(line)
            }
        }
        return Parsed(line)
    }

    /** Skips adb's global options: -s SERIAL, -t ID, -d, -e, -a, -H host, -P port, -L socket. */
    private fun skipAdbOptions(words: List<String>): List<String> {
        var i = 0
        while (i < words.size && words[i].startsWith("-")) {
            i += if (words[i] in setOf("-s", "-t", "-H", "-P", "-L") && i + 1 < words.size) 2 else 1
        }
        return words.drop(i)
    }

    /** Prompts copied from a terminal: "$ ", "> ", "shell@x:/ $ ", "C:\…> ", "PS C:\…> ". */
    private fun stripPrompt(line: String): String {
        var s = line
        Regex("^[\\w.-]+@[\\w.-]+:[^\\s$#]*\\s*[$#]\\s+").find(s)?.let { s = s.substring(it.range.last + 1) }
        Regex("^(PS )?[A-Za-z]:\\\\[^>]*>\\s*").find(s)?.let { s = s.substring(it.range.last + 1) }
        if (s.startsWith("$ ") || s.startsWith("> ")) s = s.substring(2)
        if (s.startsWith("`") && s.endsWith("`") && s.length > 2) s = s.trim('`')
        return s.trim()
    }

    private fun stripFences(raw: String): String {
        val fence = Regex("```[a-zA-Z0-9_-]*\\n?([\\s\\S]*?)```").find(raw) ?: return raw
        return fence.groupValues[1]
    }

    /** Removes one pair of quotes around the whole command: adb shell "pm list packages". */
    private fun unquote(s: String): String {
        val t = s.trim()
        if (t.length >= 2 && ((t.startsWith('"') && t.endsWith('"')) || (t.startsWith('\'') && t.endsWith('\'')))) {
            val inner = t.substring(1, t.length - 1)
            if (!inner.contains(t[0])) return inner.trim()
        }
        return t
    }

    private fun tokens(s: String) = s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    /** The original text starting at the given remaining tokens (keeps the user's quoting/spacing). */
    private fun joinFrom(line: String, rest: List<String>): String {
        if (rest.isEmpty()) return ""
        val all = tokens(line)
        val skip = all.size - rest.size
        var idx = 0
        var count = 0
        val trimmed = line.trim()
        while (count < skip) {
            while (idx < trimmed.length && trimmed[idx].isWhitespace()) idx++
            while (idx < trimmed.length && !trimmed[idx].isWhitespace()) idx++
            count++
        }
        return trimmed.substring(idx).trim()
    }

    /**
     * Does this clipboard text look like something to run in the shell?
     * True for "adb …" lines, code blocks, and lines that start with a known Android command.
     */
    fun looksLikeCommand(raw: String): Boolean {
        val t = raw.trim()
        if (t.isEmpty() || t.length > 4000) return false
        if (t.startsWith("http://") || t.startsWith("https://")) return false
        val body = stripFences(t).trim()
        val lines = body.lines().map { stripPrompt(it.trim()) }.filter { it.isNotEmpty() && !it.startsWith("#") }
        if (lines.isEmpty() || lines.size > 30) return false
        // Every line must start with a known command word; this avoids offering to "run" ordinary text.
        return lines.all { line ->
            val first = tokens(line).firstOrNull()?.lowercase()?.removeSuffix(".exe") ?: return@all false
            first in KNOWN || first.startsWith("/system/") || first.startsWith("./") || first.startsWith("/data/local/tmp/")
        }
    }
}
