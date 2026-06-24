package com.orbit.app.socket

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import com.orbit.app.model.Schedule
import com.orbit.app.model.Location
import com.orbit.app.engine.NavigationEngine

class AiControlSocketServer(
    private val host: String = "127.0.0.1",
    private val port: Int = 9090,
    private val getSchedulesCallback: () -> List<Schedule>,
    private val upsertScheduleCallback: (Schedule) -> Unit,
    private val deleteScheduleCallback: (String) -> Unit
) {
    private var server: NettyApplicationEngine? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun start() {
        server = embeddedServer(Netty, host = host, port = port) {
            install(WebSockets) {
                pingPeriod = java.time.Duration.ofSeconds(15)
                timeout = java.time.Duration.ofSeconds(15)
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }

            routing {
                webSocket("/control") {
                    send(Frame.Text("Connected to Orbit AI Control WebSocket"))
                    
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            val response = handleJsonRpcMessage(text)
                            send(Frame.Text(response))
                        }
                    }
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 2000)
        scope.cancel()
    }

    private fun handleJsonRpcMessage(messageText: String): String {
        return try {
            val json = Json.parseToJsonElement(messageText).jsonObject
            val id = json["id"] ?: JsonNull
            val method = json["method"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Method name required")
            val params = json["params"]?.jsonObject ?: JsonObject(emptyMap())

            val result = when (method) {
                "getSchedules" -> {
                    val schedules = getSchedulesCallback()
                    Json.encodeToJsonElement(schedules)
                }
                "upsertSchedule" -> {
                    val scheduleJson = params["schedule"] ?: throw IllegalArgumentException("Schedule object required in params")
                    val schedule = Json.decodeFromJsonElement<Schedule>(scheduleJson)
                    upsertScheduleCallback(schedule)
                    JsonPrimitive("success")
                }
                "deleteSchedule" -> {
                    val scheduleId = params["id"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Schedule ID required")
                    deleteScheduleCallback(scheduleId)
                    JsonPrimitive("success")
                }
                "getTravelEstimate" -> {
                    val startJson = params["start"] ?: throw IllegalArgumentException("Start location required")
                    val endJson = params["end"] ?: throw IllegalArgumentException("End location required")
                    val start = Json.decodeFromJsonElement<Location>(startJson)
                    val end = Json.decodeFromJsonElement<Location>(endJson)
                    val distance = NavigationEngine.calculateDistance(start, end)
                    val duration = NavigationEngine.estimateTravelTimeMinutes(start, end)
                    buildJsonObject {
                        put("distanceKm", distance)
                        put("estimatedDurationMinutes", duration)
                    }
                }
                else -> throw NoSuchMethodException("Method $method not found")
            }

            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", id)
                put("result", result)
            }.toString()

        } catch (e: Exception) {
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("error", buildJsonObject {
                    put("code", -32603)
                    put("message", e.message ?: "Internal Error")
                })
            }.toString()
        }
    }
}
