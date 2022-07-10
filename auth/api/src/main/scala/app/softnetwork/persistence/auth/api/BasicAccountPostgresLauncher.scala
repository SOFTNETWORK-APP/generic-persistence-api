package app.softnetwork.persistence.auth.api

import app.softnetwork.persistence.jdbc.query.PostgresSchemaProvider

object BasicAccountPostgresLauncher extends BasicAccountApi with PostgresSchemaProvider
