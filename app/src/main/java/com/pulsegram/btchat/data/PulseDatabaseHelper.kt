package com.pulsegram.btchat.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.pulsegram.btchat.bluetooth.PulsePacketCodec
import com.pulsegram.btchat.model.BtPhyMode
import com.pulsegram.btchat.model.ChatMessage
import com.pulsegram.btchat.model.DeliveryStatus
import com.pulsegram.btchat.model.NearbyPeer
import com.pulsegram.btchat.model.PowerPresetMode
import com.pulsegram.btchat.model.RadioConfig

/**
 * Local SQLite persistence for PulseLink messages, discovered peers, and radio preferences.
 */
class PulseDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    private val prefs = context.getSharedPreferences("pulsegram_prefs", Context.MODE_PRIVATE)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE messages (
                id TEXT PRIMARY KEY,
                msg_id INTEGER NOT NULL,
                conversation_id INTEGER NOT NULL,
                sender_node_id INTEGER NOT NULL,
                sender_name TEXT NOT NULL,
                target_node_id INTEGER NOT NULL,
                text TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                is_outgoing INTEGER NOT NULL,
                status TEXT NOT NULL,
                received_via_phy TEXT NOT NULL,
                rssi INTEGER,
                hop_count INTEGER NOT NULL DEFAULT 0,
                retry_count INTEGER NOT NULL DEFAULT 0,
                is_encrypted INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_messages_conv ON messages(conversation_id, timestamp)")

        db.execSQL(
            """
            CREATE TABLE peers (
                node_id INTEGER PRIMARY KEY,
                nickname TEXT NOT NULL,
                mac_address TEXT NOT NULL,
                is_pulsegram INTEGER NOT NULL,
                last_rssi INTEGER NOT NULL,
                best_rssi INTEGER NOT NULL,
                seen_on_1m INTEGER NOT NULL,
                seen_on_coded INTEGER NOT NULL,
                peer_supports_coded INTEGER NOT NULL,
                last_seen_ts INTEGER NOT NULL,
                packets_count INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )

        insertWelcomeMessage(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            runCatching {
                db.execSQL("ALTER TABLE messages ADD COLUMN is_encrypted INTEGER NOT NULL DEFAULT 0")
            }
        }
    }

    private fun insertWelcomeMessage(db: SQLiteDatabase) {
        val welcomeValues = ContentValues().apply {
            put("id", "system_welcome_0")
            put("msg_id", 0)
            put("conversation_id", PulsePacketCodec.BROADCAST_NODE_ID)
            put("sender_node_id", 0)
            put("sender_name", "PulseLink System")
            put("target_node_id", PulsePacketCodec.BROADCAST_NODE_ID)
            put(
                "text",
                "👋 Ласкаво просимо в PulseLink!\n" +
                    "• 🔒 Автоматичне E2E шифрування (ECDH + AES-GCM) для особистих чатів.\n" +
                    "• 📷 HD фото до 800px з високим бітрейтом + 🎨 стікери, 📍 офлайн GPS та 🚨 SOS.\n" +
                    "• 📡 Далекобійний Bluetooth 5 Extended Range (Coded PHY) + швидкий 1M L2CAP."
            )
            put("timestamp", System.currentTimeMillis())
            put("is_outgoing", 0)
            put("status", DeliveryStatus.DELIVERED_ACK.name)
            put("received_via_phy", "1M + LR")
            put("hop_count", 0)
            put("retry_count", 0)
            put("is_encrypted", 0)
        }
        db.insertWithOnConflict("messages", null, welcomeValues, SQLiteDatabase.CONFLICT_IGNORE)
    }

    @Synchronized
    fun upsertMessage(msg: ChatMessage) {
        val values = ContentValues().apply {
            put("id", msg.id)
            put("msg_id", msg.msgId)
            put("conversation_id", msg.conversationId)
            put("sender_node_id", msg.senderNodeId)
            put("sender_name", msg.senderName)
            put("target_node_id", msg.targetNodeId)
            put("text", msg.text)
            put("timestamp", msg.timestamp)
            put("is_outgoing", if (msg.isOutgoing) 1 else 0)
            put("status", msg.status.name)
            put("received_via_phy", msg.receivedViaPhy)
            if (msg.rssiAtReceipt != null) {
                put("rssi", msg.rssiAtReceipt)
            } else {
                putNull("rssi")
            }
            put("hop_count", msg.hopCount)
            put("retry_count", msg.retryCount)
            put("is_encrypted", if (msg.isEncrypted) 1 else 0)
        }
        writableDatabase.insertWithOnConflict(
            "messages",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    @Synchronized
    fun updateMessageStatus(id: String, status: DeliveryStatus, retryCount: Int? = null) {
        val values = ContentValues().apply {
            put("status", status.name)
            if (retryCount != null) {
                put("retry_count", retryCount)
            }
        }
        writableDatabase.update("messages", values, "id = ?", arrayOf(id))
    }

    @Synchronized
    fun deleteMessage(id: String) {
        writableDatabase.delete("messages", "id = ?", arrayOf(id))
    }

    @Synchronized
    fun deleteConversationMessages(conversationId: Int) {
        writableDatabase.delete("messages", "conversation_id = ?", arrayOf(conversationId.toString()))
    }

    @Synchronized
    fun wipeAllData() {
        writableDatabase.delete("messages", null, null)
        writableDatabase.delete("peers", null, null)
        insertWelcomeMessage(writableDatabase)
    }

    @Synchronized
    fun loadAllMessages(): List<ChatMessage> {
        val result = ArrayList<ChatMessage>()
        readableDatabase.query(
            "messages",
            null,
            null,
            null,
            null,
            null,
            "timestamp ASC"
        ).use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow("id")
            val msgIdIdx = cursor.getColumnIndexOrThrow("msg_id")
            val convIdIdx = cursor.getColumnIndexOrThrow("conversation_id")
            val senderIdIdx = cursor.getColumnIndexOrThrow("sender_node_id")
            val senderNameIdx = cursor.getColumnIndexOrThrow("sender_name")
            val targetIdIdx = cursor.getColumnIndexOrThrow("target_node_id")
            val textIdx = cursor.getColumnIndexOrThrow("text")
            val tsIdx = cursor.getColumnIndexOrThrow("timestamp")
            val outIdx = cursor.getColumnIndexOrThrow("is_outgoing")
            val statusIdx = cursor.getColumnIndexOrThrow("status")
            val phyIdx = cursor.getColumnIndexOrThrow("received_via_phy")
            val rssiIdx = cursor.getColumnIndexOrThrow("rssi")
            val hopIdx = cursor.getColumnIndexOrThrow("hop_count")
            val retryIdx = cursor.getColumnIndexOrThrow("retry_count")
            val encIdx = cursor.getColumnIndex("is_encrypted")

            while (cursor.moveToNext()) {
                val statusStr = cursor.getString(statusIdx)
                val status = runCatching { DeliveryStatus.valueOf(statusStr) }
                    .getOrDefault(DeliveryStatus.SENT_TO_AIR)
                val rssi = if (cursor.isNull(rssiIdx)) null else cursor.getInt(rssiIdx)
                val isEnc = if (encIdx >= 0) cursor.getInt(encIdx) == 1 else false

                result.add(
                    ChatMessage(
                        id = cursor.getString(idIdx),
                        msgId = cursor.getInt(msgIdIdx),
                        conversationId = cursor.getInt(convIdIdx),
                        senderNodeId = cursor.getInt(senderIdIdx),
                        senderName = cursor.getString(senderNameIdx),
                        targetNodeId = cursor.getInt(targetIdIdx),
                        text = cursor.getString(textIdx),
                        timestamp = cursor.getLong(tsIdx),
                        isOutgoing = cursor.getInt(outIdx) == 1,
                        status = status,
                        receivedViaPhy = cursor.getString(phyIdx),
                        rssiAtReceipt = rssi,
                        hopCount = cursor.getInt(hopIdx),
                        retryCount = cursor.getInt(retryIdx),
                        isEncrypted = isEnc
                    )
                )
            }
        }
        return result
    }

    @Synchronized
    fun upsertPeer(peer: NearbyPeer) {
        val values = ContentValues().apply {
            put("node_id", peer.nodeId)
            put("nickname", peer.nickname)
            put("mac_address", peer.macAddress)
            put("is_pulsegram", if (peer.isPulsegramPeer) 1 else 0)
            put("last_rssi", peer.lastRssi)
            put("best_rssi", peer.rawInstantRssi)
            put("seen_on_1m", if (peer.seenOn1M) 1 else 0)
            put("seen_on_coded", if (peer.seenOnCoded) 1 else 0)
            put("peer_supports_coded", if (peer.peerSupportsCoded) 1 else 0)
            put("last_seen_ts", peer.lastSeenTimestamp)
            put("packets_count", peer.packetsReceivedCount)
        }
        writableDatabase.insertWithOnConflict(
            "peers",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    @Synchronized
    fun loadPeers(): List<NearbyPeer> {
        val list = ArrayList<NearbyPeer>()
        readableDatabase.query(
            "peers",
            null,
            null,
            null,
            null,
            null,
            "last_seen_ts DESC"
        ).use { cursor ->
            val nodeIdIdx = cursor.getColumnIndexOrThrow("node_id")
            val nickIdx = cursor.getColumnIndexOrThrow("nickname")
            val macIdx = cursor.getColumnIndexOrThrow("mac_address")
            val isPulseIdx = cursor.getColumnIndexOrThrow("is_pulsegram")
            val lastRssiIdx = cursor.getColumnIndexOrThrow("last_rssi")
            val bestRssiIdx = cursor.getColumnIndexOrThrow("best_rssi")
            val s1mIdx = cursor.getColumnIndexOrThrow("seen_on_1m")
            val sCodedIdx = cursor.getColumnIndexOrThrow("seen_on_coded")
            val supCodedIdx = cursor.getColumnIndexOrThrow("peer_supports_coded")
            val tsIdx = cursor.getColumnIndexOrThrow("last_seen_ts")
            val countIdx = cursor.getColumnIndexOrThrow("packets_count")

            while (cursor.moveToNext()) {
                list.add(
                    NearbyPeer(
                        nodeId = cursor.getInt(nodeIdIdx),
                        nickname = cursor.getString(nickIdx),
                        macAddress = cursor.getString(macIdx),
                        isPulsegramPeer = cursor.getInt(isPulseIdx) == 1,
                        lastRssi = cursor.getInt(lastRssiIdx),
                        rawInstantRssi = cursor.getInt(bestRssiIdx),
                        seenOn1M = cursor.getInt(s1mIdx) == 1,
                        seenOnCoded = cursor.getInt(sCodedIdx) == 1,
                        peerSupportsCoded = cursor.getInt(supCodedIdx) == 1,
                        lastSeenTimestamp = cursor.getLong(tsIdx),
                        packetsReceivedCount = cursor.getInt(countIdx)
                    )
                )
            }
        }
        return list
    }

    fun loadRadioConfig(): RadioConfig {
        val defaultNodeId = (1000..64000).random()
        val savedNodeId = prefs.getInt("local_node_id", -1).let {
            if (it == -1) {
                prefs.edit().putInt("local_node_id", defaultNodeId).apply()
                defaultNodeId
            } else it
        }
        val defaultNick = "Pulse-${savedNodeId % 1000}"
        val nick = prefs.getString("nickname", defaultNick) ?: defaultNick
        val phyName = prefs.getString("phy_mode", BtPhyMode.BOTH.name) ?: BtPhyMode.BOTH.name
        val phyMode = runCatching { BtPhyMode.valueOf(phyName) }.getOrDefault(BtPhyMode.BOTH)

        val presetName = prefs.getString("power_preset", PowerPresetMode.BALANCED.name)
            ?: PowerPresetMode.BALANCED.name
        val powerPreset = runCatching { PowerPresetMode.valueOf(presetName) }
            .getOrDefault(PowerPresetMode.BALANCED)

        return RadioConfig(
            nickname = nick,
            localNodeId = savedNodeId,
            phyMode = phyMode,
            powerPreset = powerPreset,
            notificationsEnabled = prefs.getBoolean("notifications_enabled", true),
            showDebugInfo = prefs.getBoolean("show_debug_info", false),
            autoRetryUnacked = prefs.getBoolean("auto_retry", true),
            maxRetries = prefs.getInt("max_retries", 5),
            enableMeshRelay = prefs.getBoolean("mesh_relay", true),
            showAllBleDevicesInRadar = prefs.getBoolean("show_all_ble", false)
        )
    }

    fun saveRadioConfig(config: RadioConfig) {
        prefs.edit()
            .putString("nickname", config.nickname)
            .putInt("local_node_id", config.localNodeId)
            .putString("phy_mode", config.phyMode.name)
            .putString("power_preset", config.powerPreset.name)
            .putBoolean("notifications_enabled", config.notificationsEnabled)
            .putBoolean("show_debug_info", config.showDebugInfo)
            .putBoolean("auto_retry", config.autoRetryUnacked)
            .putInt("max_retries", config.maxRetries)
            .putBoolean("mesh_relay", config.enableMeshRelay)
            .putBoolean("show_all_ble", config.showAllBleDevicesInRadar)
            .apply()
    }

    companion object {
        private const val DB_NAME = "pulsegram_chat.db"
        private const val DB_VERSION = 2
    }
}
