package com.github.kimble.lokipender

import org.junit.jupiter.api.Test


class LokiAppenderTest {


    @Test
    fun name() {
        LokiAppender("http://localhost:3100").use { appender ->
            appender.start()

            repeat(1000) {
                appender.write("Hello loki $it!")
            }
        }
    }
}