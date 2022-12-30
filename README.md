# Event Scheduler
This is a library for time based event scheduling. 


### Use will gradle

```
repositories {
    maven(uri("https://mvn.mckernant1.com/release"))
}
```

```
dependencies {
    implementation("com.mckernant1:event-scheduler:0.0.1")
}
```

### How to use

```kotlin
fun main() {

    val ddb = DynamoDbClient.create()

    val scheduler = DynamoDBTimestampScheduler<Data>(
        ddb = ddb,
        clazz = Data::class.java,
        executorService = Executors.newSingleThreadScheduledExecutor(),
        delay = Duration.ofSeconds(5),
        mapper = ObjectMapper(),
        leaseDurationSeconds = 60
    ) { instant: Instant, event: Data ->
        println("$instant - $event")
    }

    scheduler.start()

    scheduler.put(Instant.now() + Duration.ofMinutes(5), Data("Yo", 1))


}

data class Data(
    val string: String,
    val int: Int
)
```
