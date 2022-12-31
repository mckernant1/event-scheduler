package com.mckernant1.scheduler.ddb

import kotlin.random.Random
import java.util.UUID

data class TestEventData(
    val testString: String = UUID.randomUUID().toString(),
    val testNum: Int = Random.nextInt()
)
