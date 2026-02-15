package dev.connor.stream

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.test.rule.ServiceTestRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteForegroundServiceTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.POST_NOTIFICATIONS
    )

    @get:Rule
    val serviceRule = ServiceTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        TestUtils.clearNotes(context)
    }

    @Test
    fun actionReplyAppendsNoteAndUpdatesNotification() {
        val startIntent = Intent(context, NoteForegroundService::class.java).apply {
            action = NoteForegroundService.ACTION_START
        }
        serviceRule.startService(startIntent)

        val replyIntent = Intent(context, NoteForegroundService::class.java).apply {
            action = NoteForegroundService.ACTION_REPLY
        }
        val remoteInput = RemoteInput.Builder(NoteForegroundService.KEY_TEXT_REPLY).build()
        val bundle = Bundle().apply {
            putCharSequence(NoteForegroundService.KEY_TEXT_REPLY, "Service note")
        }
        RemoteInput.addResultsToIntent(arrayOf(remoteInput), replyIntent, bundle)
        serviceRule.startService(replyIntent)

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        val lines = TestUtils.readNoteLines(context)
        assertEquals(1, lines.size)
        assertTrue(lines.first().contains("Service note"))

        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = manager.activeNotifications
            .firstOrNull { it.id == NoteForegroundService.NOTIFICATION_ID }
            ?.notification
        assertNotNull(notification)

        val style = NotificationCompat.MessagingStyle
            .extractMessagingStyleFromNotification(notification!!)
        assertNotNull(style)

        val messages = style!!.messages
        assertTrue(messages.isNotEmpty())
        assertEquals("Service note", messages.last().text?.toString())
    }

    @Test
    fun actionReplyPreservesExistingNotes() {
        TestUtils.writeNotes(
            context,
            listOf(1L to "Existing one", 2L to "Existing two")
        )

        val startIntent = Intent(context, NoteForegroundService::class.java).apply {
            action = NoteForegroundService.ACTION_START
        }
        serviceRule.startService(startIntent)

        val replyIntent = Intent(context, NoteForegroundService::class.java).apply {
            action = NoteForegroundService.ACTION_REPLY
        }
        val remoteInput = RemoteInput.Builder(NoteForegroundService.KEY_TEXT_REPLY).build()
        val bundle = Bundle().apply {
            putCharSequence(NoteForegroundService.KEY_TEXT_REPLY, "Added note")
        }
        RemoteInput.addResultsToIntent(arrayOf(remoteInput), replyIntent, bundle)
        serviceRule.startService(replyIntent)

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        val lines = TestUtils.readNoteLines(context)
        assertEquals(3, lines.size)
        assertTrue(lines.first().contains("Existing one"))
        assertTrue(lines[1].contains("Existing two"))
        assertTrue(lines.last().contains("Added note"))
    }

    @Test
    fun actionRefreshLoadsLatestNotes() {
        TestUtils.writeNotes(context, listOf(1L to "Original"))

        val startIntent = Intent(context, NoteForegroundService::class.java).apply {
            action = NoteForegroundService.ACTION_START
        }
        serviceRule.startService(startIntent)

        TestUtils.writeNotes(context, listOf(1L to "Original", 2L to "Refreshed"))
        val refreshIntent = Intent(context, NoteForegroundService::class.java).apply {
            action = NoteForegroundService.ACTION_REFRESH
        }
        serviceRule.startService(refreshIntent)

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        val latestMessage = latestNotificationMessage()
        assertEquals("Refreshed", latestMessage)
    }

    private fun latestNotificationMessage(): String? {
        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = manager.activeNotifications
            .firstOrNull { it.id == NoteForegroundService.NOTIFICATION_ID }
            ?.notification
            ?: return null
        val style = NotificationCompat.MessagingStyle
            .extractMessagingStyleFromNotification(notification)
            ?: return null
        return style.messages.lastOrNull()?.text?.toString()
    }
}
