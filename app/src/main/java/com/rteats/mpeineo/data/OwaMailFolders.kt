package com.rteats.mpeineo.data

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Folder IDs returned by Exchange belong to the current mailbox session. */
data class OwaMailFolder(
    val distinguishedId: String,
    val name: String,
    val title: String,
    val itemList: Boolean = false,
    val folderId: String? = null,
) {
    companion object {
        val INBOX = OwaMailFolder("inbox", "INBOX", "Входящие")
        val SENT = OwaMailFolder("sentitems", "SENT", "Отправленные")
        val DRAFTS = OwaMailFolder("drafts", "DRAFTS", "Черновики", true)
        val DELETED = OwaMailFolder("deleteditems", "DELETED", "Удалённые", true)
        val JUNK = OwaMailFolder("junkemail", "JUNK", "Спам", true)
        val NOTES = OwaMailFolder("notes", "NOTES", "Заметки", true)
        /** These two destinations are known from successful mailbox requests. */
        val defaults = listOf(INBOX, SENT)
    }
}

internal object OwaMailFolders {
    fun parseItems(
        response: JSONObject,
        validity: Long,
        stableId: (String) -> Long,
        parseDate: (String) -> Long,
    ): List<MailSummary> {
        val responses = response.optJSONObject("Body")?.optJSONObject("ResponseMessages")
            ?.optJSONArray("Items") ?: return emptyList()
        return buildList {
            for (i in 0 until responses.length()) {
                val rows = responses.optJSONObject(i)?.optJSONObject("RootFolder")
                    ?.optJSONArray("Items") ?: continue
                for (j in 0 until rows.length()) {
                    val item = rows.optJSONObject(j) ?: continue
                    val itemId = item.optJSONObject("ItemId")?.optString("Id").orEmpty()
                    if (itemId.isBlank()) continue
                    val sender = item.optJSONObject("From")?.optJSONObject("Mailbox")
                        ?: item.optJSONObject("Sender")?.optJSONObject("Mailbox")
                    add(MailSummary(
                        uid = stableId(itemId), uidValidity = validity,
                        sender = sender?.optString("Name")?.takeIf { it.isNotBlank() }
                            ?: sender?.optString("EmailAddress").orEmpty(),
                        subject = item.optString("Subject"),
                        sentAt = parseDate(item.optString("DateTimeReceived"))
                            .takeIf { it > 0 }
                            ?: parseDate(item.optString("DateTimeCreated")),
                        unread = !item.optBoolean("IsRead", true),
                        hasAttachment = item.optBoolean("HasAttachments"),
                        owaItemId = itemId,
                        recipients = item.optString("DisplayTo").takeIf { it.isNotBlank() }
                            ?.let(::listOf),
                        senderEmail = sender?.optString("EmailAddress")
                            ?.takeIf { it.contains('@') },
                    ))
                }
            }
        }
    }

    fun findFolderRequest(offset: Int = 0, includeHiddenFlag: Boolean = true): JSONObject = JSONObject()
        .put("__type", "FindFolderRequest:#Exchange")
        .put("Traversal", "Deep")
        .put("FolderShape", JSONObject()
            .put("__type", "FolderResponseShape:#Exchange")
            .put("BaseShape", "AllProperties")
            .apply {
                if (includeHiddenFlag) put("AdditionalProperties", JSONArray().put(
                    JSONObject().put("__type", "ExtendedPropertyUri:#Exchange")
                        .put("PropertyTag", "0x10f4")
                        .put("PropertyType", "Boolean")))
            })
        .put("ParentFolderIds", JSONArray().put(JSONObject()
            .put("__type", "DistinguishedFolderId:#Exchange")
            .put("Id", "msgfolderroot")))
        .put("Paging", JSONObject()
            .put("__type", "IndexedPageView:#Exchange")
            .put("BasePoint", "Beginning")
            .put("Offset", offset)
            .put("MaxEntriesReturned", 100))

    fun parseFolders(response: JSONObject): List<OwaMailFolder> {
        val items = response.optJSONObject("Body")?.optJSONObject("ResponseMessages")
            ?.optJSONArray("Items") ?: return emptyList()
        val seen = HashSet<String>()
        val internalNames = setOf("conversation action settings", "working set",
            "journal", "outbox", "исходящие", "search folders", "sync issues", "local failures",
            "server failures", "conflicts", "quick step settings")
        return buildList {
            for (i in 0 until items.length()) {
                val folders = items.optJSONObject(i)?.optJSONObject("RootFolder")
                    ?.optJSONArray("Folders") ?: continue
                for (j in 0 until folders.length()) {
                    val folder = folders.optJSONObject(j) ?: continue
                    val id = folder.optJSONObject("FolderId")?.optString("Id").orEmpty()
                    val title = folder.optString("DisplayName").trim()
                    val folderClass = folder.optString("FolderClass")
                    val folderType = folder.optString("__type")
                    val properties = folder.optJSONArray("ExtendedProperty")
                    val hiddenByTag = (0 until (properties?.length() ?: 0)).any { index ->
                        val property = properties?.optJSONObject(index) ?: return@any false
                        val field = property.optJSONObject("ExtendedFieldUri")
                            ?: property.optJSONObject("ExtendedFieldURI")
                        val tag = field?.optString("PropertyTag").orEmpty()
                        (tag.equals("0x10f4", true) || tag == "4340") &&
                            property.optString("Value").equals("true", true)
                    }
                    if (id.isBlank() || title.isBlank() || !seen.add(id) ||
                        folder.optBoolean("IsHidden", false) || hiddenByTag ||
                        title.lowercase() in internalNames ||
                        (folderClass.isNotBlank() && !folderClass.equals("IPF.Note", true)) ||
                        folderType.startsWith("CalendarFolder") ||
                        folderType.startsWith("ContactsFolder") ||
                        folderType.startsWith("TasksFolder"))
                        continue
                    val standard = when (title.lowercase()) {
                        "inbox", "входящие" -> OwaMailFolder.INBOX
                        "sent", "sent items", "sentitems", "отправленные" ->
                            OwaMailFolder.SENT
                        "drafts", "черновики" -> OwaMailFolder.DRAFTS
                        "deleted items", "deleteditems", "удаленные", "удалённые",
                            "корзина" -> OwaMailFolder.DELETED
                        "junk email", "junk e-mail", "junk mail", "junkemail",
                            "спам", "нежелательная почта" ->
                            OwaMailFolder.JUNK
                        "notes", "заметки" -> OwaMailFolder.NOTES
                        else -> null
                    }
                    if (standard != null) {
                        if (standard !in OwaMailFolder.defaults && standard !in this)
                            add(standard)
                        continue
                    }
                    val digest = MessageDigest.getInstance("SHA-256")
                        .digest(id.toByteArray(Charsets.UTF_8))
                        .take(8).joinToString("") { "%02x".format(it) }
                    add(OwaMailFolder("", "CUSTOM_$digest", title.take(80), folderId = id))
                }
            }
        }
    }

    fun findItemRequest(folder: OwaMailFolder, offset: Int, count: Int): JSONObject {
        val baseId = folder.folderId?.let {
            JSONObject().put("__type", "FolderId:#Exchange").put("Id", it)
        } ?: JSONObject().put("__type", "DistinguishedFolderId:#Exchange")
            .put("Id", folder.distinguishedId)
        return JSONObject()
            .put("__type", "FindItemRequest:#Exchange")
            .put("ItemShape", JSONObject().put("__type", "ItemResponseShape:#Exchange")
                .put("BaseShape", "IdOnly"))
            .put("ParentFolderIds", JSONArray().put(baseId))
            .put("Traversal", "Shallow")
            .put("Paging", JSONObject().put("__type", "IndexedPageView:#Exchange")
                .put("BasePoint", "Beginning")
                .put("Offset", offset.coerceIn(0, 100_000))
                .put("MaxEntriesReturned", count.coerceIn(1, 100)))
            .put("ViewFilter", "All")
            .put("IsWarmUpSearch", false)
            .put("ShapeName", "MailListItem")
            .put("SortOrder", JSONArray().put(JSONObject()
                .put("__type", "SortResults:#Exchange")
                .put("Order", "Descending")
                .put("Path", JSONObject().put("__type", "PropertyUri:#Exchange")
                    .put("FieldURI", if (folder == OwaMailFolder.DRAFTS)
                        "DateTimeCreated" else "DateTimeReceived"))))
    }
}
