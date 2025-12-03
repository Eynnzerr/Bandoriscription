package com.eynnzerr.data

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.flywaydb.core.Flyway

object DatabaseFactory {
    fun init(config: ApplicationConfig) {
        val driverClassName = config.property("storage.driverClassName").getString()
        val jdbcURL = config.property("storage.jdbcURL").getString()
        val user = System.getenv("DB_USER") ?: config.property("storage.user").getString()
        val password = System.getenv("DB_PASSWORD") ?: config.property("storage.password").getString()
        val maximumPoolSize = config.property("storage.maximumPoolSize").getString().toInt()
        connectAndMigrate(jdbcURL, driverClassName, user, password, maximumPoolSize, false)
    }

    // Overloaded init for tests
    fun init(isTest: Boolean) {
        if (isTest) {
            connectAndMigrate("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;", "org.h2.Driver", "", "", 1, true)
        } else {
            // This case should ideally not be reached if init(config) is called for prod
            // but keeping it for completeness or if called incorrectly without config
            throw IllegalStateException("DatabaseFactory.init(isTest=false) called without ApplicationConfig")
        }
    }

    private fun connectAndMigrate(jdbcURL: String, driverClassName: String, user: String, password: String, maximumPoolSize: Int, isTest: Boolean) {
        val dataSource = createHikariDataSource(jdbcURL, driverClassName, user, password, maximumPoolSize)

        if (!isTest) { // Only run migrations for non-test environments
            val flyway = Flyway.configure()
                .dataSource(dataSource)
                .load()
            flyway.migrate()
        }

        Database.connect(dataSource)
    }

    private fun createHikariDataSource(url: String, driver: String, user: String, pass: String, poolSize: Int): HikariDataSource {
        val config = HikariConfig().apply {
            driverClassName = driver
            jdbcUrl = url
            username = user
            password = pass
            maximumPoolSize = poolSize
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
        return HikariDataSource(config)
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
