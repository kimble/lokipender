package com.github.kimble.lokipender

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.google.protobuf.Timestamp
import logproto.Logproto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xerial.snappy.Snappy
import java.io.Closeable
import java.time.Instant
import java.util.concurrent.TimeUnit

class LokiAppender(
        val lokiRootUri: String

) : Closeable, AppenderBase<ILoggingEvent>() {


    private lateinit var ok: OkHttpClient

    private val pushRequestBuilder = Request.Builder()
            .url("$lokiRootUri/loki/api/v1/push")
            .build()

    private val protobuf = "application/x-protobuf".toMediaType()

    override fun start() {
        ok = OkHttpClient.Builder()
                .callTimeout(2, TimeUnit.SECONDS)
                .build()

        super.start()
    }

    override fun stop() {
        ok.dispatcher.executorService.shutdown();
        ok.connectionPool.evictAll();
        ok.cache?.close();

        super.stop()
    }

    override fun append(eventObject: ILoggingEvent?) {

        val request = Logproto.PushRequest.newBuilder()
                .build()


    }

    override fun close() {
        stop()
    }

    fun write(line: String) {
        val timestamp = Instant.now()

        val pushRequest = Logproto.PushRequest.newBuilder()
                .addStreams(Logproto.Stream.newBuilder()
                        .setLabels("""{component="junit"}""")
                        .addEntries(Logproto.Entry.newBuilder()
                                .setTimestamp(Timestamp.newBuilder()
                                        .setSeconds(timestamp.epochSecond)
                                        .setNanos(timestamp.nano)
                                        .build())
                                .setLine(line)
                                .build())
                        .build())
                .build()

        val uncompressed = pushRequest.toByteArray()
        val compressed = Snappy.compress(uncompressed)

        println("${uncompressed.size} -> ${compressed.size} bytes")

        val call = ok.newCall(pushRequestBuilder.newBuilder()
                .post(compressed.toRequestBody(protobuf))
                .build())


        call.execute().use {
            println(" -> $it: ${it.body?.string()}")
        }

    }

}