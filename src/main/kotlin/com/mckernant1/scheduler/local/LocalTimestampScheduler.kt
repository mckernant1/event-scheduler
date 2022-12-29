package com.mckernant1.scheduler.local

import com.github.mckernant1.extensions.executor.Executors.scheduleWithFixedDelay
import com.github.mckernant1.extensions.time.DurationFormat.format
import com.github.mckernant1.extensions.time.InstantUtils.isAfterNow
import com.github.mckernant1.extensions.time.InstantUtils.isBeforeNow
import com.github.mckernant1.logging.Slf4j.logger
import com.mckernant1.scheduler.TimestampScheduler
import org.slf4j.Logger
import java.time.Duration
import java.time.Instant
import java.util.Collections
import java.util.SortedMap
import java.util.TreeMap
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor

/**
 * This is a job scheduler that does jobs at a specific time.
 * its admittedly not great for long term jobs because restart will remove all items for the heap
 *
 * @param executorService for starting the
 * @param delay time between checking for times to work on
 * @param action the action to take on expired items. Taking in the instant and object
 */
class LocalTimestampScheduler<T>(
    private val executorService: ScheduledThreadPoolExecutor,
    private val delay: Duration,
    private val action: (Instant, T) -> Unit
) : TimestampScheduler<T> {

    private val logger: Logger = logger()
    private val timestampHeap: SortedMap<Instant, MutableList<T>> = Collections.synchronizedSortedMap(TreeMap())
    private var scheduledFuture: ScheduledFuture<*>? = null

    @Synchronized
    override fun put(timestamp: Instant, event: T) {
        val l: MutableList<T> = timestampHeap.getOrDefault(timestamp, mutableListOf())
        l.add(event)
        timestampHeap[timestamp] = l
    }

    override fun isEmpty(): Boolean = timestampHeap.isEmpty()

    @Synchronized
    override fun start() {
        if (scheduledFuture != null) {
            throw IllegalStateException("LocalTimestampScheduler has already been started. Stop LocalTimestampScheduler before starting again")
        }

        scheduledFuture = executorService.scheduleWithFixedDelay(delay) {
            logger.debug("Checking to see if there is a job ready for execution")
            if (timestampHeap.firstKey().isAfterNow()) {
                logger.debug("First entry in TS heap is after now. Waiting ${delay.format()} before checking again")
                return@scheduleWithFixedDelay
            }

            logger.info("There is at least one event scheduled to occur")

            val entriesToActOn: List<Map.Entry<Instant, MutableList<T>>> = timestampHeap.entries
                .takeWhile { (ts, _) -> ts.isBeforeNow() }

            for ((ts, events) in entriesToActOn) {
                try {
                    logger.debug("Starting event for timestamp: $ts")
                    events.forEach { action(ts, it) }
                } catch (e: Exception) {
                    logger.warn("Hit Exception while executing action", e)
                } finally {
                    timestampHeap.remove(ts)
                }
            }
        }
    }

    @Synchronized
    override fun stop() {
        if (scheduledFuture == null) {
            throw IllegalStateException("LocalTimestampScheduler is not currently running. Start LocalTimestampScheduler before stopping")
        }
        scheduledFuture!!.cancel(false)
        scheduledFuture = null
    }


}
