package dev.connor.stream

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

class NotesAdapter(
    private val onLongPress: (Int) -> Unit,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<NotesAdapter.NoteViewHolder>() {
    private val notes = mutableListOf<NoteEntry>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_note, parent, false)
        return NoteViewHolder(
            view,
            view.findViewById(R.id.noteTimestamp),
            view.findViewById(R.id.noteText),
            view.findViewById(R.id.noteDeleteButton)
        )
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = notes[position]
        holder.timestampView.text = DateFormat.getDateTimeInstance(
            DateFormat.SHORT,
            DateFormat.SHORT
        ).format(Date(note.ts))
        holder.textView.text = note.text
        holder.itemView.setOnLongClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                onLongPress(adapterPosition)
            }
            true
        }
        holder.deleteButton.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                onDelete(adapterPosition)
            }
        }
    }

    override fun getItemCount(): Int = notes.size

    fun setNotes(newNotes: List<NoteEntry>) {
        notes.clear()
        notes.addAll(newNotes)
    }

    class NoteViewHolder(
        itemView: android.view.View,
        val timestampView: TextView,
        val textView: TextView,
        val deleteButton: android.widget.Button
    ) : RecyclerView.ViewHolder(itemView)
}

data class NoteEntry(val ts: Long, val text: String) {
    fun toJsonLine(): String = JSONObject()
        .put("ts", ts)
        .put("text", text)
        .toString()
}
