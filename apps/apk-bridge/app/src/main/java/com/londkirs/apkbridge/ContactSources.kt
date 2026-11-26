package com.londkirs.apkbridge

import android.content.Context
import android.provider.ContactsContract

/** Contacts accounts (Google, phone, SIM…) and groups, to choose which contacts to back up. */
object ContactSources {
    /** A contacts account (Google, phone, SIM…) or a contacts group, with how many contacts it holds. */
    data class ContactSource(val key: String, val label: String, val count: Int)

    /** Accounts and groups to choose from. Needs READ_CONTACTS (granted over ADB by the caller). */
    fun list(context: Context): List<ContactSource> {
        val cr = context.contentResolver
        val byAccount = linkedMapOf<Pair<String, String>, MutableSet<Long>>()
        cr.query(ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts.ACCOUNT_TYPE, ContactsContract.RawContacts.ACCOUNT_NAME, ContactsContract.RawContacts.CONTACT_ID),
            "${ContactsContract.RawContacts.DELETED}=0", null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(2).takeIf { it > 0 } ?: continue
                byAccount.getOrPut((c.getString(0) ?: "") to (c.getString(1) ?: "")) { mutableSetOf() } += id
            }
        }
        val out = mutableListOf<ContactSource>()
        for ((acct, ids) in byAccount) {
            val (type, name) = acct
            val label = when {
                type.contains("sim", ignoreCase = true) || name.contains("sim", ignoreCase = true) -> "📇 SIM" + if (name.isNotBlank()) " ($name)" else ""
                type.contains("google") -> "🟢 Google · $name"
                type.isBlank() && name.isBlank() -> tr("📱 בטלפון", "📱 On the phone")
                type.contains("whatsapp") -> "💚 WhatsApp"
                type.contains("telegram") -> "✈ Telegram"
                name.isBlank() -> "📱 $type"
                else -> "👤 $name"
            }
            out += ContactSource("acct:$type|$name", label, ids.size)
        }
        for ((key, title, ids) in groups(context)) out += ContactSource(key, "👥 $title", ids.size)
        return out.filter { it.count > 0 }
    }

    /** Contact groups (Family, Coworkers…) as (key, title, contact ids). */
    private fun groups(context: Context): List<Triple<String, String, Set<Long>>> {
        val cr = context.contentResolver
        val titles = linkedMapOf<Long, String>()
        cr.query(ContactsContract.Groups.CONTENT_URI, arrayOf(ContactsContract.Groups._ID, ContactsContract.Groups.TITLE),
            "${ContactsContract.Groups.DELETED}=0", null, null)?.use { c ->
            while (c.moveToNext()) c.getString(1)?.takeIf { it.isNotBlank() }?.let { titles[c.getLong(0)] = it }
        }
        if (titles.isEmpty()) return emptyList()
        val members = HashMap<Long, MutableSet<Long>>()
        cr.query(ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID, ContactsContract.Data.CONTACT_ID),
            "${ContactsContract.Data.MIMETYPE}=?", arrayOf(ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE), null)?.use { c ->
            while (c.moveToNext()) members.getOrPut(c.getLong(0)) { mutableSetOf() } += c.getLong(1)
        }
        // The same group name can exist once per account: merge them.
        return titles.entries.groupBy { it.value }.map { (title, entries) ->
            Triple("group:$title", title, entries.flatMap { members[it.key].orEmpty() }.toSet())
        }
    }

    /** Contact ids in any of the chosen accounts / groups. */
    fun contactIdsFor(context: Context, keys: Set<String>): Set<Long> {
        val cr = context.contentResolver
        val ids = mutableSetOf<Long>()
        val accounts = keys.filter { it.startsWith("acct:") }.map { it.removePrefix("acct:").split('|', limit = 2).let { p -> p[0] to p.getOrElse(1) { "" } } }.toSet()
        if (accounts.isNotEmpty()) cr.query(ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts.ACCOUNT_TYPE, ContactsContract.RawContacts.ACCOUNT_NAME, ContactsContract.RawContacts.CONTACT_ID),
            "${ContactsContract.RawContacts.DELETED}=0", null, null)?.use { c ->
            while (c.moveToNext()) if (((c.getString(0) ?: "") to (c.getString(1) ?: "")) in accounts) ids += c.getLong(2)
        }
        val groupKeys = keys.filter { it.startsWith("group:") }.toSet()
        if (groupKeys.isNotEmpty()) groups(context).filter { it.first in groupKeys }.forEach { ids += it.third }
        return ids
    }
}
