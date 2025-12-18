package com.eynnzerr.tasks

import com.eynnzerr.data.ChatGroupRepository
import com.eynnzerr.data.RoomRepository
import com.eynnzerr.model.ChatDisbandedPayload
import com.eynnzerr.model.WebSocketActions
import com.eynnzerr.model.WebSocketResponse
import com.eynnzerr.utils.WebSocketManager
import io.ktor.server.application.Application
import io.ktor.server.config.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.TimeUnit

class CleanupTask(private val config: ApplicationConfig) : KoinComponent {
    private val roomRepository by inject<RoomRepository>()
    private val chatGroupRepository by inject<ChatGroupRepository>()
    private val webSocketManager by inject<WebSocketManager>()

    fun launch(scope: CoroutineScope) {
        val logger = LoggerFactory.getLogger(CleanupTask::class.java)
        val roomRetentionDays = config.property("cleanup.room_retention_days").getString().toLong() // Rename for clarity
        val cleanupIntervalHours = config.property("cleanup.interval_hours").getString().toLong()
        val chatInactivityTimeoutHours = config.property("cleanup.chat_inactivity_timeout_hours").getString().toLong()

        scope.launch(Dispatchers.IO) {
            logger.info("Room cleanup task started.")
            while (true) {
                val deletedCount = roomRepository.deleteOutdatedRooms(roomRetentionDays)
                logger.info("Room cleanup task: Deleted $deletedCount old rooms.")
                delay(TimeUnit.HOURS.toMillis(cleanupIntervalHours))
            }
        }

        scope.launch(Dispatchers.IO) {
            logger.info("Chat group cleanup task started.")
            while (true) {
                val inactiveGroups = chatGroupRepository.findInactiveChatGroups(Duration.ofHours(chatInactivityTimeoutHours))
                if (inactiveGroups.isNotEmpty()) {
                    logger.info("Found ${inactiveGroups.size} inactive chat groups to disband.")
                    inactiveGroups.forEach { group ->
                        chatGroupRepository.disbandChatGroup(group.id)
                        val disbandNotification = WebSocketResponse(
                            status = "success",
                            action = WebSocketActions.CHAT_DISBANDED,
                            response = ChatDisbandedPayload(groupId = group.id)
                        )
                        webSocketManager.broadcastToChatGroup(group.id, disbandNotification)
                        logger.info("Disbanded inactive chat group: ${group.id}")
                    }
                }
                delay(TimeUnit.HOURS.toMillis(cleanupIntervalHours)) // Use same interval as room cleanup
            }
        }
    }
}

fun Application.configureCleanup() {
    val cleanupTask = CleanupTask(environment.config)
    cleanupTask.launch(this)
}