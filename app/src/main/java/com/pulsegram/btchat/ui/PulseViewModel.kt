package com.pulsegram.btchat.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pulsegram.btchat.bluetooth.PulsePacketCodec
import com.pulsegram.btchat.bluetooth.PulseRadioEngine
import com.pulsegram.btchat.model.BtPhyMode
import com.pulsegram.btchat.model.ChatMessage
import com.pulsegram.btchat.model.ConversationSummary
import com.pulsegram.btchat.model.EnergyStats
import com.pulsegram.btchat.model.NearbyPeer
import com.pulsegram.btchat.model.PowerPresetMode
import com.pulsegram.btchat.model.PulseSticker
import com.pulsegram.btchat.model.RadioConfig
import com.pulsegram.btchat.model.RadioPulseState
import com.pulsegram.btchat.service.PulseRadioService
import com.pulsegram.btchat.util.UltraImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PulseViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = PulseRadioEngine.getInstance(application)

    val config: StateFlow<RadioConfig> = engine.config
    val pulseState: StateFlow<RadioPulseState> = engine.pulseState
    val messages: StateFlow<List<ChatMessage>> = engine.messages
    val peers: StateFlow<List<NearbyPeer>> = engine.peers
    val energyStats: StateFlow<EnergyStats> = engine.energyStats

    val isCodedPhySupported: Boolean
        get() = engine.isCodedPhySupported

    val isExtendedAdvertisingSupported: Boolean
        get() = engine.isExtendedAdvertisingSupported

    private val _selectedConversationId = MutableStateFlow<Int?>(null)
    val selectedConversationId: StateFlow<Int?> = _selectedConversationId.asStateFlow()

    /**
     * Combines messages, live radar peers, known history peers, and unread counters
     * into a Telegram-style sorted list of conversations.
     */
    val conversations: StateFlow<List<ConversationSummary>> = combine(
        engine.messages,
        engine.peers,
        engine.knownPeersHistory,
        engine.unreadByConversation
    ) { allMessages, livePeers, knownMap, unreadMap ->
        val result = ArrayList<ConversationSummary>()

        // 1. Pin Global Broadcast Mesh Chat at the top
        val meshMessages = allMessages.filter {
            it.conversationId == PulsePacketCodec.BROADCAST_NODE_ID
        }
        val lastMesh = meshMessages.lastOrNull()
        val livePulsegramPeers = livePeers.count { it.isPulsegramPeer }

        result.add(
            ConversationSummary(
                conversationId = PulsePacketCodec.BROADCAST_NODE_ID,
                title = "📢 Загальний Ефір (Mesh Chat)",
                subtitle = "Усі пристрої поруч • Зараз на зв'язку: $livePulsegramPeers",
                isGlobalMesh = true,
                lastMessageText = lastMesh?.previewText ?: "Напишіть або надішліть фото усім поруч...",
                lastMessageTime = lastMesh?.timestamp ?: System.currentTimeMillis(),
                lastMessageStatus = lastMesh?.status,
                isLastMessageOutgoing = lastMesh?.isOutgoing ?: false,
                unreadCount = unreadMap[PulsePacketCodec.BROADCAST_NODE_ID] ?: 0,
                peer = null
            )
        )

        // 2. Direct P2P conversations
        val livePeerMap = livePeers.filter { it.isPulsegramPeer }.associateBy { it.nodeId }
        val directConvIds = (livePeerMap.keys + knownMap.keys + allMessages
            .map { it.conversationId }
            .filter { it != PulsePacketCodec.BROADCAST_NODE_ID })
            .toSet()

        val directSummaries = directConvIds.map { convId ->
            val livePeer = livePeerMap[convId]
            val historicalPeer = knownMap[convId]
            val peer = livePeer ?: historicalPeer

            val convMsgs = allMessages.filter { it.conversationId == convId }
            val lastMsg = convMsgs.lastOrNull()
            val title = peer?.nickname
                ?: convMsgs.lastOrNull { !it.isOutgoing }?.senderName
                ?: "Вузол #$convId"

            val hasE2ee = peer?.hasEncryptionKey == true || engine.hasPeerEncryptionKey(convId)
            val lockTag = if (hasE2ee) "🔒 E2EE • " else ""

            val statusSubtitle = if (livePeer != null) {
                "${lockTag}🟢 Поруч: ~${"%.1f".format(livePeer.estimatedDistanceMeters)} м (${livePeer.detectedPhySummary})"
            } else {
                "${lockTag}⚪ Поза радіусом • ${peer?.detectedPhySummary ?: "BLE Peer"}"
            }

            ConversationSummary(
                conversationId = convId,
                title = title,
                subtitle = statusSubtitle,
                isGlobalMesh = false,
                lastMessageText = lastMsg?.previewText ?: "Натисніть, щоб написати або надіслати фото",
                lastMessageTime = lastMsg?.timestamp ?: (peer?.lastSeenTimestamp ?: 0L),
                lastMessageStatus = lastMsg?.status,
                isLastMessageOutgoing = lastMsg?.isOutgoing ?: false,
                unreadCount = unreadMap[convId] ?: 0,
                peer = livePeer
            )
        }.sortedByDescending { it.lastMessageTime }

        result.addAll(directSummaries)
        result
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun openConversation(conversationId: Int) {
        _selectedConversationId.value = conversationId
        engine.setActiveConversation(conversationId)
    }

    fun closeConversation() {
        _selectedConversationId.value = null
        engine.setActiveConversation(null)
    }

    fun sendMessage(conversationId: Int, text: String) {
        engine.sendChatMessage(targetNodeId = conversationId, text = text)
    }

    fun sendStickerMessage(conversationId: Int, sticker: PulseSticker) {
        engine.sendChatMessage(targetNodeId = conversationId, text = sticker.wirePayload)
    }

    fun sendLocationMessage(conversationId: Int, lat: Double, lon: Double) {
        val formatted = String.format(java.util.Locale.US, "LOC:%.5f,%.5f", lat, lon)
        engine.sendChatMessage(targetNodeId = conversationId, text = formatted)
    }

    fun sendSosMessage(conversationId: Int, note: String = "", lat: Double? = null, lon: Double? = null) {
        val coordsPart = if (lat != null && lon != null) {
            String.format(java.util.Locale.US, " (GPS: %.5f, %.5f)", lat, lon)
        } else {
            ""
        }
        val body = note.trim().ifEmpty { "Потрібна допомога!" }
        engine.sendChatMessage(targetNodeId = conversationId, text = "SOS:$body$coordsPart")
    }

    /**
     * Compresses a selected gallery or full-res camera photo URI up to 800px max side
     * with high-bitrate WebP Lossy + Zlib (7.4 KB on Standard 1M / Both Bluetooth, 3.6 KB on pure LR Coded)
     * and transmits via high-speed BLE L2CAP CoC or fast 1M Extended Advertising + Selective-ACK.
     */
    fun sendCompressedPhoto(conversationId: Int, imageUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val maxWebpBytes = if (engine.config.value.phyMode == BtPhyMode.EXTENDED_CODED) {
                UltraImageCompressor.EXTENDED_LR_MAX_BYTES
            } else {
                UltraImageCompressor.HIGH_BITRATE_1M_MAX_BYTES
            }
            val payload = UltraImageCompressor.compressUriTo800MaxPayload(
                context = getApplication(),
                uri = imageUri,
                maxBytes = maxWebpBytes
            )
            if (payload != null) {
                engine.sendChatMessage(targetNodeId = conversationId, text = payload)
            }
        }
    }

    fun retryMessage(messageId: String) {
        engine.retryMessage(messageId)
    }

    fun deleteMessage(messageId: String) {
        engine.deleteMessage(messageId)
    }

    fun clearConversation(conversationId: Int) {
        engine.clearConversation(conversationId)
    }

    fun panicWipeAll() {
        _selectedConversationId.value = null
        engine.setActiveConversation(null)
        engine.panicWipeAllData()
    }

    fun hasPeerEncryptionKey(conversationId: Int): Boolean {
        if (conversationId == PulsePacketCodec.BROADCAST_NODE_ID) return false
        return engine.hasPeerEncryptionKey(conversationId)
    }

    fun setPhyMode(mode: BtPhyMode) {
        engine.updateConfig { it.copy(phyMode = mode) }
    }

    fun setPowerPreset(preset: PowerPresetMode) {
        engine.updateConfig { it.copy(powerPreset = preset) }
    }

    fun updateNickname(newName: String) {
        val trimmed = newName.trim().take(16)
        if (trimmed.isNotEmpty()) {
            engine.updateConfig { it.copy(nickname = trimmed) }
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        engine.updateConfig { it.copy(notificationsEnabled = enabled) }
    }

    fun toggleShowDebugInfo(enabled: Boolean) {
        engine.updateConfig { it.copy(showDebugInfo = enabled) }
    }

    fun setMaxRetries(retries: Int) {
        engine.updateConfig { it.copy(maxRetries = retries.coerceIn(1, 8)) }
    }

    fun toggleMeshRelay(enabled: Boolean) {
        engine.updateConfig { it.copy(enableMeshRelay = enabled) }
    }

    fun toggleAutoRetry(enabled: Boolean) {
        engine.updateConfig { it.copy(autoRetryUnacked = enabled) }
    }

    fun toggleShowAllBleDevices(enabled: Boolean) {
        engine.updateConfig { it.copy(showAllBleDevicesInRadar = enabled) }
    }

    fun emitBeaconNow() {
        engine.sendPresenceBeaconBurst()
    }

    fun onAppForegroundChanged(inForeground: Boolean) {
        engine.setAppForegroundState(inForeground)
    }

    fun onPermissionsGrantedAndBtReady() {
        if (engine.config.value.phyMode != BtPhyMode.OFF) {
            PulseRadioService.start(getApplication())
            engine.startEngine()
        }
    }

    fun onBluetoothDisabled() {
        engine.stopEngine()
    }
}
