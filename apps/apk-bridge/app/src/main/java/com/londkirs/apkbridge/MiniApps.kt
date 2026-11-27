package com.londkirs.apkbridge

/** The list of mini apps (terminal 🧩 and widget). New ones are added here. */
object MiniApps {
    class App(val id: String, val screen: Class<out BossActivity>, val title: () -> String, val description: () -> String)

    val ALL = listOf(
        App("battery", BatteryActivity::class.java, { tr("🔋 סוללה", "🔋 Battery") },
            { tr("מצב, זרם וטמפרטורה בזמן אמת, בריאות, מי צורך, פעולות", "State, live current and temperature, health, what uses it, actions") }),
        App("memory", MemoryActivity::class.java, { tr("🧠 זיכרון RAM", "🧠 RAM") },
            { tr("שימוש בזמן אמת, פירוט, תהליכים, פינוי זיכרון", "Live usage, breakdown, processes, freeing memory") }),
    )
}
