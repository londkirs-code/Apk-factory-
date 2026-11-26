package com.londkirs.apkbridge

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract

/** Small helpers for writing and reading a folder the user picked (Storage Access Framework). */
object Saf {
    data class Child(val uri: Uri, val name: String, val isDir: Boolean, val size: Long)

    fun root(tree: Uri): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    fun mkdir(cr: ContentResolver, parent: Uri, name: String): Uri =
        DocumentsContract.createDocument(cr, parent, DocumentsContract.Document.MIME_TYPE_DIR, name)
            ?: error("לא ניתן ליצור תיקייה $name")

    fun create(cr: ContentResolver, parent: Uri, mime: String, name: String): Uri =
        DocumentsContract.createDocument(cr, parent, mime, name) ?: error("לא ניתן ליצור קובץ $name")

    fun writeText(cr: ContentResolver, parent: Uri, name: String, text: String) {
        val uri = create(cr, parent, "application/octet-stream", name)
        cr.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("לא ניתן לכתוב $name")
    }

    fun readText(cr: ContentResolver, uri: Uri): String =
        cr.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("לא ניתן לקרוא קובץ")

    fun children(cr: ContentResolver, tree: Uri, parent: Uri): List<Child> {
        val parentId = DocumentsContract.getDocumentId(parent)
        val query = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val out = mutableListOf<Child>()
        cr.query(
            query,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                out += Child(
                    uri = DocumentsContract.buildDocumentUriUsingTree(tree, id),
                    name = c.getString(1) ?: "",
                    isDir = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                    size = if (c.isNull(3)) 0L else c.getLong(3),
                )
            }
        }
        return out
    }

    fun child(cr: ContentResolver, tree: Uri, parent: Uri, name: String): Child? =
        children(cr, tree, parent).firstOrNull { it.name == name }
}
