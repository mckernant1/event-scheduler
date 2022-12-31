package com.mckernant1.scheduler.ddb

import com.github.mckernant1.extensions.time.InstantUtils.isBeforeNow
import com.github.mckernant1.standalone.delay
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.assertTimeout
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.time.Duration
import java.time.Instant

@Tag("integration")
@TestMethodOrder(MethodOrderer.Random::class)
internal class DynamoDBTimestampSchedulerTest {

    private val ddb = DynamoDbClient.builder()
        .region(Region.US_EAST_2)
        .build()

    @Test
    fun testFullRun(): Unit = runBlocking {
        val sampleData = TestEventData()
        val now: Instant = Instant.now()
        val instants: MutableList<Instant> = mutableListOf()
        val events: MutableSet<TestEventData> = mutableSetOf()

        val ts: DynamoDBTimestampScheduler<TestEventData> = DynamoDBTimestampScheduler(
            ddb,
            TestEventData::class.java,
            delay = Duration.ofSeconds(1)
        ) { instant, event ->
            instants.add(instant)
            events.add(event)
        }

        for (i in 0..30) {
            ts.put(now + Duration.ofSeconds(i.toLong()), sampleData)
        }

        ts.start()

        assertTimeout(Duration.ofSeconds(45)) {
            while (!ts.isEmpty()) {
                Thread.sleep(Duration.ofSeconds(5).toMillis())
            }
        }
        assertEquals(1, events.size)
        assertTrue(instants.all { it.isBeforeNow() })
    }

    @Test
    fun testMultiple() {
        val now: Instant = Instant.now()
        val instants: MutableList<Instant> = mutableListOf()
        val events: MutableSet<TestEventData> = mutableSetOf()

        val ts: DynamoDBTimestampScheduler<TestEventData> = DynamoDBTimestampScheduler(
            ddb,
            TestEventData::class.java,
            delay = Duration.ofSeconds(1)
        ) { instant, event ->
            instants.add(instant)
            events.add(event)
        }

        val ts1: DynamoDBTimestampScheduler<TestEventData> = DynamoDBTimestampScheduler(
            ddb,
            TestEventData::class.java,
            delay = Duration.ofSeconds(1)
        ) { instant, event ->
            instants.add(instant)
            events.add(event)
        }

        for (i in 0..30) {
            ts1.put(now + Duration.ofSeconds(i.toLong()), TestEventData())
        }

        for (i in 0..30) {
            ts.put(now + Duration.ofSeconds(i.toLong()), TestEventData())
        }

        ts.start()

        assertTimeout(Duration.ofSeconds(45)) {
            while (!ts.isEmpty()) {
                Thread.sleep(Duration.ofSeconds(5).toMillis())
            }
        }
        assertEquals(62, events.size)
        assertEquals(62, instants.size)
        assertTrue(instants.all { it.isBeforeNow() })
    }

}
