package dev.connor.stream

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import org.json.JSONObject
import java.io.File

class NoteForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "notes_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.connor.notification.action.START"
        const val ACTION_REPLY = "com.connor.notification.action.REPLY"
        const val ACTION_REFRESH = "com.connor.notification.action.REFRESH"
        const val ACTION_UPDATED = "com.connor.notification.ACTION_UPDATED"
        const val KEY_TEXT_REPLY = "key_text_reply"
        private const val NOTES_FILE = "notes.txt"
    }

    private val messages = mutableListOf<NoteMessage>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        loadMessagesFromFile()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REPLY -> handleReply(intent)
            ACTION_REFRESH -> {
                loadMessagesFromFile()
                updateNotification()
            }
            ACTION_START, null -> updateNotification()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    private fun handleReply(intent: Intent) {
        // Notification action handling for inline reply.
        val input = RemoteInput.getResultsFromIntent(intent)
        val replyText = input?.getCharSequence(KEY_TEXT_REPLY)?.toString()?.trim()
        if (!replyText.isNullOrEmpty()) {
            appendMessage(replyText)
            sendBroadcast(
                Intent(ACTION_UPDATED)
                    .setPackage(packageName)
            )
        }
        updateNotification()
    }

    private fun appendMessage(text: String) {
        val timestamp = System.currentTimeMillis()
        messages.add(NoteMessage(text, timestamp))
        // File I/O: append new note to internal storage.
        openFileOutput(NOTES_FILE, MODE_APPEND).bufferedWriter().use { writer ->
            val jsonLine = JSONObject()
                .put("ts", timestamp)
                .put("text", text)
                .toString()
            writer.append(jsonLine).append("\n")
        }
    }

    private fun loadMessagesFromFile() {
        val file = File(filesDir, NOTES_FILE)
        messages.clear()
        if (!file.exists()) return

        file.readLines().forEach { line ->
            try {
                val json = JSONObject(line)
                messages.add(NoteMessage(json.getString("text"), json.getLong("ts")))
            } catch (exception: Exception) {
                return@forEach
            }
        }
    }

    private fun updateNotification() {
        loadMessagesFromFile()
        NotificationManagerCompat.from(this)
            .notify(NOTIFICATION_ID, buildNotification())
    }


    private fun buildNotification(): android.app.Notification {
        val youPerson = Person.Builder().setName("You").build()
        val messagingStyle = NotificationCompat.MessagingStyle(youPerson)
            .setConversationTitle("Capture Stream")

        messages.takeLast(10).forEach { message ->
            messagingStyle.addMessage(message.text, message.timestamp, youPerson)
        }

        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("Type a note")
            .build()

        val replyIntent = Intent(this, NoteForegroundService::class.java).apply {
            action = ACTION_REPLY
        }

        val replyPendingIntent = PendingIntent.getService(
            this,
            0,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_input_add,
            "Add Note",
            replyPendingIntent
        ).addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        val latestText = messages.lastOrNull()?.text ?: "Capture notes from notifications"

        val contentIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Capture Stream")
            .setContentText(latestText)
            .setContentIntent(contentPendingIntent)
            .setStyle(messagingStyle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(replyAction)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Note Capture",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Foreground note capture"
        }
        manager.createNotificationChannel(channel)
    }
}

data class NoteMessage(val text: String, val timestamp: Long)
