package dev.connor.stream

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.File

class MainActivity : ComponentActivity() {
    private val notesUpdatedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            val notes = loadNotesFromFile()
            notesAdapter.setNotes(notes)
            notesAdapter.notifyDataSetChanged()
            scrollToBottom()
        }
    }
    private lateinit var notesAdapter: NotesAdapter
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.notesRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        notesAdapter = NotesAdapter(
            onLongPress = { position ->
                val updatedNotes = loadNotesFromFile().toMutableList()
                if (position in updatedNotes.indices) {
                    val note = updatedNotes[position]
                    showEditDialog(note.text) { newText ->
                        updatedNotes[position] = NoteEntry(note.ts, newText)
                        overwriteNotes(updatedNotes)
                        refreshServiceAndList(updatedNotes)
                    }
                }
            },
            onDelete = { position ->
                val updatedNotes = loadNotesFromFile().toMutableList()
                if (position in updatedNotes.indices) {
                    updatedNotes.removeAt(position)
                    overwriteNotes(updatedNotes)
                    refreshServiceAndList(updatedNotes)
                }
            }
        )
        recyclerView.adapter = notesAdapter

        val startButton = findViewById<Button>(R.id.startCaptureButton)
        val stopButton = findViewById<Button>(R.id.stopCaptureButton)
        val addButton = findViewById<Button>(R.id.addNoteButton)
        val viewLogButton = findViewById<Button>(R.id.viewLogButton)
        val noteInput = findViewById<EditText>(R.id.noteInput)

        startButton.setOnClickListener {
            startForegroundService(Intent(this, NoteForegroundService::class.java))
        }
        stopButton.setOnClickListener {
            stopService(Intent(this, NoteForegroundService::class.java))
        }

        addButton.setOnClickListener {
            val text = noteInput.text.toString().trim()
            if (text.isNotEmpty()) {
                val note = NoteEntry(System.currentTimeMillis(), text)
                appendNoteToFile(note)
                noteInput.text?.clear()
                val notes = loadNotesFromFile()
                notesAdapter.setNotes(notes)
                notesAdapter.notifyDataSetChanged()
                scrollToBottom()
                Intent(this, NoteForegroundService::class.java).also { intent ->
                    intent.action = NoteForegroundService.ACTION_REFRESH
                    startService(intent)
                }
            }
        }

        viewLogButton.setOnClickListener {
            val notes = loadNotesFromFile()
            notesAdapter.setNotes(notes)
            notesAdapter.notifyDataSetChanged()
            scrollToBottom()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        } else {
            startService()
        }
    }

    override fun onResume() {
        super.onResume()
        val notes = loadNotesFromFile()
        notesAdapter.setNotes(notes)
        notesAdapter.notifyDataSetChanged()
        scrollToBottom()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            notesUpdatedReceiver,
            IntentFilter(NoteForegroundService.ACTION_UPDATED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(notesUpdatedReceiver)
    }

    private fun loadNotesFromFile(): List<NoteEntry> {
        val file = File(filesDir, "notes.txt")
        if (!file.exists()) {
            return emptyList<NoteEntry>()
        }
        return file.readLines().mapNotNull { line ->
            try {
                val json = JSONObject(line)
                NoteEntry(json.getLong("ts"), json.getString("text"))
            } catch (exception: Exception) {
                null
            }
        }
    }

    private fun overwriteNotes(notes: List<NoteEntry>) {
        val file = File(filesDir, "notes.txt")
        val content = if (notes.isEmpty()) {
            ""
        } else {
            notes.joinToString("\n") { note ->
                note.toJsonLine()
            } + "\n"
        }
        file.writeText(content)
    }

    private fun appendNoteToFile(note: NoteEntry) {
        openFileOutput("notes.txt", MODE_APPEND).bufferedWriter().use { writer ->
            writer.append(note.toJsonLine()).append("\n")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            startService()
        }
    }

    private fun startService() {
        val startIntent = Intent(this, NoteForegroundService::class.java).apply {
            action = NoteForegroundService.ACTION_START
        }
        ContextCompat.startForegroundService(this, startIntent)
    }

    private fun refreshServiceAndList(updatedNotes: List<NoteEntry>) {
        Intent(this, NoteForegroundService::class.java).also { intent ->
            intent.action = NoteForegroundService.ACTION_REFRESH
            startService(intent)
        }
        notesAdapter.setNotes(updatedNotes)
        notesAdapter.notifyDataSetChanged()
        scrollToBottom()
    }

    private fun scrollToBottom() {
        val lastIndex = notesAdapter.itemCount - 1
        if (lastIndex >= 0) {
            recyclerView.post {
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                if (layoutManager != null) {
                    layoutManager.scrollToPositionWithOffset(lastIndex, 0)
                } else {
                    recyclerView.scrollToPosition(lastIndex)
                }
            }
        }
    }

    private fun showEditDialog(currentText: String, onSave: (String) -> Unit) {
        val input = EditText(this).apply {
            setText(currentText)
            setSelection(text.length)
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("Edit note")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val updated = input.text.toString().trim()
                if (updated.isNotEmpty()) {
                    onSave(updated)
                }
            }
            .show()
    }
}
