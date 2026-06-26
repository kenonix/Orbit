package com.orbit.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbit.app.model.*
import com.orbit.app.engine.*

// ==========================================
// 🤖 AI SOCKET CONTROL TAB
// ==========================================
@Composable
fun AiSocketTab(
    isActive: Boolean,
    onToggle: (Boolean) -> Unit,
    port: Int,
    onPortChange: (Int) -> Unit,
    logs: List<String>,
    lang: Language
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(Localization.get("ai_title", lang), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(Localization.get("ai_desc", lang), fontSize = 13.sp, color = TextSecondary)

        Spacer(modifier = Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxSize()) {
            // Left Config Controls
            Column(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceCard)
                    .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(Localization.get("config_settings", lang), fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(Localization.get("server_status", lang), color = TextSecondary)
                        Switch(
                            checked = isActive,
                            onCheckedChange = onToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ActiveGreen,
                                checkedTrackColor = ActiveGreen.copy(alpha = 0.3f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = port.toString(),
                        onValueChange = { onPortChange(it.toIntOrNull() ?: 9090) },
                        label = { Text(Localization.get("local_port", lang)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isActive, // Lock when running
                        colors = appTextFieldColors()
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SlateDarkBg)
                        .padding(12.dp)
                ) {
                    Text("JSON-RPC 2.0 METHODS", fontWeight = FontWeight.Bold, color = AccentCyan, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("• getSchedules\n• upsertSchedule\n• deleteSchedule\n• getTravelEstimate", fontSize = 12.sp, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.width(20.dp))

            // Right Server logs
            Column(
                modifier = Modifier
                    .weight(1.5f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceCard)
                    .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(Localization.get("connection_logs", lang), fontWeight = FontWeight.Bold, color = AccentPurple, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(6.dp))
                        .background(SlateDarkBg)
                        .border(0.5.dp, BorderColor, RoundedCornerShape(6.dp))
                        .padding(12.dp)
                ) {
                    items(logs) { log ->
                        Text(
                            text = log,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (log.contains("ERROR")) Color.Red.copy(alpha = 0.8f) else TextPrimary,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
