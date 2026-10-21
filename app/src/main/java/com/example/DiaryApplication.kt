package com.example

import android.app.Application
import com.example.data.repository.DiaryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DiaryApplication : Application() {

    lateinit var repository: DiaryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = DiaryRepository(this)

        CoroutineScope(Dispatchers.IO).launch {
            repository.seedInitialDataIfEmpty()
        }
    }
}
