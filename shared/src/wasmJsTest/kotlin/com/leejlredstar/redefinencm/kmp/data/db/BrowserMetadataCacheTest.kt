package com.leejlredstar.redefinencm.kmp.data.db

import app.cash.sqldelight.db.SqlDriver
import com.leejlredstar.redefinencm.kmp.data.MetadataCacheContract

class BrowserMetadataCacheTest : MetadataCacheContract() {
    override fun createDriver(): SqlDriver = BrowserStorageSqlDriver(object : BrowserDatabaseStorage {
        private val values = linkedMapOf<String, String>()
        override fun keys(): List<String> = values.keys.toList()
        override fun get(key: String): String? = values[key]
        override fun set(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
    })
}
