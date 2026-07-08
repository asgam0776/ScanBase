package com.scanbase.app.document

import android.content.Context
import com.scanbase.app.data.DocumentRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class DocumentRecordStore(context: Context) {
    private val storeFile = File(context.filesDir, "document_records.json")

    fun load(): List<DocumentRecord> {
        if (!storeFile.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(storeFile.readText())
            val parsedRecords = buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(item.toDocumentRecord())
                }
            }
            val existingRecords = parsedRecords.filter { File(it.pdfPath).exists() }
            if (existingRecords.size != parsedRecords.size) {
                save(existingRecords)
            }
            existingRecords
        }.getOrDefault(emptyList())
    }

    fun add(record: DocumentRecord): List<DocumentRecord> {
        val next = (listOf(record) + load().filterNot { it.id == record.id })
            .distinctBy { it.pdfPath }
        save(next)
        return next
    }

    fun delete(recordId: String): DeleteResult {
        val records = load()
        val target = records.firstOrNull { it.id == recordId } ?: return DeleteResult.NotFound
        val file = File(target.pdfPath)
        if (file.exists() && !file.delete()) {
            return DeleteResult.FileDeleteFailed
        }
        save(records.filterNot { it.id == recordId })
        return DeleteResult.Deleted
    }

    private fun save(records: List<DocumentRecord>) {
        val array = JSONArray()
        records.forEach { record -> array.put(record.toJson()) }
        storeFile.parentFile?.mkdirs()
        storeFile.writeText(array.toString())
    }

    private fun DocumentRecord.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("title", title)
            .put("pdfPath", pdfPath)
            .put("createdAt", createdAt)
            .put("updatedAt", updatedAt)
            .put("pageCount", pageCount)
            .put("fileSizeBytes", fileSizeBytes)
    }

    private fun JSONObject.toDocumentRecord(): DocumentRecord {
        return DocumentRecord(
            id = getString("id"),
            title = getString("title"),
            pdfPath = getString("pdfPath"),
            createdAt = getLong("createdAt"),
            updatedAt = getLong("updatedAt"),
            pageCount = getInt("pageCount"),
            fileSizeBytes = getLong("fileSizeBytes")
        )
    }
}

enum class DeleteResult {
    Deleted,
    NotFound,
    FileDeleteFailed
}

