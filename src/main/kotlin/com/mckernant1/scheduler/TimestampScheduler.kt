package com.mckernant1.scheduler

import java.time.Instant

interface TimestampScheduler<T> {

    fun put(timestamp: Instant, event: T)

    fun isEmpty(): Boolean

    fun start()

    fun stop()

}
