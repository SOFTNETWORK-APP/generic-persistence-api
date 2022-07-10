package app.softnetwork.scheduler.api

import app.softnetwork.persistence.jdbc.query.PostgresSchemaProvider

object SchedulerPostgresLauncher extends SchedulerApi with PostgresSchemaProvider
