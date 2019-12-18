package com.github.kimble.lokipender

import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class LokiAppenderTest {

    private val log : Logger = LoggerFactory.getLogger(LokiAppenderTest::class.java)



    @Test
    internal fun name2() {
        repeat(100) {
            log.info("Logging line number $it to Loki")
        }
    }

}