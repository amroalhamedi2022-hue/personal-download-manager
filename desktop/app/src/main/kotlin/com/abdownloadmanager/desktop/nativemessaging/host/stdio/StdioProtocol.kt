package com.abdownloadmanager.desktop.nativemessaging.host.stdio

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * A malformed length prefix cannot be recovered by reading the next frame:
 * the following bytes may belong to the rejected payload, not a new header.
 */
class InvalidNativeMessagingFrameException(message: String) : IOException(message)

/** A complete frame with invalid UTF-8 may be rejected without losing framing. */
class InvalidNativeMessagingPayloadException(message: String, cause: Throwable) : IllegalArgumentException(message, cause)

/**
 * Native Messaging Protocol implementation for stdio communication.
 * 
 * The protocol uses a 4-byte unsigned integer (in native byte order) to indicate 
 * the message length, followed by the JSON message content.
 * 
 * Chrome limit: 1MB
 * Firefox limit: 4MB
 */
class StdioProtocol(
    private val input: InputStream,
    private val output: OutputStream,
) {
    companion object {
        private const val MAX_MESSAGE_SIZE = 1024 * 1024 * 4 // 4MB (Firefox limit)
        private const val MESSAGE_LENGTH_SIZE = 4
    }

    /**
     * Reads a single message from stdin.
     * 
     * @return The JSON message as a string, or null if EOF is reached
     * @throws EOFException if stream ends unexpectedly
     * @throws InvalidNativeMessagingFrameException if the length prefix is invalid; the host must close the connection
     * @throws InvalidNativeMessagingPayloadException if a fully received frame contains malformed UTF-8
     */
    fun readMessage(): String {
        val messageLength = readLength(input)

        // Validate message size
        if (messageLength !in 1..MAX_MESSAGE_SIZE) {
            throw InvalidNativeMessagingFrameException(
                "Invalid message size: $messageLength (max: $MAX_MESSAGE_SIZE)"
            )
        }

        // Read message content
        val messageBytes = ByteArray(messageLength)
        val messageBytesRead = input.readNBytes(messageBytes, 0, messageLength)

        if (messageBytesRead < messageLength) {
            throw EOFException(
                "Unexpected end of stream while reading message content " +
                        "(expected: $messageLength, got: $messageBytesRead)"
            )
        }

        // The String(byte[], UTF_8) constructor silently replaces malformed
        // sequences with U+FFFD. Native messaging is UTF-8 JSON: reject such
        // payloads instead of changing browser-supplied request bytes.
        // Unlike a bad length prefix, the complete frame was consumed and
        // the host may report this error and safely read the next frame.
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(messageBytes))
                .toString()
        } catch (e: CharacterCodingException) {
            throw InvalidNativeMessagingPayloadException("Native messaging payload is not valid UTF-8", e)
        }
    }

    /**
     * Writes a message to stdout.
     * 
     * @param message The JSON message to send
     * @throws IllegalArgumentException if message exceeds size limit
     */
    fun writeMessage(message: String) {
        val messageBytes = message.toByteArray(Charsets.UTF_8)
        val messageLength = messageBytes.size

        // Validate message size
        if (messageLength > MAX_MESSAGE_SIZE) {
            throw IllegalArgumentException(
                "Message size exceeds limit: $messageLength (max: $MAX_MESSAGE_SIZE)"
            )
        }

        // Write 4-byte length prefix in native byte order
        val lengthBuffer = ByteBuffer.allocate(4)
            .order(ByteOrder.nativeOrder())
            .putInt(messageLength)

        output.write(lengthBuffer.array())

        // Write message content
        output.write(messageBytes)
        output.flush()
    }

    fun readLength(input: InputStream): Int {
        // Read 4-byte length prefix
        val lengthBytes = ByteArray(MESSAGE_LENGTH_SIZE)
        val bytesRead = input.readNBytes(lengthBytes, 0, 4)

        if (bytesRead == 0) {
            // EOF reached, browser disconnected
            throw NativeMessagingFinished()
        }

        if (bytesRead < 4) {
            throw EOFException("Unexpected end of stream while reading message length")
        }

        // Convert to unsigned int using native byte order
        return ByteBuffer.wrap(lengthBytes)
            .order(ByteOrder.nativeOrder())
            .int
    }
}
