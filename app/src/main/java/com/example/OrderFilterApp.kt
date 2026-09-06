package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.repository.OrderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class OrderFilterApp : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy {
        AppDatabase.getDatabase(this, applicationScope)
    }

    val repository by lazy {
        OrderRepository.getInstance(this, database, applicationScope)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.example.util.NotificationHelper.createNotificationChannel(this)
    }

    companion object {
        lateinit var instance: OrderFilterApp
            private set
    }
}
