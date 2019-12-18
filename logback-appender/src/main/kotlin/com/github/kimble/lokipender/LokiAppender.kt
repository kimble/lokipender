package com.github.kimble.lokipender

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import ch.qos.logback.core.encoder.Encoder
import com.google.protobuf.Timestamp
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import logproto.Logproto
import logproto.PusherGrpc
import java.io.Closeable
import java.time.Instant
import java.util.concurrent.TimeUnit

class LokiAppender : Closeable, AppenderBase<ILoggingEvent>() {

    lateinit var host: String
    var port: Int = -1

    lateinit var componentName : String

    lateinit var encoder : Encoder<ILoggingEvent>

    private lateinit var channel: ManagedChannel

    private lateinit var pusher: PusherGrpc.PusherFutureStub


    override fun start() {
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build()

        pusher = PusherGrpc.newFutureStub(channel)

        super.start()
    }

    override fun stop() {
        channel.shutdown().awaitTermination(2, TimeUnit.SECONDS)
        super.stop()
    }

    override fun append(eventObject: ILoggingEvent) {
        write(
                timestamp = Instant.ofEpochMilli(eventObject.timeStamp),
                line = encoder.encode(eventObject).toString(Charsets.UTF_8)
        )
    }

    private fun write(timestamp: Instant, line: String) {
        val pushRequest = Logproto.PushRequest.newBuilder()
                .addStreams(Logproto.Stream.newBuilder()
                        .setLabels("""{component="$componentName"}""")
                        .addEntries(Logproto.Entry.newBuilder()
                                .setTimestamp(Timestamp.newBuilder()
                                        .setSeconds(timestamp.epochSecond)
                                        .setNanos(timestamp.nano)
                                        .build())
                                .setLine(line)
                                .build())
                        .build())
                .build()

        val promise = pusher.push(pushRequest)

        try {
            promise.get()
        } catch (ex: Exception) {
            addError("Loki append failure", ex)
        }
    }

    override fun close() {
        stop()
    }

}