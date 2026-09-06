package com.aksharadasoha.ledger

import android.app.Application
import com.aksharadasoha.ledger.data.LedgerDatabase
import com.aksharadasoha.ledger.data.LedgerRepository

class LedgerApplication : Application() {
    lateinit var database: LedgerDatabase
        private set
    lateinit var repository: LedgerRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = LedgerDatabase.build(this)
        repository = LedgerRepository(this, database.dao(), database)
    }
}
