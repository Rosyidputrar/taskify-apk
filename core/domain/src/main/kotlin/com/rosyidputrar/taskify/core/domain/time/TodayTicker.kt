package com.rosyidputrar.taskify.core.domain.time

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/** Tanggal hari ini (WIB) yang berganti sendiri tepat setelah tengah malam. */
fun todayFlow(): Flow<String> = flow {
    while (true) {
        emit(Dates.today())
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val wibOffset = 7 * 3_600_000L
        delay(day - ((now + wibOffset) % day) + 250)
    }
}.distinctUntilChanged()
