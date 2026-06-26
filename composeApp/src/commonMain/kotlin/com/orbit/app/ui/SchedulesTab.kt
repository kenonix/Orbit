package com.orbit.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbit.app.model.*
import com.orbit.app.engine.*
import kotlinx.datetime.*

// ==========================================
// 📅 SCHEDULES TAB
// ==========================================
@Composable
fun SchedulesTab(
    schedules: List<Schedule>,
    lang: Language,
    mapData: OsmMapData?,
    spatialIndex: SpatialGridIndex?,
    onAddSchedule: (Schedule) -> Unit,
    onDeleteSchedule: (String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var activeViewMode by remember { mutableStateOf("month") } // "day", "month", "quarter", "semi", "year"
    var referenceDate by remember { mutableStateOf(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(Localization.get("schedules_title", lang), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(Localization.get("schedules_desc", lang), fontSize = 13.sp, color = TextSecondary)
            }
            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
                Spacer(modifier = Modifier.width(6.dp))
                Text(Localization.get("new_schedule", lang))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxSize()) {
            // Left Column (Interactive Calendar Workspace) - weight 2.0f
            Column(
                modifier = Modifier
                    .weight(2.0f)
                    .fillMaxHeight()
                    .widthIn(min = 420.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceCard)
                    .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                // Calendar Navigation & Toggle Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = {
                                referenceDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SlateDarkBg),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp).border(0.5.dp, BorderColor, RoundedCornerShape(4.dp))
                        ) {
                            Text(
                                text = when (lang) {
                                    Language.KOREAN -> "오늘"
                                    Language.JAPANESE -> "今日"
                                    Language.RUSSIAN -> "Сегодня"
                                    else -> "Today"
                                },
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        IconButton(
                            onClick = {
                                referenceDate = when (activeViewMode) {
                                    "day" -> referenceDate.minus(1, DateTimeUnit.DAY)
                                    "month" -> referenceDate.minus(1, DateTimeUnit.MONTH)
                                    "quarter" -> referenceDate.minus(3, DateTimeUnit.MONTH)
                                    "semi" -> referenceDate.minus(6, DateTimeUnit.MONTH)
                                    "year" -> referenceDate.minus(1, DateTimeUnit.YEAR)
                                    else -> referenceDate
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ChevronLeft, "Previous", tint = TextPrimary)
                        }
                        
                        IconButton(
                            onClick = {
                                referenceDate = when (activeViewMode) {
                                    "day" -> referenceDate.plus(1, DateTimeUnit.DAY)
                                    "month" -> referenceDate.plus(1, DateTimeUnit.MONTH)
                                    "quarter" -> referenceDate.plus(3, DateTimeUnit.MONTH)
                                    "semi" -> referenceDate.plus(6, DateTimeUnit.MONTH)
                                    "year" -> referenceDate.plus(1, DateTimeUnit.YEAR)
                                    else -> referenceDate
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ChevronRight, "Next", tint = TextPrimary)
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        val periodLabel = when (activeViewMode) {
                            "day" -> when (lang) {
                                Language.KOREAN -> "${referenceDate.year}년 ${referenceDate.monthNumber}월 ${referenceDate.dayOfMonth}일"
                                Language.JAPANESE -> "${referenceDate.year}年 ${referenceDate.monthNumber}월 ${referenceDate.dayOfMonth}일"
                                Language.RUSSIAN -> "${referenceDate.dayOfMonth}.${referenceDate.monthNumber}.${referenceDate.year}"
                                else -> "${referenceDate.year}-${referenceDate.monthNumber}-${referenceDate.dayOfMonth}"
                            }
                            "month" -> when (lang) {
                                Language.KOREAN -> "${referenceDate.year}년 ${referenceDate.monthNumber}월"
                                Language.JAPANESE -> "${referenceDate.year}年 ${referenceDate.monthNumber}月"
                                Language.RUSSIAN -> "${referenceDate.month.name} ${referenceDate.year}"
                                else -> "${referenceDate.year} - ${referenceDate.month.name}"
                            }
                            "quarter" -> {
                                val q = (referenceDate.monthNumber - 1) / 3 + 1
                                when (lang) {
                                    Language.KOREAN -> "${referenceDate.year}년 제 ${q}분기"
                                    Language.JAPANESE -> "${referenceDate.year}年 第${q}四半期"
                                    Language.RUSSIAN -> "${referenceDate.year} год - ${q}-й квартал"
                                    else -> "${referenceDate.year} Q$q"
                                }
                            }
                            "semi" -> {
                                val half = if (referenceDate.monthNumber <= 6) 1 else 2
                                when (lang) {
                                    Language.KOREAN -> "${referenceDate.year}년 ${if (half == 1) "상반기" else "하반기"}"
                                    Language.JAPANESE -> "${referenceDate.year}年 ${if (half == 1) "上半期" else "下半期"}"
                                    Language.RUSSIAN -> "${referenceDate.year} год - ${if (half == 1) "1-е полугодие" else "2-е полугодие"}"
                                    else -> "${referenceDate.year} ${if (half == 1) "1st Half" else "2nd Half"}"
                                }
                            }
                            "year" -> when (lang) {
                                Language.KOREAN -> "${referenceDate.year}년"
                                Language.JAPANESE -> "${referenceDate.year}年"
                                Language.RUSSIAN -> "${referenceDate.year} год"
                                else -> "${referenceDate.year}"
                            }
                            else -> ""
                        }
                        
                        Text(
                            text = periodLabel,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1
                        )
                    }
                    
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SlateDarkBg)
                            .border(0.5.dp, BorderColor, RoundedCornerShape(6.dp))
                            .padding(2.dp)
                    ) {
                        val modes = listOf(
                            "day" to when (lang) {
                                Language.KOREAN -> "일별"
                                Language.JAPANESE -> "日別"
                                Language.RUSSIAN -> "День"
                                else -> "Day"
                            },
                            "month" to when (lang) {
                                Language.KOREAN -> "월별"
                                Language.JAPANESE -> "月別"
                                Language.RUSSIAN -> "Месяц"
                                else -> "Month"
                            },
                            "quarter" to when (lang) {
                                Language.KOREAN -> "분기별"
                                Language.JAPANESE -> "四半期"
                                Language.RUSSIAN -> "Квартал"
                                else -> "Quarter"
                            },
                            "semi" to when (lang) {
                                Language.KOREAN -> "반기별"
                                Language.JAPANESE -> "半期"
                                Language.RUSSIAN -> "Полугодие"
                                else -> "Semi"
                            },
                            "year" to when (lang) {
                                Language.KOREAN -> "년별"
                                Language.JAPANESE -> "年別"
                                Language.RUSSIAN -> "Год"
                                else -> "Year"
                            }
                        )
                        
                        for ((m, label) in modes) {
                            val active = activeViewMode == m
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (active) AccentCyan else Color.Transparent)
                                    .clickable { activeViewMode = m }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) SlateDarkBg else TextSecondary
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Box(modifier = Modifier.weight(1.0f).fillMaxWidth()) {
                    when (activeViewMode) {
                        "day" -> DailyCalendarView(referenceDate, schedules, lang, onDeleteSchedule)
                        "month" -> MonthlyCalendarView(referenceDate, schedules, lang, onDeleteSchedule)
                        "quarter" -> QuarterlyCalendarView(referenceDate, schedules, lang, onDeleteSchedule)
                        "semi" -> SemiAnnualCalendarView(referenceDate, schedules, lang, onDeleteSchedule)
                        "year" -> YearlyCalendarView(referenceDate, schedules, lang, onDeleteSchedule)
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Right Column (All Schedules Master List Sidebar) - weight 1.0f
            Column(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxHeight()
                    .widthIn(min = 240.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceCard)
                    .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = when (lang) {
                        Language.KOREAN -> "전체 일정 마스터 리스트"
                        Language.JAPANESE -> "全スケジュール一覧"
                        Language.RUSSIAN -> "Все события"
                        else -> "All Schedules Master List"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentPurple,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderColor.copy(alpha = 0.3f)))
                Spacer(modifier = Modifier.height(12.dp))
                
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (schedules.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(Localization.get("no_schedules", lang), color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    } else {
                        items(schedules) { schedule ->
                            ScheduleListItem(schedule = schedule, onDelete = { onDeleteSchedule(schedule.id) })
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddScheduleDialog(
            lang = lang,
            mapData = mapData,
            spatialIndex = spatialIndex,
            onDismiss = { showAddDialog = false },
            onConfirm = { schedule ->
                onAddSchedule(schedule)
                showAddDialog = false
            }
        )
    }
}

fun getOccurrencesForWindow(
    schedules: List<Schedule>,
    start: Instant,
    end: Instant
): List<ScheduleOccurrence> {
    val list = mutableListOf<ScheduleOccurrence>()
    for (s in schedules) {
        list.addAll(RecurrenceEngine.generateOccurrences(s, start, end))
    }
    return list.sortedBy { it.startTime }
}

@Composable
fun DailyCalendarView(
    referenceDate: LocalDate,
    schedules: List<Schedule>,
    lang: Language,
    onDeleteSchedule: (String) -> Unit
) {
    val tz = TimeZone.currentSystemDefault()
    val startOfDay = LocalDateTime(referenceDate.year, referenceDate.monthNumber, referenceDate.dayOfMonth, 0, 0, 0, 0).toInstant(tz)
    val endOfDay = LocalDateTime(referenceDate.year, referenceDate.monthNumber, referenceDate.dayOfMonth, 23, 59, 59, 999_999_999).toInstant(tz)
    
    val occurrences = getOccurrencesForWindow(schedules, startOfDay, endOfDay)
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        items(24) { hour ->
            val hourStr = if (hour < 10) "0$hour:00" else "$hour:00"
            
            val hourStart = LocalDateTime(referenceDate.year, referenceDate.monthNumber, referenceDate.dayOfMonth, hour, 0, 0, 0).toInstant(tz)
            val hourEnd = LocalDateTime(referenceDate.year, referenceDate.monthNumber, referenceDate.dayOfMonth, hour, 59, 59, 999_999_999).toInstant(tz)
            
            val hourOccurrences = occurrences.filter { 
                it.startTime < hourEnd && it.endTime > hourStart 
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = hourStr,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.width(60.dp).padding(top = 4.dp),
                    fontFamily = FontFamily.Monospace
                )
                
                Column(
                    modifier = Modifier
                        .weight(1.0f)
                        .border(0.5.dp, BorderColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .background(if (hourOccurrences.isEmpty()) Color.Transparent else SurfaceCard.copy(alpha = 0.6f))
                        .padding(if (hourOccurrences.isEmpty()) 0.dp else 8.dp)
                        .heightIn(min = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (hourOccurrences.isEmpty()) {
                        Box(modifier = Modifier.padding(top = 20.dp).fillMaxWidth().height(1.dp).background(BorderColor.copy(alpha = 0.3f)))
                    } else {
                        for (occ in hourOccurrences) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = SlateDarkBg),
                                border = BorderStroke(1.dp, AccentPurple.copy(alpha = 0.8f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(occ.schedule.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text(
                                            "${occ.startTime.toLocalDateTime(tz).time.toString().take(5)} ~ ${occ.endTime.toLocalDateTime(tz).time.toString().take(5)}",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteSchedule(occ.schedule.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, "Delete", tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MonthlyCalendarView(
    referenceDate: LocalDate,
    schedules: List<Schedule>,
    lang: Language,
    onDeleteSchedule: (String) -> Unit
) {
    val tz = TimeZone.currentSystemDefault()
    
    val firstDay = LocalDate(referenceDate.year, referenceDate.monthNumber, 1)
    val firstDayDayOfWeek = firstDay.dayOfWeek.ordinal
    val startOffset = (firstDayDayOfWeek + 1) % 7
    
    val nextMonthYear = if (referenceDate.monthNumber == 12) referenceDate.year + 1 else referenceDate.year
    val nextMonth = if (referenceDate.monthNumber == 12) 1 else referenceDate.monthNumber + 1
    val daysInMonth = LocalDate(nextMonthYear, nextMonth, 1).minus(1, DateTimeUnit.DAY).dayOfMonth
    
    val prevMonthYear = if (referenceDate.monthNumber == 1) referenceDate.year - 1 else referenceDate.year
    val prevMonth = if (referenceDate.monthNumber == 1) 12 else referenceDate.monthNumber - 1
    val daysInPrevMonth = LocalDate(referenceDate.year, referenceDate.monthNumber, 1).minus(1, DateTimeUnit.DAY).dayOfMonth
    
    val gridDates = List(42) { i ->
        if (i < startOffset) {
            val d = daysInPrevMonth - startOffset + i + 1
            LocalDate(prevMonthYear, prevMonth, d)
        } else if (i < startOffset + daysInMonth) {
            val d = i - startOffset + 1
            LocalDate(referenceDate.year, referenceDate.monthNumber, d)
        } else {
            val d = i - startOffset - daysInMonth + 1
            LocalDate(nextMonthYear, nextMonth, d)
        }
    }
    
    val startWindow = LocalDateTime(gridDates.first().year, gridDates.first().monthNumber, gridDates.first().dayOfMonth, 0, 0, 0, 0).toInstant(tz)
    val endWindow = LocalDateTime(gridDates.last().year, gridDates.last().monthNumber, gridDates.last().dayOfMonth, 23, 59, 59, 999_999_999).toInstant(tz)
    val occurrences = getOccurrencesForWindow(schedules, startWindow, endWindow)
    
    var selectedDayForList by remember { mutableStateOf<LocalDate?>(referenceDate) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            val weekdays = when (lang) {
                Language.KOREAN -> listOf("일", "월", "화", "수", "목", "금", "토")
                Language.JAPANESE -> listOf("日", "月", "火", "水", "木", "金", "土")
                Language.RUSSIAN -> listOf("Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")
                else -> listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            }
            for (day in weekdays) {
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (day == weekdays[0]) Color.Red else if (day == weekdays[6]) AccentCyan else TextSecondary,
                    maxLines = 1
                )
            }
        }
        
        Column(modifier = Modifier.weight(1.0f)) {
            for (row in 0 until 6) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val idx = row * 7 + col
                        val date = gridDates[idx]
                        val isCurrentMonth = date.monthNumber == referenceDate.monthNumber
                        val isSelected = date == selectedDayForList
                        
                        val dayStart = LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, 0, 0, 0, 0).toInstant(tz)
                        val dayEnd = LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, 23, 59, 59, 999_999_999).toInstant(tz)
                        val dayOccurrences = occurrences.filter {
                            it.startTime < dayEnd && it.endTime > dayStart
                        }
                        
                        Box(
                            modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .border(0.5.dp, BorderColor.copy(alpha = 0.3f))
                                    .background(
                                        if (isSelected) AccentPurple.copy(alpha = 0.2f)
                                        else if (isCurrentMonth) Color.Transparent
                                        else SurfaceCard.copy(alpha = 0.3f)
                                    )
                                    .clickable { selectedDayForList = date }
                                    .padding(4.dp)
                        ) {
                            Column {
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) AccentPurple
                                            else if (isCurrentMonth) TextPrimary
                                            else TextSecondary.copy(alpha = 0.4f),
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                
                                for (occ in dayOccurrences.take(2)) {
                                    Text(
                                        text = occ.schedule.title,
                                        fontSize = 8.sp,
                                        maxLines = 1,
                                        color = TextPrimary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 1.dp)
                                            .background(AccentCyan.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
                                            .padding(horizontal = 2.dp),
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (dayOccurrences.size > 2) {
                                    Text(
                                        text = "+${dayOccurrences.size - 2}",
                                        fontSize = 8.sp,
                                        color = AccentPurple,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        selectedDayForList?.let { sDay ->
            val sDayStart = LocalDateTime(sDay.year, sDay.monthNumber, sDay.dayOfMonth, 0, 0, 0, 0).toInstant(tz)
            val sDayEnd = LocalDateTime(sDay.year, sDay.monthNumber, sDay.dayOfMonth, 23, 59, 59, 999_999_999).toInstant(tz)
            val sDayOccurrences = occurrences.filter {
                it.startTime < sDayEnd && it.endTime > sDayStart
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .padding(top = 8.dp)
                    .background(SurfaceCard)
                    .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = "${sDay.year}-${sDay.monthNumber}-${sDay.dayOfMonth} " + when (lang) {
                        Language.KOREAN -> "일정 리스트"
                        Language.JAPANESE -> "スケジュールリスト"
                        Language.RUSSIAN -> "Список событий"
                        else -> "Schedules"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentPurple,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                
                if (sDayOccurrences.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(Localization.get("no_schedules", lang), fontSize = 12.sp, color = TextSecondary)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(sDayOccurrences) { occ ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .background(SlateDarkBg, RoundedCornerShape(4.dp))
                                    .padding(6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(occ.schedule.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text(
                                        "${occ.startTime.toLocalDateTime(tz).time.toString().take(5)} ~ ${occ.endTime.toLocalDateTime(tz).time.toString().take(5)}",
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteSchedule(occ.schedule.id) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(Icons.Default.Delete, "Delete", tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuarterlyCalendarView(
    referenceDate: LocalDate,
    schedules: List<Schedule>,
    lang: Language,
    onDeleteSchedule: (String) -> Unit
) {
    val tz = TimeZone.currentSystemDefault()
    val year = referenceDate.year
    
    LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (q in 1..4) {
            val startMonth = (q - 1) * 3 + 1
            val endMonth = q * 3
            
            val startQ = LocalDateTime(year, startMonth, 1, 0, 0, 0, 0).toInstant(tz)
            val endMonthLastDay = LocalDate(year, endMonth, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth
            val endQ = LocalDateTime(year, endMonth, endMonthLastDay, 23, 59, 59, 999_999_999).toInstant(tz)
            
            val qOccurrences = getOccurrencesForWindow(schedules, startQ, endQ)
            
            val qTitle = when (lang) {
                Language.KOREAN -> "${year}년 제 ${q}분기"
                Language.JAPANESE -> "${year}年 第${q}四半期"
                Language.RUSSIAN -> "${year} год - ${q}-й квартал"
                else -> "$year Q$q"
            }
            
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().border(0.5.dp, BorderColor, RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(qTitle, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        if (qOccurrences.isEmpty()) {
                            Text(
                                text = when (lang) {
                                    Language.KOREAN -> "등록된 분기 목표/일정이 없습니다."
                                    Language.JAPANESE -> "登録された目標/スケジュールはありません。"
                                    Language.RUSSIAN -> "Нет событий на этот квартал."
                                    else -> "No goals or schedules in this quarter."
                                },
                                fontSize = 11.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            for (occ in qOccurrences) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .background(SlateDarkBg, RoundedCornerShape(4.dp))
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(occ.schedule.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text(
                                            "${occ.startTime.toLocalDateTime(tz).date} (${occ.startTime.toLocalDateTime(tz).time.toString().take(5)})",
                                            fontSize = 10.sp,
                                            color = TextSecondary
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteSchedule(occ.schedule.id) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, "Delete", tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SemiAnnualCalendarView(
    referenceDate: LocalDate,
    schedules: List<Schedule>,
    lang: Language,
    onDeleteSchedule: (String) -> Unit
) {
    val tz = TimeZone.currentSystemDefault()
    val year = referenceDate.year
    
    LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val halves = listOf(1, 2)
        for (half in halves) {
            val startMonth = if (half == 1) 1 else 7
            val endMonth = if (half == 1) 6 else 12
            
            val startH = LocalDateTime(year, startMonth, 1, 0, 0, 0, 0).toInstant(tz)
            val endH = LocalDateTime(year, endMonth, if (half == 1) 30 else 31, 23, 59, 59, 999_999_999).toInstant(tz)
            
            val hOccurrences = getOccurrencesForWindow(schedules, startH, endH)
            
            val hTitle = when (lang) {
                Language.KOREAN -> if (half == 1) "${year}년 상반기 (1st Half)" else "${year}년 하반기 (2nd Half)"
                Language.JAPANESE -> if (half == 1) "${year}年 上半期" else "${year}年 下半期"
                Language.RUSSIAN -> if (half == 1) "${year} год - 1-е полугодие" else "${year} год - 2-е полугодие"
                else -> if (half == 1) "$year First Half" else "$year Second Half"
            }
            
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().border(0.5.dp, BorderColor, RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(hTitle, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentPurple)
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        if (hOccurrences.isEmpty()) {
                            Text(
                                text = when (lang) {
                                    Language.KOREAN -> "등록된 반기 목표/일정이 없습니다."
                                    Language.JAPANESE -> "登録された目標/スケジュールはありません。"
                                    Language.RUSSIAN -> "Нет событий на это полугодие."
                                    else -> "No goals or schedules in this half-year."
                                },
                                fontSize = 11.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            for (occ in hOccurrences) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .background(SlateDarkBg, RoundedCornerShape(4.dp))
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(occ.schedule.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text(
                                            "${occ.startTime.toLocalDateTime(tz).date} (${occ.startTime.toLocalDateTime(tz).time.toString().take(5)})",
                                            fontSize = 10.sp,
                                            color = TextSecondary
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteSchedule(occ.schedule.id) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, "Delete", tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun YearlyCalendarView(
    referenceDate: LocalDate,
    schedules: List<Schedule>,
    lang: Language,
    onDeleteSchedule: (String) -> Unit
) {
    val tz = TimeZone.currentSystemDefault()
    val year = referenceDate.year
    
    val yearlySchedules = schedules.filter { schedule ->
        val startLocalDateTime = schedule.startTime.toLocalDateTime(tz)
        val startYear = startLocalDateTime.year
        if (startYear == year) {
            true
        } else if (schedule.recurrenceRule != null && startYear < year) {
            val until = schedule.recurrenceRule.until
            if (until == null) {
                true
            } else {
                until.toLocalDateTime(tz).year >= year
            }
        } else {
            false
        }
    }.sortedBy { it.startTime }

    val viewTitle = when (lang) {
        Language.KOREAN -> "${year}년 등록된 연간 일정 / 목표 목록 (${yearlySchedules.size}개)"
        Language.JAPANESE -> "${year}年 登録済みの年間スケジュール・目標一覧 (${yearlySchedules.size}件)"
        Language.RUSSIAN -> "Зарегистрированные цели и планы на ${year} год (${yearlySchedules.size})"
        else -> "Configured Schedules & Goals for $year (${yearlySchedules.size} items)"
    }

    val emptyLabel = when (lang) {
        Language.KOREAN -> "등록된 연간 일정이 없습니다."
        Language.JAPANESE -> "登録された年間スケジュールはありません。"
        Language.RUSSIAN -> "Нет зарегистрированных целей на этот год."
        else -> "No configured schedules for this year."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        Text(
            text = viewTitle,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = AccentCyan,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        if (yearlySchedules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.0f)
                    .background(SurfaceCard, RoundedCornerShape(8.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emptyLabel,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(yearlySchedules) { schedule ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, BorderColor, RoundedCornerShape(8.dp)),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                        ) {
                            val barColor = if (schedule.recurrenceRule != null) AccentPurple else AccentCyan
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(6.dp)
                                    .background(barColor)
                            )
                            
                            Row(
                                modifier = Modifier
                                    .weight(1.0f)
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1.0f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = schedule.title,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        if (schedule.recurrenceRule != null) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(AccentPurple.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = schedule.recurrenceRule.frequency.name,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AccentPurple
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = "Time",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${schedule.startTime.toLocalDateTime(tz).toString().take(16)} ~ ${schedule.endTime.toLocalDateTime(tz).toString().take(16)}",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }
                                    if (schedule.location != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.LocationOn,
                                                contentDescription = "Location",
                                                tint = AccentCyan,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${schedule.location.name} (${schedule.location.latitude}, ${schedule.location.longitude})",
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                    if (schedule.participants.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = "Participants",
                                                tint = TextSecondary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = schedule.participants.joinToString { it.displayName },
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { onDeleteSchedule(schedule.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = Color.Red.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleListItem(schedule: Schedule, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(0.5.dp, BorderColor, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = SlateDarkBg)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1.0f)) {
                Text(schedule.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, "Time", tint = AccentPurple, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "${schedule.startTime.toString().take(16)} ~ ${schedule.endTime.toString().take(16)}",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                if (schedule.location != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, "Location", tint = AccentCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "${schedule.location.name} (${schedule.location.latitude}, ${schedule.location.longitude})",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
                if (schedule.recurrenceRule != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Autorenew, "Recurrence", tint = ActiveGreen, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Cycle: ${schedule.recurrenceRule.frequency} (x${schedule.recurrenceRule.count ?: "Inf"})",
                            fontSize = 11.sp,
                            color = ActiveGreen
                        )
                    }
                }
                if (schedule.participants.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Participants: " + schedule.participants.joinToString { it.displayName + " (" + it.phoneNumber + ")" },
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f))
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun AddScheduleDialog(
    lang: Language,
    mapData: OsmMapData?,
    spatialIndex: SpatialGridIndex?,
    onDismiss: () -> Unit,
    onConfirm: (Schedule) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var startTimeText by remember { mutableStateOf("2026-06-01T10:00:00Z") }
    var endTimeText by remember { mutableStateOf("2026-06-01T11:00:00Z") }
    
    var locName by remember { mutableStateOf("") }
    var locLat by remember { mutableStateOf("37.5665") }
    var locLon by remember { mutableStateOf("126.9780") }

    var recurFreq by remember { mutableStateOf(RecurrenceFrequency.DAILY) }
    var recurInterval by remember { mutableStateOf("1") }
    var recurCount by remember { mutableStateOf("") }
    var hasRecur by remember { mutableStateOf(false) }

    var partName by remember { mutableStateOf("") }
    var partPhone by remember { mutableStateOf("") }
    var partEmail by remember { mutableStateOf("") }
    var participantsList by remember { mutableStateOf(emptyList<Participant>()) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Map Picker & Search States
    var miniMapZoom by remember { mutableStateOf(15.0) }
    var searchQuery by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(emptyList<OsmPlace>()) }
    var nearestPlace by remember { mutableStateOf<Pair<OsmPlace, Double>?>(null) }

    fun updateSuggestions(query: String) {
        searchQuery = query
        suggestions = if (query.length >= 2 && mapData != null) {
            mapData.places.filter { it.name.contains(query, ignoreCase = true) }.take(5)
        } else {
            emptyList()
        }
    }

    LaunchedEffect(locLat, locLon, mapData) {
        val latVal = locLat.toDoubleOrNull() ?: 37.5665
        val lonVal = locLon.toDoubleOrNull() ?: 126.9780
        val data = mapData
        if (data != null) {
            nearestPlace = findNearestPlaceLocal(latVal, lonVal, data, spatialIndex)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Localization.get("new_schedule", lang), color = TextPrimary) },
        containerColor = SurfaceCard,
        tonalElevation = 6.dp,
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
            ) {
                item {
                    if (errorMessage != null) {
                        Text(errorMessage!!, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }

                    Text(Localization.get("basic_info", lang), fontWeight = FontWeight.Bold, color = AccentPurple, fontSize = 14.sp)
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(Localization.get("event_title", lang)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = appTextFieldColors()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = startTimeText,
                        onValueChange = { startTimeText = it },
                        label = { Text(Localization.get("start_time", lang)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = appTextFieldColors()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = endTimeText,
                        onValueChange = { endTimeText = it },
                        label = { Text(Localization.get("end_time", lang)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = appTextFieldColors()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(Localization.get("geo_location", lang), fontWeight = FontWeight.Bold, color = AccentCyan, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(6.dp))

                    // Address Search Field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { 
                            updateSuggestions(it)
                            locName = it
                        },
                        label = { Text("주소/장소 검색 (Search Address/Place)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = appTextFieldColors()
                    )

                    // Floating suggestions dropdown
                    if (suggestions.isNotEmpty()) {
                        Surface(
                            color = SurfaceCard,
                            shadowElevation = 4.dp,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column {
                                suggestions.forEach { place ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                locLat = place.lat.toString()
                                                locLon = place.lon.toString()
                                                locName = place.name
                                                searchQuery = place.name
                                                suggestions = emptyList()
                                            }
                                            .padding(12.dp)
                                    ) {
                                        Text(place.name + " (${place.type})", color = TextPrimary, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Coordinates Inputs
                    Row {
                        OutlinedTextField(
                            value = locLat,
                            onValueChange = { locLat = it },
                            label = { Text(Localization.get("latitude", lang)) },
                            modifier = Modifier.weight(1f),
                            colors = appTextFieldColors()
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedTextField(
                            value = locLon,
                            onValueChange = { locLon = it },
                            label = { Text(Localization.get("longitude", lang)) },
                            modifier = Modifier.weight(1f),
                            colors = appTextFieldColors()
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Nearest place HUD & Autofill
                    nearestPlace?.let { (place, dist) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x1F00E5FF), shape = RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                "가장 가까운 곳: ${place.name} (${dist.toInt()}m)",
                                color = AccentCyan,
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    locName = place.name
                                    searchQuery = place.name
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("자동입력", color = SlateDarkBg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Interactive Mini Map Picker Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(currentThemeState.value.mapBackground)
                            .clipToBounds()
                    ) {
                        val latVal = locLat.toDoubleOrNull() ?: 37.5665
                        val lonVal = locLon.toDoubleOrNull() ?: 126.9780

                        val path = remember { Path() }
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(miniMapZoom) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        // Read directly from the mutable states to get the latest updated values on each drag tick
                                        val currentLon = locLon.toDoubleOrNull() ?: 126.9780
                                        val currentLat = locLat.toDoubleOrNull() ?: 37.5665
                                        val cx = getPixelX(currentLon, miniMapZoom)
                                        val cy = getPixelY(currentLat, miniMapZoom)
                                        val newCx = cx - dragAmount.x
                                        val newCy = cy - dragAmount.y
                                        locLon = String.format("%.6f", pixelXToLon(newCx, miniMapZoom))
                                        locLat = String.format("%.6f", pixelYToLat(newCy, miniMapZoom))
                                    }
                                }
                                .onPointerEvent(PointerEventType.Scroll) { event ->
                                    val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                    if (delta != 0f) {
                                        val zoomChange = if (delta < 0) 1.0 else -1.0
                                        miniMapZoom = (miniMapZoom + zoomChange).coerceIn(11.0, 18.0)
                                    }
                                }
                        ) {
                            val activeMapData = mapData ?: SimulatedMapData.data
                            val canvasWidth = size.width
                            val canvasHeight = size.height
                            val centerX = getPixelX(lonVal, miniMapZoom)
                            val centerY = getPixelY(latVal, miniMapZoom)

                            fun toScreenX(lon: Double): Float = ((canvasWidth / 2) + (getPixelX(lon, miniMapZoom) - centerX)).toFloat()
                            fun toScreenY(lat: Double): Float = ((canvasHeight / 2) + (getPixelY(lat, miniMapZoom) - centerY)).toFloat()

                            val minLon = pixelXToLon(centerX - canvasWidth / 2, miniMapZoom)
                            val maxLon = pixelXToLon(centerX + canvasWidth / 2, miniMapZoom)
                            val minLat = pixelYToLat(centerY + canvasHeight / 2, miniMapZoom)
                            val maxLat = pixelYToLat(centerY - canvasHeight / 2, miniMapZoom)

                            val visibleWays = spatialIndex?.queryWays(minLat, maxLat, minLon, maxLon) ?: activeMapData.ways

                            // 1. Draw Roads & Polygons
                            for (way in visibleWays) {
                                if (way.maxLat < minLat || way.minLat > maxLat || way.maxLon < minLon || way.minLon > maxLon) continue

                                // Simple LOD check for mini map
                                if (way.type == "building" && miniMapZoom < 15.0) continue
                                if (way.type == "water" && miniMapZoom < 12.0) continue

                                if (way.type !in listOf("building", "water")) {
                                    if (miniMapZoom < 14.0 && way.type !in listOf("motorway", "trunk", "primary", "secondary")) continue
                                }

                                path.reset()
                                var first = true
                                for (coord in way.coords) {
                                    val sx = toScreenX(coord.lon)
                                    val sy = toScreenY(coord.lat)
                                    if (first) {
                                        path.moveTo(sx, sy)
                                        first = false
                                    } else {
                                        path.lineTo(sx, sy)
                                    }
                                }

                                if (way.type == "building") {
                                    // Draw filled building polygon
                                    drawPath(
                                        path = path,
                                        color = currentThemeState.value.surfaceCardColor.copy(alpha = 0.6f),
                                        style = androidx.compose.ui.graphics.drawscope.Fill
                                    )
                                    drawPath(
                                        path = path,
                                        color = currentThemeState.value.accentPurpleColor.copy(alpha = 0.25f),
                                        style = Stroke(width = 0.8f.dp.toPx())
                                    )
                                } else if (way.type == "water") {
                                    // Draw filled water body
                                    drawPath(
                                        path = path,
                                        color = currentThemeState.value.mapWater,
                                        style = androidx.compose.ui.graphics.drawscope.Fill
                                    )
                                } else {
                                    // Draw regular road stroke
                                    drawPath(
                                        path = path,
                                        color = getRoadColor(way.type, currentThemeState.value),
                                        style = Stroke(width = 1.5f.dp.toPx())
                                    )
                                }
                            }

                            // 2. Draw Center Crosshair (Red Pin)
                            val ccX = canvasWidth / 2
                            val ccY = canvasHeight / 2
                            // Crosshair lines
                            drawLine(Color.Red, Offset(ccX - 15f, ccY), Offset(ccX + 15f, ccY), strokeWidth = 2f)
                            drawLine(Color.Red, Offset(ccX, ccY - 15f), Offset(ccX, ccY + 15f), strokeWidth = 2f)
                            drawCircle(Color.Red, radius = 4f, center = Offset(ccX, ccY))
                        }

                        // Zoom buttons on mini map
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                        ) {
                            Button(
                                onClick = { miniMapZoom = (miniMapZoom + 1.0).coerceAtMost(18.0) },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("+", color = TextPrimary, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = { miniMapZoom = (miniMapZoom - 1.0).coerceAtLeast(11.0) },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("-", color = TextPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = hasRecur, onCheckedChange = { hasRecur = it })
                        Text(Localization.get("add_recur", lang), color = TextPrimary)
                    }

                    if (hasRecur) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(Localization.get("frequency", lang) + ": ", color = TextSecondary)
                            Spacer(modifier = Modifier.width(6.dp))
                            RecurrenceFrequency.entries.forEach { freq ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { recurFreq = freq }.padding(horizontal = 4.dp)
                                ) {
                                    RadioButton(selected = recurFreq == freq, onClick = { recurFreq = freq })
                                    Text(freq.name.take(3), color = TextPrimary, fontSize = 10.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = recurInterval,
                            onValueChange = { recurInterval = it },
                            label = { Text(Localization.get("interval", lang)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = appTextFieldColors()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = recurCount,
                            onValueChange = { recurCount = it },
                            label = { Text(Localization.get("count_limit", lang)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = appTextFieldColors()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(Localization.get("participants", lang), fontWeight = FontWeight.Bold, color = ActiveGreen, fontSize = 14.sp)
                    OutlinedTextField(
                        value = partName,
                        onValueChange = { partName = it },
                        label = { Text(Localization.get("display_name", lang)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = appTextFieldColors()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = partPhone,
                        onValueChange = { partPhone = it },
                        label = { Text(Localization.get("phone_num", lang)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = appTextFieldColors()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = partEmail,
                        onValueChange = { partEmail = it },
                        label = { Text(Localization.get("email_addr", lang)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = appTextFieldColors()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            if (partName.isNotBlank() && partPhone.isNotBlank()) {
                                participantsList = participantsList + Participant(
                                    displayName = partName,
                                    phoneNumber = partPhone,
                                    email = partEmail.ifBlank { null }
                                )
                                partName = ""
                                partPhone = ""
                                partEmail = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ActiveGreen)
                    ) {
                        Text(Localization.get("add_participant", lang), color = SlateDarkBg, fontWeight = FontWeight.Bold)
                    }

                    if (participantsList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Added: " + participantsList.joinToString { it.displayName },
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        if (title.isBlank()) {
                            errorMessage = "Title is required."
                            return@Button
                        }
                        val start = Instant.parse(startTimeText.trim())
                        val end = Instant.parse(endTimeText.trim())
                        if (start > end) {
                            errorMessage = "Start time must be before end time."
                            return@Button
                        }

                        val locationObj = if (locName.isNotBlank()) {
                            Location(
                                name = locName,
                                latitude = locLat.toDoubleOrNull() ?: 0.0,
                                longitude = locLon.toDoubleOrNull() ?: 0.0
                            )
                        } else null

                        val recurObj = if (hasRecur) {
                            RecurrenceRule(
                                frequency = recurFreq,
                                interval = recurInterval.toIntOrNull() ?: 1,
                                count = recurCount.toIntOrNull()
                            )
                        } else null

                        val finalSchedule = Schedule(
                            id = Clock.System.now().toEpochMilliseconds().toString(),
                            title = title,
                            startTime = start,
                            endTime = end,
                            location = locationObj,
                            recurrenceRule = recurObj,
                            participants = participantsList
                        )

                        onConfirm(finalSchedule)

                    } catch (e: Exception) {
                        errorMessage = "Error validating inputs: ${e.message}"
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
            ) {
                Text(Localization.get("confirm", lang))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Localization.get("cancel", lang), color = TextSecondary)
            }
        }
    )
}
