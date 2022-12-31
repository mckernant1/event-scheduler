import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `maven-publish`
    signing
    `java-library`
    kotlin("jvm") version "1.7.20"
}

group = "com.mckernant1"
version = "0.0.2"

repositories {
    mavenCentral()
    maven(uri("https://jitpack.io"))
    maven(uri("https://mvn.mckernant1.com/release"))
}

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.5")

    // Dumb stupid https://github.com/awslabs/amazon-dynamodb-lock-client/issues/33
    implementation("com.github.awslabs:amazon-dynamodb-lock-client:4b99836f7602d5e0691a5a6b27c7dba6886da901")

    implementation("com.github.mckernant1:kotlin-utils:0.0.35")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.14.0")

    implementation(platform("software.amazon.awssdk:bom:2.19.4"))
    implementation("software.amazon.awssdk:dynamodb")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.4")


}

tasks.test {
    useJUnitPlatform {
        excludeTags("integration")
    }
}

tasks.register<Test>("test-integration") {
    useJUnitPlatform {
        includeTags("integration")
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}


publishing {
    repositories {
        maven {
            url = uri("s3://mvn.mckernant1.com/release")
            authentication {
                register("awsIm", AwsImAuthentication::class.java)
            }
        }
    }

    publications {
        create<MavenPublication>("default") {
            artifactId = "event-scheduler"
            from(components["kotlin"])
            val sourcesJar by tasks.creating(Jar::class) {
                val sourceSets: SourceSetContainer by project
                from(sourceSets["main"].allSource)
                archiveClassifier.set("sources")
            }
            artifact(sourcesJar)
            pom {
                name.set("event-scheduler")
                description.set("Event Scheduling")
                url.set("https://github.com/mckernant1/event-scheduler")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("mckernant1")
                        name.set("Tom McKernan")
                        email.set("tmeaglei@gmail.com")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/mckernant1/event-scheduler.git")
                    developerConnection.set("scm:git:ssh://github.com/mckernant1/event-scheduler.git")
                    url.set("https://github.com/mckernant1/event-scheduler")
                }
            }
        }
    }
}
