package dev.connor.stream

import android.Manifest
import android.content.Context
import android.content.Intent
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.NoMatchingViewException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.POST_NOTIFICATIONS
    )

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        TestUtils.clearNotes(context)
    }

    @Test
    fun broadcastUpdated_refreshesList() {
        TestUtils.writeNotes(context, listOf(1L to "First note"))

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.notesRecyclerView))
                .check(RecyclerViewItemCountAssertion(1))

            TestUtils.writeNotes(
                context,
                listOf(1L to "First note", 2L to "Second note")
            )
            context.sendBroadcast(Intent(NoteForegroundService.ACTION_UPDATED))

            onView(withId(R.id.notesRecyclerView))
                .check(RecyclerViewItemCountAssertion(2))
        }
    }

    @Test
    fun appendedJsonlNoteRendersAfterBroadcast() {
        TestUtils.writeNotes(context, listOf(1L to "Existing note"))

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.notesRecyclerView))
                .check(RecyclerViewItemCountAssertion(1))

            TestUtils.appendNote(context, 2L, "Appended note")
            context.sendBroadcast(Intent(NoteForegroundService.ACTION_UPDATED))

            onView(withText("Appended note"))
                .check(matches(isDisplayed()))
            onView(withId(R.id.notesRecyclerView))
                .check(RecyclerViewItemCountAssertion(2))
        }
    }

    @Test
    fun jsonlNoteRendersOnLaunch() {
        TestUtils.writeNotes(context, listOf(2L to "Render this"))

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText("Render this"))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun longPressDeletesNoteAndRewritesFile() {
        TestUtils.writeNotes(
            context,
            listOf(1L to "Delete me", 2L to "Keep me")
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withText("Delete me"))
                .perform(longClick())

            onView(withId(R.id.notesRecyclerView))
                .check(RecyclerViewItemCountAssertion(1))

            scenario.onActivity {
                val lines = TestUtils.readNoteLines(context)
                assertEquals(1, lines.size)
                assertTrue(lines.first().contains("Keep me"))
            }
        }
    }

    @Test
    fun longPressDeletesLastNoteClearsFile() {
        TestUtils.writeNotes(context, listOf(1L to "Only note"))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withText("Only note"))
                .perform(longClick())

            onView(withId(R.id.notesRecyclerView))
                .check(RecyclerViewItemCountAssertion(0))

            scenario.onActivity {
                val lines = TestUtils.readNoteLines(context)
                assertTrue(lines.isEmpty())
            }
        }
    }

    @Test
    fun onResumeReloadsNotesFromFile() {
        TestUtils.writeNotes(context, listOf(1L to "Initial"))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.notesRecyclerView))
                .check(RecyclerViewItemCountAssertion(1))

            scenario.moveToState(Lifecycle.State.STARTED)
            TestUtils.writeNotes(context, listOf(1L to "Initial", 2L to "Resumed"))
            scenario.moveToState(Lifecycle.State.RESUMED)

            onView(withId(R.id.notesRecyclerView))
                .check(RecyclerViewItemCountAssertion(2))
        }
    }

    @Test
    fun ignoresInvalidJsonLines() {
        val file = context.getFileStreamPath("notes.txt")
        file.writeText("not-json\n{\"ts\":3,\"text\":\"Valid\"}\n")

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.notesRecyclerView))
                .check(RecyclerViewItemCountAssertion(1))
            onView(withText("Valid"))
                .check(matches(isDisplayed()))
        }
    }
}

class RecyclerViewItemCountAssertion(private val expectedCount: Int) : ViewAssertion {
    override fun check(view: View?, noViewFoundException: NoMatchingViewException?) {
        if (noViewFoundException != null) {
            throw noViewFoundException
        }
        val recyclerView = view as RecyclerView
        val actualCount = recyclerView.adapter?.itemCount ?: 0
        assertEquals(expectedCount, actualCount)
    }
}
