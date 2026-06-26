package com.orbit.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import com.orbit.app.db.ScheduleRepository
import com.orbit.app.sync.*
import kotlinx.datetime.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

// ==========================================
// 🔗 P2P SYNC ENGINE TAB
// ==========================================
@Composable
fun SyncTab(
    repository: ScheduleRepository,
    deviceId: String,
    lang: Language,
    onSyncComplete: () -> Unit
) {
    var syncLog by remember { mutableStateOf(listOf("Sync Engine ready.", "Current Vector Sequence: Device[$deviceId] -> ${repository.getLatestSequenceForDevice(deviceId)}")) }
    var isScanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Mock Peers Discovered
    val peers = listOf(
        PeerStatus("tablet-node-920", "Kenonix's Tab Ultra", ConnectionType.BLE_PROXIMITY, true, 0f),
        PeerStatus("win-pc-core", "Workstation Main", ConnectionType.MDNS_LAN, false, 0f)
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Text(Localization.get("tab_sync", lang), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(Localization.get("p2p_desc", lang), fontSize = 13.sp, color = TextSecondary)

        Spacer(modifier = Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxSize()) {
            // Left: Peer Discovery list
            Column(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceCard)
                    .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(Localization.get("discovered_nodes", lang), fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                    IconButton(onClick = {
                        isScanning = true
                        syncLog = syncLog + "Scanning BLE & Wi-Fi Direct for local peers..."
                    }) {
                        Icon(Icons.Default.Refresh, "Scan", tint = AccentPurple)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn {
                    items(peers) { peer ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(SlateDarkBg)
                                .border(0.5.dp, BorderColor, RoundedCornerShape(6.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(peer.deviceName, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                                Text("ID: ${peer.deviceId} (${peer.connectionType})", fontSize = 11.sp, color = TextSecondary)
                            }
                            Button(
                                onClick = {
                                    scope.launch {
                                        syncLog = syncLog + "Initiating handshake with ${peer.deviceName}..."
                                        delay(800)
                                        
                                        // 1. Handshake Vector clock exchanging
                                        val myLastSeq = repository.getLatestSequenceForDevice(deviceId)
                                        val localClock = VectorClock(mapOf(deviceId to myLastSeq))
                                        
                                        // Simulate peer having a higher sequence number for itself (seq 5), but lag our device.
                                        val peerLastSeq = 5L
                                        val peerClock = VectorClock(mapOf(peer.deviceId to peerLastSeq, deviceId to (myLastSeq - 1).coerceAtLeast(0L)))
                                        
                                        syncLog = syncLog + "Handshake complete. Vector Clock Diff calculated:"
                                        syncLog = syncLog + "  Local: Device[$deviceId] -> seq $myLastSeq"
                                        syncLog = syncLog + "  Peer: Device[${peer.deviceId}] -> seq $peerLastSeq"
                                        
                                        // 2. Simulate requesting delta changes
                                        delay(800)
                                        syncLog = syncLog + "Requesting missing updates from ${peer.deviceName}..."
                                        
                                        // Simulate merging a incoming delta mutation (creating a new sync schedule event)
                                        val newSyncSchedule = Schedule(
                                            id = "sync-${Clock.System.now().toEpochMilliseconds()}",
                                            title = "Synced Team Briefing",
                                            startTime = Instant.parse("2026-06-10T14:00:00Z"),
                                            endTime = Instant.parse("2026-06-10T15:00:00Z"),
                                            location = Location("Virtual P2P Room", 0.0, 0.0),
                                            participants = listOf(Participant("+821099998888", displayName = "Co-worker P2P"))
                                        )
                                        
                                        val incomingEntry = ChangelogEntry(
                                            id = "mutation-${Clock.System.now().toEpochMilliseconds()}",
                                            entityId = newSyncSchedule.id,
                                            entityType = "SCHEDULE",
                                            mutationType = "UPDATE",
                                            serializedData = Json.encodeToString(newSyncSchedule),
                                            sequenceNumber = peerLastSeq,
                                            deviceId = peer.deviceId,
                                            timestamp = Clock.System.now().toEpochMilliseconds()
                                        )
                                        
                                        repository.applyIncomingChangelog(incomingEntry)
                                        
                                        syncLog = syncLog + "Applied 1 delta update from ${peer.deviceName}."
                                        syncLog = syncLog + "Sync completed successfully!"
                                        onSyncComplete()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(Localization.get("sync_now_btn", lang), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(20.dp))

            // Right Log Console & Storage Health
            Column(
                modifier = Modifier
                    .weight(1.5f)
                    .fillMaxHeight()
            ) {
                // Console Log
                Column(
                    modifier = Modifier
                        .weight(1.5f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceCard)
                        .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Text(Localization.get("sync_console_log", lang), fontWeight = FontWeight.Bold, color = AccentCyan, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(6.dp))
                            .background(SlateDarkBg)
                            .border(0.5.dp, BorderColor, RoundedCornerShape(6.dp))
                            .padding(12.dp)
                    ) {
                        items(syncLog) { line ->
                            Text(
                                text = line,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (line.contains("complete") || line.contains("success")) ActiveGreen else TextPrimary,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Asset Health (1GB Optimization Stats)
                Column(
                    modifier = Modifier
                        .weight(1.0f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceCard)
                        .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Text(Localization.get("storage_opt", lang), fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(Localization.get("database_sqlite", lang), fontSize = 11.sp, color = TextSecondary)
                            Text("354 KB", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                        }
                        Column {
                            Text(Localization.get("maps_cache", lang), fontSize = 11.sp, color = TextSecondary)
                            Text("845 MB", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AccentPurple)
                        }
                        Column {
                            Text(Localization.get("decoupled_assets", lang), fontSize = 11.sp, color = TextSecondary)
                            Text("180 MB", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ActiveGreen)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        Localization.get("storage_desc", lang),
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
