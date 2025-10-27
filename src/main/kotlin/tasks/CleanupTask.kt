package com.eynnzerr.tasks

import com.eynnzerr.data.RoomRepository
import io.ktor.server.application.Application
import io.ktor.server.config.* 
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class CleanupTask(private val config: ApplicationConfig) : KoinComponent {
    private val roomRepository by inject<RoomRepository>()

    fun launch(scope: CoroutineScope) {
        val logger = LoggerFactory.getLogger(CleanupTask::class.java)
        val retentionDays = config.property("cleanup.retention_days").getString().toLong()
        val intervalHours = config.property("cleanup.interval_hours").getString().toLong()

        scope.launch(Dispatchers.IO) {
            logger.info("Cleanup task started.")
            while (true) {
                val deletedCount = roomRepository.deleteOutdatedRooms(retentionDays)
                logger.info("Cleanup task: Deleted $deletedCount old rooms.")
                delay(TimeUnit.HOURS.toMillis(intervalHours))
            }
        }
    }
}

fun Application.configureCleanup() {
    val cleanupTask = CleanupTask(environment.config)
    cleanupTask.launch(this)
}