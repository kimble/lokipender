package com.github.kimble.lokipender

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.google.protobuf.Timestamp
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import logproto.Logproto
import logproto.PusherGrpc
import java.io.Closeable
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class Lokipender<E> : Closeable, CoroutineScope, AppenderBase<E>() {

    override val coroutineContext: CoroutineContext
        get() = Executors.newSingleThreadExecutor { r -> Thread(r, "lokipender") }.asCoroutineDispatcher() + job

    private val job: Job = Job()

    var gracefulShutdownMillis = 5000L

    var periodicFlushIntervalMillis = 5000L

    var actorChannelCapacity = 1000

    var maxBatchSize = 1000

    lateinit var host: String
    var port: Int = -1

    lateinit var componentName: String


    @Suppress("BlockingMethodInNonBlockingContext")
    private val actor = actor<Command>(coroutineContext, capacity = actorChannelCapacity) {
        val grpc = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build()

        val pusher = PusherGrpc.newBlockingStub(grpc)
        val buffer = mutableListOf<ILoggingEvent>()

        val flushStream: () -> Unit = {
            if (buffer.size > 0) {
                try {
                    pusher.push(Logproto.PushRequest.newBuilder()
                            .addStreams(Logproto.Stream.newBuilder()
                                    .setLabels("""{component="$componentName"}""")
                                    .addAllEntries(buffer.map { toProtobuf(it) })
                                    .build())
                            .build())
                } catch (ex: Exception) {
                    addError("Failed to push ${buffer.size} log entries", ex)
                }

                buffer.clear()
            }
        }

        launch(CoroutineName("Periodic lokipender flusher")) {
            while (isActive) {
                delay(periodicFlushIntervalMillis)
                channel.send(Command.Flush)
            }
        }

        channel.invokeOnClose {
            runBlocking {
                grpc.shutdown().awaitTermination(gracefulShutdownMillis, TimeUnit.MILLISECONDS)
            }
        }

        for (command in channel) {
            when (command) {
                is Command.Write -> {
                    buffer.add(command.event)

                    if (buffer.size >= maxBatchSize) {
                        flushStream()
                    }
                }

                is Command.Flush -> {
                    flushStream()
                }

                is Command.DrainAndClose -> {
                    flushStream()
                    channel.close()
                    command.deferred.complete(Unit)
                }
            }
        }
    }

    override fun stop() {
        try {
            runBlocking {
                try {
                    withTimeout(5000) {
                        val deferred = CompletableDeferred<Unit>()
                        actor.send(Command.DrainAndClose(deferred = deferred))
                        deferred.await()
                    }
                } finally {
                    job.cancelAndJoin()
                }
            }

            super.stop()
        } catch (ex: Exception) {
            addError("Failed to stop", ex)
        }
    }

    override fun append(eventObject: E) {
        if (eventObject is ILoggingEvent) {
            runBlocking {
                actor.send(Command.Write(eventObject))
            }
        }
    }

    override fun close() {
        stop()
    }

}


private fun toProtobuf(event: ILoggingEvent): Logproto.Entry {
    val timestamp = Instant.ofEpochMilli(event.timeStamp)
    val formattedLine = """level="${event.level.levelStr}" logger="${event.loggerName}" msg="${event.formattedMessage}" """

    return Logproto.Entry.newBuilder()
            .setTimestamp(Timestamp.newBuilder()
                    .setSeconds(timestamp.epochSecond)
                    .setNanos(timestamp.nano)
                    .build())
            .setLine(formattedLine)
            .build()
}

private sealed class Command {

    object Flush : Command()

    data class Write(val event: ILoggingEvent) : Command()

    class DrainAndClose(val deferred: CompletableDeferred<Unit>) : Command()

}