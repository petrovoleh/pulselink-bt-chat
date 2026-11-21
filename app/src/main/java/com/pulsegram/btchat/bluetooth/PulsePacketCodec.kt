package com.pulsegram.btchat.bluetooth

import android.os.ParcelUuid
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.math.ceil

/**
 * Compact binary protocol codec for Pulsegram over Bluetooth LE (both Legacy 1M and Extended Coded PHY).
 *
 * Header format (11 bytes):
 * - Byte 0:     Magic (0x50 = 'P')
 * - Byte 1:     Flags & Type:
 *               bits 0..2: PacketType (0 = BEACON, 1 = CHAT_MSG, 2 = ACK)
 *               bit 3:     Sender supports LE Coded PHY (Long Range)
 *               bit 4:     Transmitted on Extended Coded PHY set
 *               bits 5..6: Remaining Mesh Hops (TTL 0..3)
 *               bit 7:     Reserved
 * - Bytes 2..3: Sender Node ID (UInt16, Big Endian)
 * - Bytes 4..5: Target Node ID (UInt16, Big Endian, 0xFFFF = Broadcast to all)
 * - Bytes 6..7: Message ID (UInt16, Big Endian)
 * - Byte 8:     Chunk Index (0..255)
 * - Byte 9:     Total Chunks (1..255)
 * - Byte 10:    Sender Nickname Length in bytes (0..10)
 * - Bytes 11..(11+nickLen-1): Sender Nickname (UTF-8)
 * - Remaining:  UTF-8 Message Chunk Payload (or ACK info)
 */
object PulsePacketCodec {

    val PULSEGRAM_SERVICE_UUID: UUID = UUID.fromString("0000b17c-0000-1000-8000-00805f9b34fb")
    val PULSEGRAM_PARCEL_UUID: ParcelUuid = ParcelUuid(PULSEGRAM_SERVICE_UUID)

    const val BROADCAST_NODE_ID: Int = 0xFFFF
    private const val MAGIC_BYTE: Byte = 0x50 // 'P'
    private const val HEADER_FIXED_SIZE = 11

    // Legacy 1M advertisement Service Data payload limit is ~24 bytes total:
    // 11 bytes fixed header + up to 5 bytes nickname + up to 8 bytes text per chunk.
    const val MAX_PAYLOAD_LEGACY_1M = 23

    // Extended Advertising (Coded PHY S=8 or Extended 1M) supports up to 220 bytes safely:
    // 11 bytes header + up to 10 bytes nickname + up to 180 bytes text in a SINGLE unfragmented packet!
    const val MAX_PAYLOAD_EXTENDED = 200

    enum class PacketType(val id: Int) {
        BEACON(0),
        CHAT_MSG(1),
        ACK(2),
        KEY_EXCHANGE(3);

        companion object {
            fun fromId(id: Int): PacketType = entries.firstOrNull { it.id == id } ?: BEACON
        }
    }

    data class DecodedFrame(
        val type: PacketType,
        val senderSupportsCoded: Boolean,
        val sentViaCodedSet: Boolean,
        val ttlHops: Int,
        val senderNodeId: Int,
        val targetNodeId: Int,
        val msgId: Int,
        val chunkIndex: Int,
        val totalChunks: Int,
        val senderNickname: String,
        val payloadBytes: ByteArray
    ) {
        val payloadText: String
            get() = String(payloadBytes, StandardCharsets.UTF_8)
    }

    /**
     * Splits a chat message into one or more binary frames that fit into either
     * a Legacy 1M advertisement (23 bytes max) or an Extended Coded advertisement (200 bytes max).
     */
    fun encodeChatMessageFrames(
        senderNodeId: Int,
        senderNickname: String,
        targetNodeId: Int,
        msgId: Int,
        messageText: String,
        senderSupportsCoded: Boolean,
        sentViaCodedSet: Boolean,
        ttlHops: Int = 2,
        useExtendedPayloadSize: Boolean
    ): List<ByteArray> {
        // If the message is a photo (`IMG1:...`) or large encrypted payload (`ENC1:...` > 300 chars),
        // keep the chunk header nickname compact (max 6 bytes) so each Extended 220B packet fits 203 bytes!
        val isLargePayload = messageText.startsWith("IMG1:") || messageText.startsWith("IMGB:") || messageText.length > 300
        val maxTotalBytes = if (useExtendedPayloadSize) {
            if (isLargePayload) 220 else MAX_PAYLOAD_EXTENDED
        } else {
            MAX_PAYLOAD_LEGACY_1M
        }
        val maxNickBytes = if (useExtendedPayloadSize) {
            if (isLargePayload) 6 else 24
        } else {
            0
        }
        val nickBytes = if (maxNickBytes > 0) {
            trimUtf8Bytes(senderNickname.trim().ifEmpty { "User" }, maxNickBytes)
        } else {
            ByteArray(0)
        }

        val maxChunkBytes = (maxTotalBytes - HEADER_FIXED_SIZE - nickBytes.size).coerceAtLeast(6)
        val fullTextBytes = messageText.toByteArray(StandardCharsets.UTF_8)

        if (fullTextBytes.isEmpty()) {
            return listOf(
                buildFrame(
                    type = PacketType.CHAT_MSG,
                    senderSupportsCoded = senderSupportsCoded,
                    sentViaCodedSet = sentViaCodedSet,
                    ttlHops = ttlHops,
                    senderNodeId = senderNodeId,
                    targetNodeId = targetNodeId,
                    msgId = msgId,
                    chunkIndex = 0,
                    totalChunks = 1,
                    nickBytes = nickBytes,
                    chunkBytes = ByteArray(0)
                )
            )
        }

        val totalChunks = ceil(fullTextBytes.size.toDouble() / maxChunkBytes.toDouble())
            .toInt()
            .coerceIn(1, 250)

        val frames = ArrayList<ByteArray>(totalChunks)
        for (idx in 0 until totalChunks) {
            val start = idx * maxChunkBytes
            val end = (start + maxChunkBytes).coerceAtMost(fullTextBytes.size)
            if (start >= end) break
            val slice = fullTextBytes.copyOfRange(start, end)
            frames.add(
                buildFrame(
                    type = PacketType.CHAT_MSG,
                    senderSupportsCoded = senderSupportsCoded,
                    sentViaCodedSet = sentViaCodedSet,
                    ttlHops = ttlHops,
                    senderNodeId = senderNodeId,
                    targetNodeId = targetNodeId,
                    msgId = msgId,
                    chunkIndex = idx,
                    totalChunks = totalChunks,
                    nickBytes = nickBytes,
                    chunkBytes = slice
                )
            )
        }
        return frames
    }

    /**
     * Encodes a Selective-ACK / Full-ACK frame:
     * - If `receivedChunkIndices` is null or equals `0 until totalChunks`, `payloadBytes = [0x01]` (Full ACK).
     * - If partial chunks are received, `payloadBytes` starts with `0x02` followed by the list of
     *   already-received chunk indices (`0..254`), so the sender ONLY retransmits the missing chunks!
     */
    fun encodeAckFrame(
        ackSenderNodeId: Int,
        ackSenderNickname: String,
        originalSenderNodeId: Int,
        ackedMsgId: Int,
        senderSupportsCoded: Boolean,
        sentViaCodedSet: Boolean,
        ttlHops: Int = 2,
        isFullAck: Boolean = true,
        receivedChunksList: ByteArray = byteArrayOf()
    ): ByteArray {
        val nickBytes = trimUtf8Bytes(ackSenderNickname.trim().ifEmpty { "Peer" }, 8)
        val payload = if (isFullAck) {
            byteArrayOf(0x01)
        } else {
            ByteArray(1 + receivedChunksList.size).also {
                it[0] = 0x02 // Selective-ACK marker
                System.arraycopy(receivedChunksList, 0, it, 1, receivedChunksList.size)
            }
        }
        return buildFrame(
            type = PacketType.ACK,
            senderSupportsCoded = senderSupportsCoded,
            sentViaCodedSet = sentViaCodedSet,
            ttlHops = ttlHops,
            senderNodeId = ackSenderNodeId,
            targetNodeId = originalSenderNodeId,
            msgId = ackedMsgId,
            chunkIndex = 0,
            totalChunks = 1,
            nickBytes = nickBytes,
            chunkBytes = payload
        )
    }

    /**
     * Encodes a lightweight discovery beacon frame so nearby devices see this phone's
     * nickname, node ID, and whether it supports Extended Range (Coded PHY).
     */
    fun encodeBeaconFrame(
        senderNodeId: Int,
        senderNickname: String,
        senderSupportsCoded: Boolean,
        sentViaCodedSet: Boolean,
        rollingSeq: Int = 0,
        maxNickBytes: Int = 24
    ): ByteArray {
        val nickBytes = trimUtf8Bytes(senderNickname.trim().ifEmpty { "User" }, maxNickBytes)
        return buildFrame(
            type = PacketType.BEACON,
            senderSupportsCoded = senderSupportsCoded,
            sentViaCodedSet = sentViaCodedSet,
            ttlHops = 1,
            senderNodeId = senderNodeId,
            targetNodeId = BROADCAST_NODE_ID,
            msgId = rollingSeq and 0xFFFF,
            chunkIndex = 0,
            totalChunks = 1,
            nickBytes = nickBytes,
            chunkBytes = ByteArray(0)
        )
    }

    /**
     * Encodes a compact 33-byte compressed P-256 EC Public Key broadcast frame so nearby peers
     * automatically establish AES-128-GCM End-to-End Encryption without any user setup!
     */
    fun encodeKeyExchangeFrame(
        senderNodeId: Int,
        senderNickname: String,
        compressedPubKey33: ByteArray,
        senderSupportsCoded: Boolean,
        sentViaCodedSet: Boolean,
        rollingSeq: Int = 0
    ): ByteArray {
        val nickBytes = trimUtf8Bytes(senderNickname.trim().ifEmpty { "User" }, 24)
        return buildFrame(
            type = PacketType.KEY_EXCHANGE,
            senderSupportsCoded = senderSupportsCoded,
            sentViaCodedSet = sentViaCodedSet,
            ttlHops = 1,
            senderNodeId = senderNodeId,
            targetNodeId = BROADCAST_NODE_ID,
            msgId = rollingSeq and 0xFFFF,
            chunkIndex = 0,
            totalChunks = 1,
            nickBytes = nickBytes,
            chunkBytes = compressedPubKey33
        )
    }

    /**
     * Decodes a raw Service Data byte array into a [DecodedFrame], or returns null if invalid.
     */
    fun decodeFrame(raw: ByteArray?): DecodedFrame? {
        if (raw == null || raw.size < HEADER_FIXED_SIZE) return null
        if (raw[0] != MAGIC_BYTE) return null

        val flags = raw[1].toInt() and 0xFF
        val packetType = PacketType.fromId(flags and 0x07)
        val senderSupportsCoded = (flags and (1 shl 3)) != 0
        val sentViaCodedSet = (flags and (1 shl 4)) != 0
        val ttlHops = (flags ushr 5) and 0x03
        // Bit 7 (0x80) is our rolling anti-duplicate toggle bit!

        val buffer = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN)
        val senderNodeId = buffer.getShort(2).toInt() and 0xFFFF
        val targetNodeId = buffer.getShort(4).toInt() and 0xFFFF
        val msgId = buffer.getShort(6).toInt() and 0xFFFF
        val chunkIndex = raw[8].toInt() and 0xFF
        val totalChunks = (raw[9].toInt() and 0xFF).coerceAtLeast(1)
        val nickLen = (raw[10].toInt() and 0xFF).coerceAtMost(raw.size - HEADER_FIXED_SIZE)

        if (HEADER_FIXED_SIZE + nickLen > raw.size) return null

        val nickname = String(raw, HEADER_FIXED_SIZE, nickLen, StandardCharsets.UTF_8)
        val payloadStart = HEADER_FIXED_SIZE + nickLen
        val payloadBytes = if (payloadStart < raw.size) {
            raw.copyOfRange(payloadStart, raw.size)
        } else {
            ByteArray(0)
        }

        return DecodedFrame(
            type = packetType,
            senderSupportsCoded = senderSupportsCoded,
            sentViaCodedSet = sentViaCodedSet,
            ttlHops = ttlHops,
            senderNodeId = senderNodeId,
            targetNodeId = targetNodeId,
            msgId = msgId,
            chunkIndex = chunkIndex,
            totalChunks = totalChunks,
            senderNickname = nickname,
            payloadBytes = payloadBytes
        )
    }

    /**
     * Creates a decremented-TTL copy of a raw frame for Mesh Relay forwarding.
     */
    fun decrementTtlForRelay(raw: ByteArray): ByteArray? {
        if (raw.size < HEADER_FIXED_SIZE || raw[0] != MAGIC_BYTE) return null
        val flags = raw[1].toInt() and 0xFF
        val currentTtl = (flags ushr 5) and 0x03
        if (currentTtl <= 0) return null
        val newTtl = currentTtl - 1
        val newFlags = (flags and 0x1F) or ((newTtl and 0x03) shl 5)
        val copy = raw.copyOf()
        copy[1] = newFlags.toByte()
        return copy
    }

    private fun buildFrame(
        type: PacketType,
        senderSupportsCoded: Boolean,
        sentViaCodedSet: Boolean,
        ttlHops: Int,
        senderNodeId: Int,
        targetNodeId: Int,
        msgId: Int,
        chunkIndex: Int,
        totalChunks: Int,
        nickBytes: ByteArray,
        chunkBytes: ByteArray
    ): ByteArray {
        val totalSize = HEADER_FIXED_SIZE + nickBytes.size + chunkBytes.size
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)

        var flags = type.id and 0x07
        if (senderSupportsCoded) flags = flags or (1 shl 3)
        if (sentViaCodedSet) flags = flags or (1 shl 4)
        flags = flags or ((ttlHops.coerceIn(0, 3) and 0x03) shl 5)

        buffer.put(MAGIC_BYTE)
        buffer.put(flags.toByte())
        buffer.putShort((senderNodeId and 0xFFFF).toShort())
        buffer.putShort((targetNodeId and 0xFFFF).toShort())
        buffer.putShort((msgId and 0xFFFF).toShort())
        buffer.put((chunkIndex and 0xFF).toByte())
        buffer.put((totalChunks and 0xFF).toByte())
        buffer.put((nickBytes.size and 0xFF).toByte())
        buffer.put(nickBytes)
        buffer.put(chunkBytes)

        return buffer.array()
    }

    private fun trimUtf8Bytes(input: String, maxBytes: Int): ByteArray {
        val raw = input.toByteArray(StandardCharsets.UTF_8)
        if (raw.size <= maxBytes) return raw
        var len = maxBytes
        // Avoid splitting multi-byte UTF-8 characters mid-sequence
        while (len > 0 && (raw[len].toInt() and 0xC0) == 0x80) {
            len--
        }
        return raw.copyOfRange(0, len)
    }
}
