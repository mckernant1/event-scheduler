package com.mckernant1.scheduler.ddb

import com.amazonaws.services.dynamodbv2.AcquireLockOptions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBLockClient
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBLockClientOptions
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.mckernant1.extensions.executor.Executors.scheduleWithFixedDelay
import com.github.mckernant1.extensions.time.InstantUtils.isBeforeNow
import com.github.mckernant1.logging.Slf4j.logger
import com.mckernant1.scheduler.TimestampScheduler
import org.slf4j.Logger
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.BillingMode
import software.amazon.awssdk.services.dynamodb.model.ComparisonOperator
import software.amazon.awssdk.services.dynamodb.model.Condition
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement
import software.amazon.awssdk.services.dynamodb.model.KeyType
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 *
 */
class DynamoDBTimestampScheduler<T> @Throws(DynamoDbException::class) constructor(
    private val ddb: DynamoDbClient,
    private val clazz: Class<T>,
    private val executorService: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
    private val delay: Duration = Duration.ofSeconds(30),
    private val mapper: ObjectMapper = ObjectMapper(),
    leaseDurationSeconds: Long = 60,
    private val action: (Instant, T) -> Unit
) : TimestampScheduler<T> {

    private var scheduledFuture: ScheduledFuture<*>? = null

    private val lockClient = AmazonDynamoDBLockClient(
        AmazonDynamoDBLockClientOptions.builder(ddb, TIMESTAMP_TABLE_NAME)
            .withTimeUnit(TimeUnit.SECONDS)
            .withLeaseDuration(leaseDurationSeconds)
            .withHeartbeatPeriod(5)
            .withCreateHeartbeatBackgroundThread(true)
            .withPartitionKeyName(TIMESTAMP_ATTRIBUTE_NAME)
            .withSortKeyName(EVENT_ATTRIBUTE_NAME)
            .build()
    )

    init {
        try {
            ddb.createTable {
                it.tableName(TIMESTAMP_TABLE_NAME)
                it.billingMode(BillingMode.PAY_PER_REQUEST)
                it.attributeDefinitions(
                    AttributeDefinition.builder()
                        .attributeName(TIMESTAMP_ATTRIBUTE_NAME)
                        .attributeType(ScalarAttributeType.S)
                        .build(),
                    AttributeDefinition.builder()
                        .attributeName(EVENT_ATTRIBUTE_NAME)
                        .attributeType(ScalarAttributeType.S)
                        .build()
                )
                it.keySchema(
                    KeySchemaElement.builder()
                        .attributeName(TIMESTAMP_ATTRIBUTE_NAME)
                        .keyType(KeyType.HASH)
                        .build(),
                    KeySchemaElement.builder()
                        .attributeName(EVENT_ATTRIBUTE_NAME)
                        .keyType(KeyType.RANGE)
                        .build()
                )
            }
            ddb.waiter().waitUntilTableExists {
                it.tableName(TIMESTAMP_TABLE_NAME)
            }
        } catch (e: ResourceInUseException) {
            log.info("Timestamp table already exists")
        }
    }

    companion object {
        private const val TIMESTAMP_TABLE_NAME = "TimestampTable"
        private const val TIMESTAMP_ATTRIBUTE_NAME = "timestamp"
        private const val EVENT_ATTRIBUTE_NAME = "eventData"

        private val log: Logger = logger()
    }

    override fun put(timestamp: Instant, event: T) {
        val lockItem = lockClient.acquireLock(
            AcquireLockOptions.builder(timestamp.toEpochMilli().toString())
                .withSortKey(mapper.writeValueAsString(event))
                .withDeleteLockOnRelease(false)
                .build()
        )
        lockClient.releaseLock(lockItem)
    }

    override fun isEmpty(): Boolean = ddb.scan {
        it.tableName(TIMESTAMP_TABLE_NAME)
    }.count() == 0


    override fun start() {
        if (scheduledFuture != null) {
            throw IllegalStateException("DynamoDBTimestampScheduler has already been started. Stop DynamoDBTimestampScheduler before starting again")
        }

        scheduledFuture = executorService.scheduleWithFixedDelay(delay) {

            ddb.scanPaginator {
                it.tableName(TIMESTAMP_TABLE_NAME)
                it.scanFilter(
                    mapOf(
                        TIMESTAMP_ATTRIBUTE_NAME to Condition.builder()
                            .comparisonOperator(ComparisonOperator.LE)
                            .attributeValueList(AttributeValue.fromS(Instant.now().toEpochMilli().toString()))
                            .build()
                    )
                )
            }.items()
                .asSequence()
                .map { it[TIMESTAMP_ATTRIBUTE_NAME]?.s()!! to it[EVENT_ATTRIBUTE_NAME]?.s()!! }
                .map { (instant, event) ->
                    TimeScheduleEvent(
                        instant,
                        mapper.readValue(event, clazz),
                        lockClient.tryAcquireLock(
                            AcquireLockOptions.builder(instant)
                                .withSortKey(event)
                                .build()
                        )
                    )
                }
                .filter { it.lockItem.isPresent }
                .filter { it.instant.isBeforeNow() }
                .forEach { event ->
                    try {
                        action(event.instant, event.eventData)
                    } catch (e: Exception) {
                        log.error("Hit Exception while executing action", e)
                    }

                    try {
                        lockClient.releaseLock(event.lockItem.get())
                    } catch (e: Exception) {
                        log.error("Hit Exception while releasing the lock", e)
                    }
                }
        }
    }

    override fun stop() {
        if (scheduledFuture == null) {
            throw IllegalStateException("DynamoDBTimestampScheduler is not currently running. Start DynamoDBTimestampScheduler before stopping")
        }
        scheduledFuture!!.cancel(false)
        scheduledFuture = null
    }
}
