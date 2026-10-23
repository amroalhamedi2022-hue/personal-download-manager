package com.abdownloadmanager.desktop.nativemessaging.host.stdio

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.CharBuffer
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
 * Browser-to-host messages are capped locally at 4 MiB to limit allocations.
 * Host-to-browser messages must not exceed 1 MiB (Chrome, Edge and Firefox).
 */
class StdioProtocol(
    private val input: InputStream,
    private val output: OutputStream,
) {
    companion object {
        private const val MAX_INBOUND_MESSAGE_SIZE = 1024 * 1024 * 4
        private const val MAX_OUTBOUND_MESSAGE_SIZE = 1024 * 1024
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
        if (messageLength !in 1..MAX_INBOUND_MESSAGE_SIZE) {
            throw InvalidNativeMessagingFrameException(
                "Invalid message size: $messageLength (max: $MAX_INBOUND_MESSAGE_SIZE)"
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
     * @throws IllegalArgumentException if the encoded message exceeds the browser limit
     * or the input contains malformed UTF-16 (such as an unpaired surrogate)
     */
    fun writeMessage(message: String) {
        // Even ASCII would exceed the browser's outbound limit. Reject before
        // allocating an encoded buffer; multibyte content is checked below.
        require(message.length <= MAX_OUTBOUND_MESSAGE_SIZE) {
            "Message character count exceeds browser limit: ${message.length}"
        }
        // String.toByteArray(UTF_8) silently replaces unpaired surrogates.
        // Never mutate a native-messaging JSON response on its way to a browser.
        val encoded = try {
            Charsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(message))
        } catch (e: CharacterCodingException) {
            throw IllegalArgumentException("Native messaging response is not valid UTF-16", e)
        }
        val messageLength = encoded.remaining()
        require(messageLength <= MAX_OUTBOUND_MESSAGE_SIZE) {
            "Message size exceeds browser limit: $messageLength (max: $MAX_OUTBOUND_MESSAGE_SIZE)"
        }
        val messageBytes = ByteArray(messageLength)
        encoded.get(messageBytes)

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
