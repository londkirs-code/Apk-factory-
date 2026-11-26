package com.londkirs.apkbridge

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The terminal's shortcuts: the built-in list (by category, Hebrew + English), the user's saved
 * list in the "terminal" preferences, and the migrations between versions. Used by the terminal
 * and by the home-screen widget settings.
 */
object TerminalShortcuts {
    class Shortcut(var label: String, var command: String, var group: String = MINE)

    const val MINE = "mine"
    const val VERSION = 7

    /** Built-in commands fixed in 1.4.3: lists saved by 1.4–1.4.2 get the new version (index into DEFAULT_SHORTCUTS by label). */
    private val FIXED_COMMANDS: Map<String, String> by lazy {
        mapOf(
            "dumpsys battery | awk -F': ' '/ level:/{print \"Level: \"$2\"%\"} /temperature:/{printf \"Temperature: %.1f C\\n\",$2/10} " +
                "/health:/{h=$2; print \"Health: \" (h==2?\"Good\":h==3?\"Overheat\":h==4?\"Dead\":h==5?\"Over voltage\":h==7?\"Cold\":\"Unknown\")} " +
                "/voltage:/{print \"Voltage: \"$2\" mV\"} /status:/{st=$2; print \"Status: \" (st==2?\"Charging\":st==3?\"Discharging\":st==5?\"Full\":\"Not charging\")}'; " +
                "f=/sys/class/power_supply/battery; [ -r \$f/cycle_count ] && echo \"Charge cycles: $(cat \$f/cycle_count)\"; " +
                "if [ -r \$f/charge_full ] && [ -r \$f/charge_full_design ]; then echo \"Capacity left: $(( $(cat \$f/charge_full) * 100 / $(cat \$f/charge_full_design) ))% of original\"; fi" to "🔋 Battery health",
            "du -sh /sdcard/* /sdcard/Android/media/* 2>/dev/null | sort -rh | head -12" to "📂 Largest folders",
            "dumpsys activity activities | grep -m1 -E 'mResumedActivity|topResumedActivity'" to "Foreground app",
        ).mapValues { (_, en) -> DEFAULT_SHORTCUTS.first { it.en == en }.command }
    }
    val GROUP_ORDER = listOf("toggles", "screen", "info", "maint", "tools", MINE)
    fun groupLabel(id: String) = when (id) {
        "toggles" -> tr("מתגים", "Toggles")
        "screen" -> tr("מסך ומקשים", "Screen & keys")
        "info" -> tr("מידע", "Info")
        "maint" -> tr("תחזוקה", "Maintenance")
        "tools" -> tr("כלים", "Tools")
        MINE -> tr("שלי", "Mine")
        else -> id
    }
    /** Category names saved by versions before 1.4 (Hebrew) → stable ids. */
    val OLD_GROUPS = mapOf("מתגים" to "toggles", "מסך ומקשים" to "screen", "מידע" to "info", "תחזוקה" to "maint", "כלים" to "tools", "שלי" to MINE) // i18n-ok

    /** Built-in shortcut: category id, Hebrew label, English label, command. */
    class Def(val group: String, val he: String, val en: String, val command: String) {
        val label get() = tr(he, en)
    }

    /** Commands try the modern "cmd" form first and fall back to "svc". */
    val DEFAULT_SHORTCUTS = listOf(
        // Toggles
        Def("toggles", "📶 Wi‑Fi הפעל/כבה", "📶 Wi‑Fi toggle", ShortcutWidget.WIFI_TOGGLE),
        Def("toggles", "🔵 בלוטות׳ הפעל/כבה", "🔵 Bluetooth toggle", ShortcutWidget.BT_TOGGLE),
        Def("toggles", "📶 Wi‑Fi פועל", "📶 Wi‑Fi on", "cmd wifi set-wifi-enabled enabled 2>/dev/null || svc wifi enable"),
        Def("toggles", "📶 Wi‑Fi כבוי", "📶 Wi‑Fi off", "cmd wifi set-wifi-enabled disabled 2>/dev/null || svc wifi disable"),
        Def("toggles", "🔵 בלוטות׳ פועל", "🔵 Bluetooth on", "cmd bluetooth_manager enable 2>/dev/null || svc bluetooth enable"),
        Def("toggles", "🔵 בלוטות׳ כבוי", "🔵 Bluetooth off", "cmd bluetooth_manager disable 2>/dev/null || svc bluetooth disable"),
        Def("toggles", "📱 נתונים פועל", "📱 Mobile data on", "svc data enable"),
        Def("toggles", "📱 נתונים כבוי", "📱 Mobile data off", "svc data disable"),
        Def("toggles", "✈ מצב טיסה פועל", "✈ Airplane mode on", "cmd connectivity airplane-mode enable"),
        Def("toggles", "✈ מצב טיסה כבוי", "✈ Airplane mode off", "cmd connectivity airplane-mode disable"),
        Def("toggles", "📍 מיקום פועל", "📍 Location on", "cmd location set-location-enabled true"),
        Def("toggles", "📍 מיקום כבוי", "📍 Location off", "cmd location set-location-enabled false"),
        Def("toggles", "🔕 נא לא להפריע", "🔕 Do not disturb", "cmd notification set_dnd priority"),
        Def("toggles", "🔔 בטל נא לא להפריע", "🔔 Do not disturb off", "cmd notification set_dnd off"),
        Def("toggles", "🔋 חיסכון בסוללה פועל", "🔋 Battery saver on", "cmd power set-mode 1"),
        Def("toggles", "🔋 חיסכון בסוללה כבוי", "🔋 Battery saver off", "cmd power set-mode 0"),
        Def("toggles", "🔄 סיבוב אוטומטי פועל", "🔄 Auto-rotate on", "settings put system accelerometer_rotation 1"),
        Def("toggles", "🔄 סיבוב אוטומטי כבוי", "🔄 Auto-rotate off", "settings put system accelerometer_rotation 0"),
        Def("toggles", "NFC פועל", "NFC on", "svc nfc enable"),
        Def("toggles", "NFC כבוי", "NFC off", "svc nfc disable"),
        // Screen & keys
        Def("screen", "📳 רטט", "📳 Vibrate", "cmd vibrator_manager synced oneshot 600 2>/dev/null || cmd vibrator vibrate 600"),
        Def("screen", "☀ בהירות מלאה", "☀ Full brightness", "settings put system screen_brightness_mode 0; settings put system screen_brightness 255"),
        Def("screen", "🌗 בהירות אוטומטית", "🌗 Auto brightness", "settings put system screen_brightness_mode 1"),
        Def("screen", "🔊 עוצמה +", "🔊 Volume +", "input keyevent KEYCODE_VOLUME_UP"),
        Def("screen", "🔉 עוצמה −", "🔉 Volume −", "input keyevent KEYCODE_VOLUME_DOWN"),
        Def("screen", "🔇 השתק", "🔇 Mute", "input keyevent KEYCODE_VOLUME_MUTE"),
        Def("screen", "⏯ נגן/השהה", "⏯ Play/Pause", "input keyevent KEYCODE_MEDIA_PLAY_PAUSE"),
        Def("screen", "💡 הער מסך", "💡 Wake screen", "input keyevent KEYCODE_WAKEUP"),
        Def("screen", "🔒 נעל מסך", "🔒 Lock screen", "input keyevent KEYCODE_SLEEP"),
        Def("screen", "🏠 בית", "🏠 Home", "input keyevent KEYCODE_HOME"),
        Def("screen", "↩ חזור", "↩ Back", "input keyevent KEYCODE_BACK"),
        Def("screen", "🗂 אפליקציות אחרונות", "🗂 Recent apps", "input keyevent KEYCODE_APP_SWITCH"),
        Def("screen", "⏱ מסך נכבה אחרי 10 דק׳", "⏱ Screen off after 10 min", "settings put system screen_off_timeout 600000"),
        Def("screen", "📸 צילום מסך", "📸 Screenshot", "screencap -p /sdcard/Download/screenshot.png && echo saved to Download"),
        // Info
        Def("info", "גרסה", "Version", "getprop ro.build.version.release"),
        Def("info", "דגם", "Model", "getprop ro.product.model"),
        Def("info", "סוללה", "Battery", "dumpsys battery"),
        Def("info", "מסך", "Screen", "wm size; wm density"),
        Def("info", "אחסון", "Storage", "df -h /data /sdcard"),
        Def("info", "זיכרון", "Memory", "cat /proc/meminfo | head -5"),
        Def("info", "רשת", "Network", "ip addr show wlan0"),
        Def("info", "אפליקציה פתוחה", "Foreground app", "dumpsys activity activities 2>/dev/null | grep -m1 -E 'mResumedActivity|topResumedActivity'"),
        Def("info", "זמן פעילות", "Uptime", "uptime"),
        Def("info", "תהליכים", "Processes", "top -b -n 1 | head -25"),
        Def("info", "לוג", "Log", "logcat -d -t 100"),
        // Tools
        Def("tools", "אפליקציות שהותקנו", "Installed apps", "pm list packages -3"),
        Def("tools", "⚙ פתח הגדרות", "⚙ Open settings", "am start -a android.settings.SETTINGS"),
        Def("tools", "🧹 סגור אפליקציות ברקע", "🧹 Close background apps", "am kill-all"),
        Def("tools", "♻ הפעלה מחדש", "♻ Restart", "reboot"),
        // Maintenance: cache cleaning and phone health (output from the device, in English)
        Def("maint", "🧠 פנה זיכרון RAM", "🧠 Free up RAM",
            "m(){ awk '/MemAvailable/{print int($2/1024)}' /proc/meminfo; }; b=$(m); me=com.londkirs.apkbridge; n=0; for p in $(ps -A -o NAME | grep -E '^[a-z][a-z0-9_]*(\\.[A-Za-z0-9_]+)+$' | grep -vE \"^(\$me|com\\.android\\.systemui|com\\.android\\.phone|system)$\" | sort -u); do am send-trim-memory \"\$p\" COMPLETE >/dev/null 2>&1 || am send-trim-memory \"\$p\" RUNNING_CRITICAL >/dev/null 2>&1 && n=$((n+1)); done; am kill-all; am compact system >/dev/null 2>&1; sleep 2; a=$(m); echo \"Asked \$n apps to release memory, stopped background processes.\"; echo \"Available RAM: \$b MB -> \$a MB ($((a-b)) MB)\""),
        // Aggressive: also force-stops every running downloaded app (except The Boss, the keyboard and the home screen).
        Def("maint", "🧨 פינוי RAM אגרסיבי (סוגר אפליקציות)", "🧨 Aggressive RAM cleanup (closes apps)",
            "m(){ awk '/MemAvailable/{print int($2/1024)}' /proc/meminfo; }; b=$(m); me=com.londkirs.apkbridge; ime=$(settings get secure default_input_method | cut -d/ -f1); home=$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null | tail -1 | cut -d/ -f1); keep=\"^(\$me|\$ime|\$home|com\\.android\\.systemui|com\\.android\\.phone|system)$\"; running=$(ps -A -o NAME | grep -E '^[a-z][a-z0-9_]*(\\.[A-Za-z0-9_]+)+(:.*)?$' | cut -d: -f1 | sort -u); user=$(pm list packages -3 | cut -d: -f2); s=0; for p in \$running; do echo \"\$p\" | grep -qE \"\$keep\" && continue; if echo \"\$user\" | grep -qx \"\$p\"; then am force-stop \"\$p\" && s=$((s+1)); else am send-trim-memory \"\$p\" COMPLETE >/dev/null 2>&1 || am send-trim-memory \"\$p\" RUNNING_CRITICAL >/dev/null 2>&1; fi; done; am kill-all; am compact system >/dev/null 2>&1; sleep 3; a=$(m); echo \"Closed \$s apps, stopped all background processes.\"; echo \"Available RAM: \$b MB -> \$a MB ($((a-b)) MB)\""),
        Def("maint", "🧹 נקה מטמון לכל האפליקציות", "🧹 Clear cache of all apps",
            "b=$(df /data | awk 'NR==2{print $4}'); pm trim-caches 999G; a=$(df /data | awk 'NR==2{print $4}'); echo \"Cache cleared. Freed: $(( (a-b)/1024 )) MB\""),
        Def("maint", "🔋 בריאות סוללה", "🔋 Battery health",
            "dumpsys battery | awk -F': *' '{k=$1; gsub(/^ +/,\"\",k); v[k]=$2} END {print \"Level: \" v[\"level\"] \"%\"; st=v[\"status\"]; print \"Status: \" (st==2?\"Charging\":st==3?\"Discharging\":st==4?\"Not charging\":st==5?\"Full\":\"Unknown\"); h=v[\"health\"]; print \"Health: \" (h==2?\"Good\":h==3?\"Overheat\":h==4?\"Dead\":h==5?\"Over voltage\":h==6?\"Failure\":h==7?\"Cold\":\"Unknown\"); t=v[\"temperature\"]/10; printf \"Temperature: %.1f C%s\\n\", t, (t<5?\" (unusual — the sensor may report in other units)\":\"\"); print \"Voltage: \" v[\"voltage\"] \" mV\"}'; f=/sys/class/power_supply/battery; c=$(cat \$f/cycle_count 2>/dev/null); [ -n \"\$c\" ] && [ \"\$c\" -gt 1 ] && echo \"Charge cycles: \$c\"; cf=$(cat \$f/charge_full 2>/dev/null); cd=$(cat \$f/charge_full_design 2>/dev/null); if [ -n \"\$cf\" ] && [ -n \"\$cd\" ] && [ \"\$cd\" -gt 0 ]; then echo \"\$cf \$cd\" | awk '{r=$1/$2; while (r > 1.2) r/=10; while (r < 0.12) r*=10; p=int(r*100+0.5); if (p>=30 && p<=110) printf \"Capacity left: %d%% of original\\n\", p}'; fi"),
        Def("maint", "🌡 טמפרטורות", "🌡 Temperatures",
            "for z in /sys/class/thermal/thermal_zone*; do t=$(cat \$z/temp 2>/dev/null); n=$(cat \$z/type 2>/dev/null); [ -n \"\$t\" ] && [ \"\$t\" -gt 0 ] && echo \"\$n: $((t/1000)) C\"; done | head -15"),
        Def("maint", "💾 ניצול אחסון", "💾 Storage usage",
            "df -h /data | tail -1 | awk '{print \"Used \"$3\" of \"$2\" (\"$5\"), free \"$4}'; echo; " +
                "dumpsys diskstats | grep -E '^(App Size|App Data Size|App Cache Size|Photos Size|Videos Size|Audio Size|Downloads Size|System Size|Other Size):' | " +
                "awk -F': ' '{printf \"%-16s %.2f GB\\n\", $1, $2/1073741824}'"),
        Def("maint", "📂 התיקיות הגדולות", "📂 Largest folders",
            "du -sk /sdcard/* /sdcard/Android/media/* 2>/dev/null | sort -rn | head -12 | awk '{s=$1; $1=\"\"; sub(/^ /,\"\"); if (s>=1048576) printf \"%6.1f GB  %s\\n\", s/1048576, $0; else printf \"%6.0f MB  %s\\n\", s/1024, $0}'"),
    )

    /** The regular "free up RAM" command (the widget's one-button default). */
    val FREE_RAM: String get() = DEFAULT_SHORTCUTS.first { it.en == "🧠 Free up RAM" }.command

    private fun prefs(context: Context) = context.getSharedPreferences("terminal", Context.MODE_PRIVATE)

    fun defaults() = DEFAULT_SHORTCUTS.map { Shortcut(it.label, it.command, it.group) }.toMutableList()

    /** Built-in shortcuts are shown in the current language, whichever language they were saved in. */
    fun shown(sc: Shortcut): String =
        DEFAULT_SHORTCUTS.firstOrNull { it.command == sc.command && (sc.label == it.he || sc.label == it.en) }?.label ?: sc.label

    /** The user's list (or the built-ins). Lists saved by older versions are upgraded once. */
    fun load(context: Context): MutableList<Shortcut> {
        val p = prefs(context)
        val json = p.getString("shortcuts", null) ?: return defaults()
        val list = runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                arr.getJSONObject(i).let { o -> Shortcut(o.getString("l"), o.getString("c"), o.optString("g", "").let { g -> OLD_GROUPS[g] ?: g }) }
            }.toMutableList()
        }.getOrElse { return defaults() }
        // Older lists: fixed commands, a category for each item, and the new built-in shortcuts (once).
        if (p.getInt("shortcuts_v", 1) < VERSION) {
            list.forEach { sc -> FIXED_COMMANDS[sc.command]?.let { sc.command = it } }
            val byCommand = DEFAULT_SHORTCUTS.associate { it.command to it.group }
            list.forEach { if (it.group.isBlank()) it.group = byCommand[it.command] ?: MINE }
            val have = list.map { it.command }.toSet()
            defaults().filter { it.command !in have }.forEach { list += it }
            save(context, list)
        }
        list.forEach { if (it.group.isBlank()) it.group = MINE }
        return list
    }

    fun save(context: Context, items: List<Shortcut>) {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("l", it.label).put("c", it.command).put("g", it.group)) }
        prefs(context).edit().putString("shortcuts", arr.toString()).putInt("shortcuts_v", VERSION).apply()
    }

    /** Back to the built-in list. */
    fun reset(context: Context) = prefs(context).edit().remove("shortcuts").putInt("shortcuts_v", VERSION).apply()

    /** Categories present in [items], in the usual order. */
    fun groups(items: List<Shortcut>): List<String> {
        val present = items.map { it.group }.toSet()
        return (GROUP_ORDER.filter { it in present } + present.filter { it !in GROUP_ORDER }).ifEmpty { listOf(MINE) }
    }

    /** Category id of the on/off shortcuts (the widget offers real toggles instead). */
    const val TOGGLES_GROUP = "toggles"

    /** Every shortcut grouped by category (labels in the current language) — for the widget settings. */
    fun choices(context: Context, exclude: Set<String> = emptySet()): List<Pair<String, List<ShortcutWidget.Pin>>> {
        val items = load(context)
        return groups(items).filter { it !in exclude }.map { g ->
            groupLabel(g) to items.filter { it.group == g }.map { ShortcutWidget.Pin(shown(it), it.command) }
        }
    }
}
