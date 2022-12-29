package com.mckernant1.scheduler.ddb

import com.amazonaws.services.dynamodbv2.LockItem
import java.time.Instant
import java.util.Optional

internal data class TimeScheduleEvent<T>(
    val instant: Instant,
    val eventData: T,
    val lockItem: Optional<LockItem>
) {

    constructor(
        instant: String,
        event: T,
        lockItem: Optional<LockItem>
    ) : this(
        Instant.ofEpochMilli(instant.toLong()),
        event,
        lockItem
    )

}
