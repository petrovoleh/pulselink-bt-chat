package com.pulsegram.btchat.model

import android.bluetooth.BluetoothDevice
import com.pulsegram.btchat.util.UltraImageCompressor

/**
 * Radio mode selected by the user in Pulsegram:
 * - STANDARD_1M: Uses Bluetooth LE 1M PHY (compatible with all BLE devices, ~30-100m)
 * - EXTENDED_CODED: Uses Bluetooth 5.0+ LE Coded PHY (Long Range S=8 / S=2, up to 400-1000m+)
 * - BOTH: Simultaneously scans and advertises on both 1M PHY and Coded PHY
 */
enum class BtPhyMode(
    val title: String,
    val shortBadge: String,
    val description: String
) {
    OFF(
        title = "Вимкнено (OFF)",
        shortBadge = "OFF",
        description = "Радіомодуль додатку повністю вимкнено (не сканує і не передає)"
    ),
    STANDARD_1M(
        title = "Звичайний BT (1M PHY)",
        shortBadge = "1M",
        description = "Стандартна дальність (~50–100 м), сумісність з усіма телефонами"
    ),
    EXTENDED_CODED(
        title = "Extended Range (Coded PHY)",
        shortBadge = "LR Coded",
        description = "Далекобійний режим S=8 FEC (до 400–1000 м), Bluetooth 5.0+"
    ),
    BOTH(
        title = "Обидва режими (1M + Extended)",
        shortBadge = "1M + LR",
        description = "Гібридне сканування та передача на обох частотних профілях"
    );

    val isOff: Boolean
        get() = this == OFF

    val uses1M: Boolean
        get() = this == STANDARD_1M || this == BOTH

    val usesCoded: Boolean
        get() = this == EXTENDED_CODED || this == BOTH
}

/**
 * Simple, user-friendly Power & Speed presets instead of manual millisecond sliders.
 */
enum class PowerPresetMode(
    val title: String,
    val subtitle: String,
    val scanWindowMs: Long,
    val sleepIntervalMs: Long,
    val txBurstDurationMs: Long
) {
    ECO(
        title = "🔋 Еко (0.5с скан / 5с пауза)",
        subtitle = "Мінімум батареї (~9% активності), відправка 5.2с",
        scanWindowMs = 500L,
        sleepIntervalMs = 5000L,
        txBurstDurationMs = 5200L
    ),
    BALANCED(
        title = "⚡ Стандартний (0.7с скан / 3с пауза)",
        subtitle = "Швидке з'єднання та доставка (~18% активності)",
        scanWindowMs = 700L,
        sleepIntervalMs = 3000L,
        txBurstDurationMs = 3400L
    ),
    TURBO(
        title = "🚀 Турбо (1.0с скан / 1.5с пауза)",
        subtitle = "Миттєвий чат і радар у реальному часі",
        scanWindowMs = 1000L,
        sleepIntervalMs = 1500L,
        txBurstDurationMs = 2200L
    )
}

/**
 * Detected PHY layer of an incoming packet or peer device.
 */
enum class DetectedPhy(val label: String) {
    PHY_1M("1M Standard"),
    PHY_CODED("LR Coded (Extended)"),
    PHY_BOTH("1M + LR Coded"),
    CLASSIC_OR_UNKNOWN("BLE Legacy");

    companion object {
        fun fromAndroidPhy(primaryPhy: Int, secondaryPhy: Int): DetectedPhy {
            val isCoded = primaryPhy == BluetoothDevice.PHY_LE_CODED ||
                    secondaryPhy == BluetoothDevice.PHY_LE_CODED
            val is1M = primaryPhy == BluetoothDevice.PHY_LE_1M ||
                    secondaryPhy == BluetoothDevice.PHY_LE_1M
            return when {
                isCoded && is1M -> PHY_BOTH
                isCoded -> PHY_CODED
                is1M -> PHY_1M
                else -> CLASSIC_OR_UNKNOWN
            }
        }
    }
}

/**
 * Current state of the ultra-low-power duty-cycled radio engine.
 */
enum class RadioPulseState(val statusText: String) {
    DISABLED("Вимкнено (очікування дозволів або BT)"),
    SCAN_WINDOW("Активний імпульс сканування..."),
    SLEEP_PAUSE("Енергозбереження (пауза)"),
    TX_BURST("Відправка пакету в ефір...")
}

/**
 * Delivery status of a chat message, styled like Telegram.
 */
enum class DeliveryStatus {
    QUEUED,         // Waiting in queue for next TX burst
    SENDING_BURST,  // Currently broadcasting radio burst
    SENT_TO_AIR,    // Single check ✓ (burst finished, or broadcast message)
    DELIVERED_ACK,  // Double check ✓✓ (Confirmed via packet ACK from recipient)
    FAILED          // Retry limit reached without ACK (user can tap to retry)
}

/**
 * Represents a discovered device (either a Pulsegram peer or general nearby BLE device).
 * Uses live RSSI smoothing so the distance shown is the REAL-TIME distance right now.
 */
data class NearbyPeer(
    val nodeId: Int,
    val nickname: String,
    val macAddress: String,
    val isPulsegramPeer: Boolean,
    val lastRssi: Int,               // Smoothed real-time RSSI right now
    val rawInstantRssi: Int,         // Instantaneous RSSI of the last packet
    val seenOn1M: Boolean,
    val seenOnCoded: Boolean,
    val peerSupportsCoded: Boolean,
    val lastSeenTimestamp: Long,
    val packetsReceivedCount: Int = 1,
    val hasEncryptionKey: Boolean = false,
    val l2capPsm: Int = 0
) {
    val detectedPhySummary: String
        get() = when {
            seenOn1M && seenOnCoded -> "1M + Extended LR"
            seenOnCoded -> "Extended LR (Coded)"
            seenOn1M -> "Standard 1M"
            else -> "Standard BLE"
        }

    /**
     * Visual 1..4 signal strength level for easy reading by everyday users.
     */
    val signalStrengthBars: Int
        get() = when {
            lastRssi >= -62 -> 4
            lastRssi >= -75 -> 3
            lastRssi >= -88 -> 2
            else -> 1
        }

    /**
     * Real-time distance in meters right now based on current smoothed RSSI.
     */
    val estimatedDistanceMeters: Double
        get() {
            val txPowerAt1m = if (seenOnCoded && !seenOn1M) -55.0 else -59.0
            val rssi = lastRssi.coerceIn(-110, -25).toDouble()
            val pathLossExponent = 2.4
            return Math.pow(10.0, (txPowerAt1m - rssi) / (10.0 * pathLossExponent))
        }

    /**
     * Whether this device is actively being received right now (within the last 45 seconds).
     */
    val isCurrentlyInRange: Boolean
        get() = (System.currentTimeMillis() - lastSeenTimestamp) <= LIVE_RADAR_TIMEOUT_MS

    companion object {
        const val LIVE_RADAR_TIMEOUT_MS = 45_000L
    }
}

/**
 * Represents a chat message in either the Global Broadcast Mesh chat (peerNodeId = 0xFFFF)
 * or a Direct P2P chat with a specific peerNodeId.
 * Supports both text messages and ultra-compressed single-message photos (`IMG1:<base64>`).
 */
data class ChatMessage(
    val id: String,
    val msgId: Int,
    val conversationId: Int,
    val senderNodeId: Int,
    val senderName: String,
    val targetNodeId: Int,
    val text: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    val status: DeliveryStatus,
    val receivedViaPhy: String,
    val rssiAtReceipt: Int? = null,
    val hopCount: Int = 0,
    val retryCount: Int = 0,
    val burstProgressPercent: Int = 0,
    val isEncrypted: Boolean = false
) {
    val isPhotoMessage: Boolean
        get() = UltraImageCompressor.isPhotoPayload(text)

    val isLocationMessage: Boolean
        get() = text.startsWith(LOCATION_PREFIX)

    val isSosMessage: Boolean
        get() = text.startsWith(SOS_PREFIX)

    val isStickerMessage: Boolean
        get() = text.startsWith(STICKER_PREFIX)

    val parsedSticker: PulseSticker?
        get() {
            if (!isStickerMessage) return null
            val code = text.removePrefix(STICKER_PREFIX).trim()
            return PulseSticker.fromCode(code)
        }

    val parsedCoordinates: Pair<Double, Double>?
        get() {
            if (!isLocationMessage) return null
            val parts = text.removePrefix(LOCATION_PREFIX).split(",")
            if (parts.size < 2) return null
            val lat = parts[0].trim().toDoubleOrNull() ?: return null
            val lon = parts[1].trim().toDoubleOrNull() ?: return null
            return Pair(lat, lon)
        }

    val previewText: String
        get() = when {
            isPhotoMessage -> "📷 HD Фото (800px)"
            isStickerMessage -> {
                val stk = parsedSticker
                if (stk != null) "${stk.emoji} Стікер «${stk.title}»" else "🎨 Стікер"
            }
            isLocationMessage -> "📍 Геолокація (офлайн GPS)"
            isSosMessage -> "🚨 SOS: ${text.removePrefix(SOS_PREFIX).trim()}"
            else -> text
        }

    companion object {
        const val LOCATION_PREFIX = "LOC:"
        const val SOS_PREFIX = "SOS:"
        const val STICKER_PREFIX = "STK:"
    }
}

/**
 * Built-in Ultra-Compact Graphic Sticker Pack for PulseLink.
 * Transmitted over Bluetooth in a single 12-byte packet (`STK:<code>`) and rendered
 * as a large 150x150dp vector/canvas artwork card in the chat!
 */
enum class PulseSticker(
    val code: String,
    val emoji: String,
    val title: String,
    val colorStart: Long,
    val colorEnd: Long
) {
    RADAR_PING("RADAR_PING", "📡", "На зв'язку!", 0xFF0D9488, 0xFF115E59),
    ROGER_THAT("ROGER_THAT", "🫡", "Прийнято! +", 0xFF14B8A6, 0xFF0F766E),
    SUPER_FIRE("SUPER_FIRE", "🔥", "Вогонь!", 0xFFF97316, 0xFFB91C1C),
    WALL_BREAKER("WALL_BREAKER", "⚡", "Пробиває стіни!", 0xFF8B5CF6, 0xFF4C1D95),
    HEART_THANKS("HEART_THANKS", "💚", "Дякую!", 0xFF10B981, 0xFF047857),
    PARTY_ROCKET("PARTY_ROCKET", "🚀", "Полетіли!", 0xFF3B82F6, 0xFF1D4ED8),
    LAUGH_TEARS("LAUGH_TEARS", "😂", "Ха-ха-ха!", 0xFFEAB308, 0xFFB45309),
    STEALTH_E2EE("STEALTH_E2EE", "🔒", "Секретно E2EE", 0xFF06B6D4, 0xFF0E7490),
    COFFEE_TIME("COFFEE_TIME", "☕", "Час на каву", 0xFFD97706, 0xFF78350F),
    LOCATION_HERE("LOCATION_HERE", "📍", "Я на місці!", 0xFF22C55E, 0xFF15803D),
    BATTERY_FULL("BATTERY_FULL", "🔋", "Заряд 100%", 0xFF84CC16, 0xFF3F6212),
    SHIELD_SAFE("SHIELD_SAFE", "🛡️", "Все чисто!", 0xFF38BDF8, 0xFF0369A1),
    EYES_WATCH("EYES_WATCH", "👀", "Бачу тебе", 0xFFA855F7, 0xFF6B21A8),
    THUMBS_DEAL("THUMBS_DEAL", "👍", "Домовились!", 0xFF2DD4BF, 0xFF0F766E),
    NIGHT_OWL("NIGHT_OWL", "🦉", "Нічний ефір", 0xFF6366F1, 0xFF312E81),
    SOS_URGENT("SOS_URGENT", "🚨", "Терміново!", 0xFFEF4444, 0xFF991B1B);

    val wirePayload: String
        get() = "${ChatMessage.STICKER_PREFIX}$code"

    companion object {
        fun fromCode(code: String): PulseSticker? =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
    }
}

/**
 * Conversation summary for the Telegram-like chat list screen.
 */
data class ConversationSummary(
    val conversationId: Int,
    val title: String,
    val subtitle: String,
    val isGlobalMesh: Boolean,
    val lastMessageText: String,
    val lastMessageTime: Long,
    val lastMessageStatus: DeliveryStatus?,
    val isLastMessageOutgoing: Boolean,
    val unreadCount: Int,
    val peer: NearbyPeer?
)

/**
 * User-configurable settings using simple modes instead of manual millisecond tuning.
 */
data class RadioConfig(
    val nickname: String = "User-${(100..999).random()}",
    val localNodeId: Int = (1..65000).random(),
    val phyMode: BtPhyMode = BtPhyMode.BOTH,
    val powerPreset: PowerPresetMode = PowerPresetMode.ECO,
    val notificationsEnabled: Boolean = true,
    val showDebugInfo: Boolean = false,
    val autoRetryUnacked: Boolean = true,
    val maxRetries: Int = 5,
    val enableMeshRelay: Boolean = true,
    val showAllBleDevicesInRadar: Boolean = false
) {
    val scanWindowMs: Long
        get() = powerPreset.scanWindowMs

    val sleepIntervalMs: Long
        get() = powerPreset.sleepIntervalMs

    val txBurstDurationMs: Long
        get() = powerPreset.txBurstDurationMs
}

/**
 * Real-time telemetry & energy statistics.
 */
data class EnergyStats(
    val scanPulsesCount: Int = 0,
    val txBurstsCount: Int = 0,
    val earlyAckSavesCount: Int = 0,
    val packetsSent: Int = 0,
    val packetsReceived: Int = 0,
    val acksReceived: Int = 0,
    val relayedPackets: Int = 0,
    val totalScanActiveMs: Long = 0L,
    val totalTxActiveMs: Long = 0L,
    val sessionStartTimeMs: Long = System.currentTimeMillis()
) {
    val dutyCyclePercent: Double
        get() {
            val elapsed = (System.currentTimeMillis() - sessionStartTimeMs).coerceAtLeast(1000L)
            val active = totalScanActiveMs + totalTxActiveMs
            return ((active.toDouble() / elapsed.toDouble()) * 100.0).coerceIn(0.0, 100.0)
        }
}
