package com.orbit.app.engine

import com.orbit.app.model.RecurrenceFrequency
import com.orbit.app.model.Schedule
import com.orbit.app.model.ScheduleOccurrence
import kotlinx.datetime.*

object RecurrenceEngine {

    /**
     * Generates all occurrences of a [Schedule] within a given time window [windowStart, windowEnd].
     */
    fun generateOccurrences(
        schedule: Schedule,
        windowStart: Instant,
        windowEnd: Instant,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): List<ScheduleOccurrence> {
        val occurrences = mutableListOf<ScheduleOccurrence>()
        val rule = schedule.recurrenceRule

        val duration = schedule.endTime - schedule.startTime

        if (rule == null) {
            // Single instance
            if (schedule.startTime <= windowEnd && schedule.endTime >= windowStart) {
                occurrences.add(ScheduleOccurrence(schedule, 0, schedule.startTime, schedule.endTime))
            }
            return occurrences
        }

        val startLdt = schedule.startTime.toLocalDateTime(timeZone)
        val startDate = startLdt.date
        val startLocalTime = startLdt.time

        var index = 0
        while (true) {
            // Check count condition
            if (rule.count != null && index >= rule.count) {
                break
            }

            val occDate = calculateOccurrenceDate(startDate, rule.frequency, rule.interval, index)
            val occLdt = LocalDateTime(occDate, startLocalTime)
            val occStart = occLdt.toInstant(timeZone)
            val occEnd = occStart + duration

            // Check until condition
            if (rule.until != null && occStart > rule.until) {
                break
            }

            // If we have gone past the window, we can stop generating
            if (occStart > windowEnd) {
                break
            }

            // Add if it falls within the window
            if (occStart <= windowEnd && occEnd >= windowStart) {
                occurrences.add(ScheduleOccurrence(schedule, index, occStart, occEnd))
            }

            index++
        }

        return occurrences
    }

    /**
     * REVERSE CALCULATION:
     * Given a target start time [targetStart], checks if it represents a valid occurrence
     * of the [Schedule] and returns its occurrence index. Returns null if it doesn't match.
     */
    fun getOccurrenceIndex(
        schedule: Schedule,
        targetStart: Instant,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Int? {
        if (targetStart < schedule.startTime) return null

        val rule = schedule.recurrenceRule
        if (rule == null) {
            return if (targetStart == schedule.startTime) 0 else null
        }

        val startLdt = schedule.startTime.toLocalDateTime(timeZone)
        val targetLdt = targetStart.toLocalDateTime(timeZone)

        // Check if time matches (ignoring timezone DST shifts for exact matching)
        if (startLdt.time != targetLdt.time) {
            // Verify if it maps to the same instant when converted back in this timezone
            // (in case of local offset shifts, we check the exact instant match)
            val expectedStart = LocalDateTime(targetLdt.date, startLdt.time).toInstant(timeZone)
            if (expectedStart != targetStart) {
                return null
            }
        }

        val index = calculateOccurrenceIndex(
            startDate = startLdt.date,
            targetDate = targetLdt.date,
            frequency = rule.frequency,
            interval = rule.interval
        ) ?: return null

        // Check constraints
        if (rule.count != null && index >= rule.count) return null
        if (rule.until != null && targetStart > rule.until) return null

        return index
    }

    /**
     * REVERSE CALCULATION:
     * Finds the previous occurrence of a [Schedule] relative to a reference time [relativeTo].
     * Returns null if there are no occurrences before [relativeTo].
     */
    fun getPreviousOccurrence(
        schedule: Schedule,
        relativeTo: Instant,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): ScheduleOccurrence? {
        if (relativeTo <= schedule.startTime) return null

        val rule = schedule.recurrenceRule
        val duration = schedule.endTime - schedule.startTime

        if (rule == null) {
            return ScheduleOccurrence(schedule, 0, schedule.startTime, schedule.endTime)
        }

        val startLdt = schedule.startTime.toLocalDateTime(timeZone)
        val relativeLdt = relativeTo.toLocalDateTime(timeZone)

        // Guess the index based on time difference
        val diffMonths = startLdt.date.monthsUntil(relativeLdt.date)
        val diffDays = startLdt.date.daysUntil(relativeLdt.date)

        val estimatedIndex = when (rule.frequency) {
            RecurrenceFrequency.DAILY -> diffDays / rule.interval
            RecurrenceFrequency.MONTHLY -> diffMonths / rule.interval
            RecurrenceFrequency.QUARTERLY -> diffMonths / (rule.interval * 3)
            RecurrenceFrequency.SEMI_ANNUALLY -> diffMonths / (rule.interval * 6)
            RecurrenceFrequency.ANNUALLY -> diffMonths / (rule.interval * 12)
        }

        // Search backward starting from estimatedIndex
        var index = estimatedIndex
        if (rule.count != null && index >= rule.count) {
            index = rule.count - 1
        }

        while (index >= 0) {
            val occDate = calculateOccurrenceDate(startLdt.date, rule.frequency, rule.interval, index)
            val occLdt = LocalDateTime(occDate, startLdt.time)
            val occStart = occLdt.toInstant(timeZone)
            val occEnd = occStart + duration

            if (occStart < relativeTo) {
                // Verify 'until' constraint
                if (rule.until == null || occStart <= rule.until) {
                    return ScheduleOccurrence(schedule, index, occStart, occEnd)
                }
            }
            index--
        }

        return null
    }

    /**
     * Finds the next occurrence of a [Schedule] relative to a reference time [relativeTo].
     */
    fun getNextOccurrence(
        schedule: Schedule,
        relativeTo: Instant,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): ScheduleOccurrence? {
        val rule = schedule.recurrenceRule
        val duration = schedule.endTime - schedule.startTime

        if (rule == null) {
            return if (schedule.startTime >= relativeTo) {
                ScheduleOccurrence(schedule, 0, schedule.startTime, schedule.endTime)
            } else {
                null
            }
        }

        val startLdt = schedule.startTime.toLocalDateTime(timeZone)
        val relativeLdt = relativeTo.toLocalDateTime(timeZone)

        val diffMonths = startLdt.date.monthsUntil(relativeLdt.date)
        val diffDays = startLdt.date.daysUntil(relativeLdt.date)

        val estimatedIndex = when (rule.frequency) {
            RecurrenceFrequency.DAILY -> diffDays / rule.interval
            RecurrenceFrequency.MONTHLY -> diffMonths / rule.interval
            RecurrenceFrequency.QUARTERLY -> diffMonths / (rule.interval * 3)
            RecurrenceFrequency.SEMI_ANNUALLY -> diffMonths / (rule.interval * 6)
            RecurrenceFrequency.ANNUALLY -> diffMonths / (rule.interval * 12)
        }.coerceAtLeast(0)

        var index = estimatedIndex
        while (true) {
            if (rule.count != null && index >= rule.count) {
                break
            }

            val occDate = calculateOccurrenceDate(startLdt.date, rule.frequency, rule.interval, index)
            val occLdt = LocalDateTime(occDate, startLdt.time)
            val occStart = occLdt.toInstant(timeZone)
            val occEnd = occStart + duration

            if (rule.until != null && occStart > rule.until) {
                break
            }

            if (occStart >= relativeTo) {
                return ScheduleOccurrence(schedule, index, occStart, occEnd)
            }
            index++
        }

        return null
    }

    private fun calculateOccurrenceDate(
        startDate: LocalDate,
        frequency: RecurrenceFrequency,
        interval: Int,
        index: Int
    ): LocalDate {
        val totalUnits = index * interval
        return when (frequency) {
            RecurrenceFrequency.DAILY -> startDate.plus(totalUnits, DateTimeUnit.DAY)
            RecurrenceFrequency.MONTHLY -> startDate.plus(totalUnits, DateTimeUnit.MONTH)
            RecurrenceFrequency.QUARTERLY -> startDate.plus(totalUnits * 3, DateTimeUnit.MONTH)
            RecurrenceFrequency.SEMI_ANNUALLY -> startDate.plus(totalUnits * 6, DateTimeUnit.MONTH)
            RecurrenceFrequency.ANNUALLY -> startDate.plus(totalUnits, DateTimeUnit.YEAR)
        }
    }

    private fun calculateOccurrenceIndex(
        startDate: LocalDate,
        targetDate: LocalDate,
        frequency: RecurrenceFrequency,
        interval: Int
    ): Int? {
        if (targetDate < startDate) return null

        val diffMonths = startDate.monthsUntil(targetDate)
        val diffDays = startDate.daysUntil(targetDate)

        val index = when (frequency) {
            RecurrenceFrequency.DAILY -> {
                if (diffDays % interval != 0) return null
                diffDays / interval
            }
            RecurrenceFrequency.MONTHLY -> {
                if (diffMonths % interval != 0) return null
                diffMonths / interval
            }
            RecurrenceFrequency.QUARTERLY -> {
                val step = interval * 3
                if (diffMonths % step != 0) return null
                diffMonths / step
            }
            RecurrenceFrequency.SEMI_ANNUALLY -> {
                val step = interval * 6
                if (diffMonths % step != 0) return null
                diffMonths / step
            }
            RecurrenceFrequency.ANNUALLY -> {
                val diffYears = startDate.yearsUntil(targetDate)
                if (diffYears % interval != 0) return null
                diffYears / interval
            }
        }

        val calculatedDate = calculateOccurrenceDate(startDate, frequency, interval, index)
        if (calculatedDate != targetDate) return null

        return index
    }

    /**
     * Aggregates schedules by Day, Month, Quarter, Semi-annually, and Year.
     */
    fun calculateAggregates(
        schedules: List<Schedule>,
        reference: Instant,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Map<String, PeriodAggregate> {
        val ldt = reference.toLocalDateTime(timeZone)
        val year = ldt.year
        val month = ldt.monthNumber
        val day = ldt.dayOfMonth

        // Day range
        val startDay = LocalDateTime(year, month, day, 0, 0, 0, 0).toInstant(timeZone)
        val endDay = LocalDateTime(year, month, day, 23, 59, 59, 999_999_999).toInstant(timeZone)

        // Month range
        val startMonth = LocalDateTime(year, month, 1, 0, 0, 0, 0).toInstant(timeZone)
        val nextMonthYear = if (month == 12) year + 1 else year
        val nextMonthVal = if (month == 12) 1 else month + 1
        val lastDayOfMonth = LocalDate(nextMonthYear, nextMonthVal, 1).minus(1, DateTimeUnit.DAY)
        val endMonth = LocalDateTime(lastDayOfMonth.year, lastDayOfMonth.monthNumber, lastDayOfMonth.dayOfMonth, 23, 59, 59, 999_999_999).toInstant(timeZone)

        // Quarter range
        val startQMonth = when (month) {
            in 1..3 -> 1
            in 4..6 -> 4
            in 7..9 -> 7
            else -> 10
        }
        val endQMonth = startQMonth + 2
        val startQuarter = LocalDateTime(year, startQMonth, 1, 0, 0, 0, 0).toInstant(timeZone)
        val nextQYear = if (endQMonth == 12) year + 1 else year
        val nextQMonth = if (endQMonth == 12) 1 else endQMonth + 1
        val lastDayOfQuarter = LocalDate(nextQYear, nextQMonth, 1).minus(1, DateTimeUnit.DAY)
        val endQuarter = LocalDateTime(lastDayOfQuarter.year, lastDayOfQuarter.monthNumber, lastDayOfQuarter.dayOfMonth, 23, 59, 59, 999_999_999).toInstant(timeZone)

        // Semi-annually range
        val startSMonth = if (month <= 6) 1 else 7
        val endSMonth = if (month <= 6) 6 else 12
        val startSemi = LocalDateTime(year, startSMonth, 1, 0, 0, 0, 0).toInstant(timeZone)
        val nextSYear = if (endSMonth == 12) year + 1 else year
        val nextSMonth = if (endSMonth == 12) 1 else endSMonth + 1
        val lastDayOfSemi = LocalDate(nextSYear, nextSMonth, 1).minus(1, DateTimeUnit.DAY)
        val endSemi = LocalDateTime(lastDayOfSemi.year, lastDayOfSemi.monthNumber, lastDayOfSemi.dayOfMonth, 23, 59, 59, 999_999_999).toInstant(timeZone)

        // Year range
        val startYear = LocalDateTime(year, 1, 1, 0, 0, 0, 0).toInstant(timeZone)
        val endYear = LocalDateTime(year, 12, 31, 23, 59, 59, 999_999_999).toInstant(timeZone)

        fun getAggregate(start: Instant, end: Instant): PeriodAggregate {
            var count = 0
            var totalMinutes = 0L
            for (s in schedules) {
                val occurrences = generateOccurrences(s, start, end, timeZone)
                count += occurrences.size
                for (occ in occurrences) {
                    val duration = occ.endTime - occ.startTime
                    totalMinutes += duration.inWholeMinutes
                }
            }
            return PeriodAggregate(count, totalMinutes)
        }

        return mapOf(
            "day" to getAggregate(startDay, endDay),
            "month" to getAggregate(startMonth, endMonth),
            "quarter" to getAggregate(startQuarter, endQuarter),
            "semi" to getAggregate(startSemi, endSemi),
            "year" to getAggregate(startYear, endYear)
        )
    }
}

data class PeriodAggregate(
    val count: Int,
    val totalDurationMinutes: Long
)

