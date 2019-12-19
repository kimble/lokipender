package com.github.kimble.lokipender

import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class LokipenderTest {

    private val log : Logger = LoggerFactory.getLogger(LokipenderTest::class.java)

    @Test
    internal fun name2() {
        repeat(1000000) {
            log.info("Logging line number $it to Loki")
        }
    }

}