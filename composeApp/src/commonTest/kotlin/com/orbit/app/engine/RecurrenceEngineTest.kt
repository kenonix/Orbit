package com.orbit.app.engine

import com.orbit.app.model.RecurrenceFrequency
import com.orbit.app.model.RecurrenceRule
import com.orbit.app.model.Schedule
import kotlinx.datetime.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RecurrenceEngineTest {

    private val timeZone = TimeZone.UTC

    @Test
    fun testDailyRecurrenceOccurrences() {
        val startInstant = Instant.parse("2026-06-01T10:00:00Z")
        val endInstant = Instant.parse("2026-06-01T11:00:00Z")
        
        val schedule = Schedule(
            id = "test-daily",
            title = "Daily Standup",
            startTime = startInstant,
            endTime = endInstant,
            recurrenceRule = RecurrenceRule(
                frequency = RecurrenceFrequency.DAILY,
                interval = 2, // Every 2 days
                count = 5     // 5 occurrences total: June 1, 3, 5, 7, 9
            )
        )

        // Generate within a window: June 2 to June 8
        val windowStart = Instant.parse("2026-06-02T00:00:00Z")
        val windowEnd = Instant.parse("2026-06-08T23:59:59Z")
        
        val occurrences = RecurrenceEngine.generateOccurrences(schedule, windowStart, windowEnd, timeZone)
        
        // Should contain: June 3 (index 1), June 5 (index 2), June 7 (index 3)
        assertEquals(3, occurrences.size)
        assertEquals(1, occurrences[0].occurrenceIndex)
        assertEquals(Instant.parse("2026-06-03T10:00:00Z"), occurrences[0].startTime)
        assertEquals(3, occurrences[2].occurrenceIndex)
        assertEquals(Instant.parse("2026-06-07T10:00:00Z"), occurrences[2].startTime)
    }

    @Test
    fun testReverseCalculationIndexMatch() {
        val startInstant = Instant.parse("2026-01-31T15:00:00Z")
        val endInstant = Instant.parse("2026-01-31T16:00:00Z")

        val schedule = Schedule(
            id = "test-monthly",
            title = "Monthly Report",
            startTime = startInstant,
            endTime = endInstant,
            recurrenceRule = RecurrenceRule(
                frequency = RecurrenceFrequency.MONTHLY,
                interval = 1
            )
        )

        // Test matching a valid occurrence (Feb 28 in 2026 since it's not a leap year, 31st caps to 28th)
        val targetFeb = Instant.parse("2026-02-28T15:00:00Z")
        val indexFeb = RecurrenceEngine.getOccurrenceIndex(schedule, targetFeb, timeZone)
        assertEquals(1, indexFeb)

        // Test matching an invalid date (e.g. Feb 27)
        val invalidFeb = Instant.parse("2026-02-27T15:00:00Z")
        assertNull(RecurrenceEngine.getOccurrenceIndex(schedule, invalidFeb, timeZone))

        // Test matching March 31st (index 2)
        val targetMarch = Instant.parse("2026-03-31T15:00:00Z")
        assertEquals(2, RecurrenceEngine.getOccurrenceIndex(schedule, targetMarch, timeZone))
    }

    @Test
    fun testQuarterlyRecurrence() {
        val startInstant = Instant.parse("2026-01-15T09:00:00Z")
        val endInstant = Instant.parse("2026-01-15T10:00:00Z")

        val schedule = Schedule(
            id = "test-quarterly",
            title = "Quarterly Review",
            startTime = startInstant,
            endTime = endInstant,
            recurrenceRule = RecurrenceRule(
                frequency = RecurrenceFrequency.QUARTERLY,
                interval = 1 // Every 1 quarter (3 months)
            )
        )

        // Occurrences: Jan 15, Apr 15, Jul 15, Oct 15
        val targetJul = Instant.parse("2026-07-15T09:00:00Z")
        assertEquals(2, RecurrenceEngine.getOccurrenceIndex(schedule, targetJul, timeZone))
        
        // Non-matching month
        val targetAug = Instant.parse("2026-08-15T09:00:00Z")
        assertNull(RecurrenceEngine.getOccurrenceIndex(schedule, targetAug, timeZone))
    }

    @Test
    fun testGetPreviousOccurrence() {
        val startInstant = Instant.parse("2026-06-01T08:00:00Z")
        val endInstant = Instant.parse("2026-06-01T09:00:00Z")

        val schedule = Schedule(
            id = "test-prev",
            title = "Daily Gym",
            startTime = startInstant,
            endTime = endInstant,
            recurrenceRule = RecurrenceRule(
                frequency = RecurrenceFrequency.DAILY,
                interval = 1
            )
        )

        // Find previous occurrence relative to June 4th 08:30:00
        val refTime = Instant.parse("2026-06-04T08:30:00Z")
        val prev = RecurrenceEngine.getPreviousOccurrence(schedule, refTime, timeZone)
        
        assertNotNull(prev)
        assertEquals(3, prev.occurrenceIndex) // June 1 (0), June 2 (1), June 3 (2), June 4 (3)
        assertEquals(Instant.parse("2026-06-04T08:00:00Z"), prev.startTime)
    }

    @Test
    fun testGetNextOccurrence() {
        val startInstant = Instant.parse("2026-06-01T12:00:00Z")
        val endInstant = Instant.parse("2026-06-01T13:00:00Z")

        val schedule = Schedule(
            id = "test-next",
            title = "Weekly Meet",
            startTime = startInstant,
            endTime = endInstant,
            recurrenceRule = RecurrenceRule(
                frequency = RecurrenceFrequency.DAILY,
                interval = 7
            )
        )

        // Relative to June 3rd, next should be June 8th (index 1)
        val refTime = Instant.parse("2026-06-03T00:00:00Z")
        val next = RecurrenceEngine.getNextOccurrence(schedule, refTime, timeZone)
        
        assertNotNull(next)
        assertEquals(1, next.occurrenceIndex)
        assertEquals(Instant.parse("2026-06-08T12:00:00Z"), next.startTime)
    }
}
