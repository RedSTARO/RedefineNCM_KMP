package com.leejlredstar.redefinencm.kmp.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

class JvmMetadataCacheTest : MetadataCacheContract() {
    override fun createDriver(): SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
}
