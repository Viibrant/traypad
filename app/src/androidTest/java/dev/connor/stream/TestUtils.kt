package dev.connor.stream

import android.content.Context
import org.json.JSONObject
import java.io.File

object TestUtils {
    private const val NOTES_FILE = "notes.txt"

    fun clearNotes(context: Context) {
        val file = File(context.filesDir, NOTES_FILE)
        if (file.exists()) {
            file.delete()
        }
    }

    fun writeNotes(context: Context, notes: List<Pair<Long, String>>) {
        val file = File(context.filesDir, NOTES_FILE)
        if (notes.isEmpty()) {
            file.writeText("")
            return
        }
        val content = notes.joinToString("\n") { (timestamp, text) ->
            JSONObject()
                .put("ts", timestamp)
                .put("text", text)
                .toString()
        } + "\n"
        file.writeText(content)
    }

    fun readNoteLines(context: Context): List<String> {
        val file = File(context.filesDir, NOTES_FILE)
        if (!file.exists()) {
            return emptyList()
        }
        return file.readLines()
    }
}
