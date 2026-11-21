package com.pulsegram.btchat.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.pulsegram.btchat.bluetooth.PulsePacketCodec
import com.pulsegram.btchat.model.BtPhyMode
import com.pulsegram.btchat.model.ChatMessage
import com.pulsegram.btchat.model.ConversationSummary
import com.pulsegram.btchat.model.DeliveryStatus
import com.pulsegram.btchat.model.EnergyStats
import com.pulsegram.btchat.model.NearbyPeer
import com.pulsegram.btchat.model.PowerPresetMode
import com.pulsegram.btchat.model.PulseSticker
import com.pulsegram.btchat.model.RadioConfig
import com.pulsegram.btchat.model.RadioPulseState
import com.pulsegram.btchat.ui.theme.TgAccentCyan
import com.pulsegram.btchat.ui.theme.TgAmberBurst
import com.pulsegram.btchat.ui.theme.TgCodedPurple
import com.pulsegram.btchat.ui.theme.TgDarkBackground
import com.pulsegram.btchat.ui.theme.TgGreenPulse
import com.pulsegram.btchat.ui.theme.TgIncomingBubble
import com.pulsegram.btchat.ui.theme.TgOutgoingBubble
import com.pulsegram.btchat.ui.theme.TgPrimaryBlue
import com.pulsegram.btchat.ui.theme.TgSurfaceCard
import com.pulsegram.btchat.ui.theme.TgSurfaceHeader
import com.pulsegram.btchat.ui.theme.TgTextPrimary
import com.pulsegram.btchat.ui.theme.TgTextSecondary
import com.pulsegram.btchat.util.UltraImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun PulsegramApp(
    viewModel: PulseViewModel,
    hasPermissions: Boolean,
    isBluetoothEnabled: Boolean,
    onRequestPermissionsAndBt: () -> Unit
) {
    val config by viewModel.config.collectAsState()
    val pulseState by viewModel.pulseState.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val allMessages by viewModel.messages.collectAsState()
    val peers by viewModel.peers.collectAsState()
    val energyStats by viewModel.energyStats.collectAsState()
    val selectedConvId by viewModel.selectedConversationId.collectAsState()

    var currentTab by rememberSaveable { mutableIntStateOf(0) }

    if (selectedConvId != null) {
        val convId = selectedConvId!!
        val convSummary = conversations.firstOrNull { it.conversationId == convId }
        val chatMessages = remember(allMessages, convId) {
            allMessages.filter { it.conversationId == convId }
        }

        BackHandler {
            viewModel.closeConversation()
        }

        TelegramChatDetailScreen(
            conversationId = convId,
            title = convSummary?.title ?: if (convId == PulsePacketCodec.BROADCAST_NODE_ID) {
                "📢 Загальний Ефір (Mesh Chat)"
            } else {
                "Вузол #$convId"
            },
            subtitle = convSummary?.subtitle ?: "Прямий Bluetooth-канал",
            isE2eeActive = viewModel.hasPeerEncryptionKey(convId),
            messages = chatMessages,
            pulseState = pulseState,
            config = config,
            onBack = { viewModel.closeConversation() },
            onSendMessage = { text -> viewModel.sendMessage(convId, text) },
            onSendSticker = { sticker -> viewModel.sendStickerMessage(convId, sticker) },
            onSendPhotoUri = { uri -> viewModel.sendCompressedPhoto(convId, uri) },
            onSendLocation = { lat, lon -> viewModel.sendLocationMessage(convId, lat, lon) },
            onSendSos = { note, lat, lon -> viewModel.sendSosMessage(convId, note, lat, lon) },
            onRetryMessage = { msgId -> viewModel.retryMessage(msgId) },
            onDeleteMessage = { msgId -> viewModel.deleteMessage(msgId) },
            onClearChat = { viewModel.clearConversation(convId) },
            onSelectPhyMode = { mode -> viewModel.setPhyMode(mode) }
        )
    } else {
        Scaffold(
            containerColor = TgDarkBackground,
            topBar = {
                TelegramTopHeader(
                    config = config,
                    pulseState = pulseState,
                    energyStats = energyStats,
                    isCodedSupported = viewModel.isCodedPhySupported,
                    onSelectPhyMode = { viewModel.setPhyMode(it) },
                    onEmitBeacon = { viewModel.emitBeaconNow() }
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = TgSurfaceHeader,
                    contentColor = TgTextPrimary
                ) {
                    val itemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TgPrimaryBlue,
                        selectedTextColor = TgPrimaryBlue,
                        indicatorColor = TgPrimaryBlue.copy(alpha = 0.16f),
                        unselectedIconColor = TgTextSecondary,
                        unselectedTextColor = TgTextSecondary
                    )
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        icon = { Text("💬", fontSize = 18.sp) },
                        label = { Text("Чати") },
                        colors = itemColors
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        icon = { Text("📡", fontSize = 18.sp) },
                        label = {
                            val liveCount = peers.size
                            Text(if (liveCount > 0) "Радар Зараз ($liveCount)" else "Радар Зараз")
                        },
                        colors = itemColors
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Режими") },
                        colors = itemColors
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (!hasPermissions || !isBluetoothEnabled) {
                    PermissionAndBtBanner(
                        hasPermissions = hasPermissions,
                        isBluetoothEnabled = isBluetoothEnabled,
                        onRequest = onRequestPermissionsAndBt
                    )
                }

                when (currentTab) {
                    0 -> ChatListTab(
                        conversations = conversations,
                        onOpenConversation = { viewModel.openConversation(it) },
                        onSwitchToRadar = { currentTab = 1 }
                    )

                    1 -> RadarScannerTab(
                        peers = peers,
                        config = config,
                        isCodedSupported = viewModel.isCodedPhySupported,
                        onEmitBeacon = { viewModel.emitBeaconNow() },
                        onOpenPeerChat = { peer ->
                            viewModel.openConversation(peer.nodeId)
                        },
                        onToggleShowAllBle = { viewModel.toggleShowAllBleDevices(it) }
                    )

                    2 -> RadioAndEnergySettingsTab(
                        config = config,
                        energyStats = energyStats,
                        isCodedSupported = viewModel.isCodedPhySupported,
                        isExtendedSupported = viewModel.isExtendedAdvertisingSupported,
                        onUpdateNickname = { viewModel.updateNickname(it) },
                        onSelectPreset = { viewModel.setPowerPreset(it) },
                        onToggleNotifications = { viewModel.toggleNotifications(it) },
                        onToggleDebugInfo = { viewModel.toggleShowDebugInfo(it) },
                        onToggleMeshRelay = { viewModel.toggleMeshRelay(it) },
                        onToggleAutoRetry = { viewModel.toggleAutoRetry(it) },
                        onSetMaxRetries = { viewModel.setMaxRetries(it) },
                        onPanicWipe = { viewModel.panicWipeAll() }
                    )
                }
            }
        }
    }
}

@Composable
private fun TelegramTopHeader(
    config: RadioConfig,
    pulseState: RadioPulseState,
    energyStats: EnergyStats,
    isCodedSupported: Boolean,
    onSelectPhyMode: (BtPhyMode) -> Unit,
    onEmitBeacon: () -> Unit
) {
    val dotColor by animateColorAsState(
        targetValue = when (pulseState) {
            RadioPulseState.SCAN_WINDOW -> TgGreenPulse
            RadioPulseState.TX_BURST -> TgAmberBurst
            RadioPulseState.SLEEP_PAUSE -> TgPrimaryBlue
            RadioPulseState.DISABLED -> Color.Gray
        },
        label = "pulseColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulseScale")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotScale"
    )

    Surface(
        color = TgSurfaceHeader,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .scale(if (pulseState == RadioPulseState.SCAN_WINDOW || pulseState == RadioPulseState.TX_BURST) scale else 1f)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "PulseLink",
                            color = TgTextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(TgPrimaryBlue.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${config.nickname} (#${config.localNodeId})",
                                color = TgAccentCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    val subtitleText = if (config.showDebugInfo) {
                        "${pulseState.statusText} • Активність: ${"%.1f".format(energyStats.dutyCyclePercent)}%"
                    } else {
                        "${config.powerPreset.title} • ${pulseState.statusText}"
                    }
                    Text(
                        text = subtitleText,
                        color = TgTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onEmitBeacon) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Імпульс присутності",
                        tint = TgAccentCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Mode Switcher Bar: Standard 1M | Extended LR | Both
            PhyModeSelectorBar(
                selectedMode = config.phyMode,
                isCodedSupported = isCodedSupported,
                onSelectMode = onSelectPhyMode
            )
        }
    }
}

@Composable
fun PhyModeSelectorBar(
    selectedMode: BtPhyMode,
    isCodedSupported: Boolean,
    onSelectMode: (BtPhyMode) -> Unit
) {
    val orderedModes = listOf(
        BtPhyMode.OFF,
        BtPhyMode.STANDARD_1M,
        BtPhyMode.EXTENDED_CODED,
        BtPhyMode.BOTH
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(TgDarkBackground)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        orderedModes.forEach { mode ->
            val isSelected = selectedMode == mode
            val bgColor = when {
                !isSelected -> Color.Transparent
                mode == BtPhyMode.OFF -> Color(0xFFE53935).copy(alpha = 0.28f)
                mode == BtPhyMode.EXTENDED_CODED -> TgCodedPurple.copy(alpha = 0.28f)
                mode == BtPhyMode.BOTH -> TgPrimaryBlue.copy(alpha = 0.28f)
                else -> TgGreenPulse.copy(alpha = 0.25f)
            }
            val textColor = when {
                !isSelected -> TgTextSecondary
                mode == BtPhyMode.OFF -> Color(0xFFFF8A80)
                mode == BtPhyMode.EXTENDED_CODED -> TgCodedPurple
                mode == BtPhyMode.BOTH -> TgAccentCyan
                else -> TgGreenPulse
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(if (mode == BtPhyMode.OFF) 0.7f else 1.1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .clickable { onSelectMode(mode) }
                    .padding(vertical = 6.dp, horizontal = 3.dp)
            ) {
                Text(
                    text = when (mode) {
                        BtPhyMode.OFF -> "OFF"
                        BtPhyMode.STANDARD_1M -> "Звичайний"
                        BtPhyMode.EXTENDED_CODED -> if (isCodedSupported) "Extended" else "Ext (Авто)"
                        BtPhyMode.BOTH -> "Обидва"
                    },
                    color = textColor,
                    fontSize = 11.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun PermissionAndBtBanner(
    hasPermissions: Boolean,
    isBluetoothEnabled: Boolean,
    onRequest: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3E2723)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (!hasPermissions) "Потрібні дозволи Bluetooth" else "Bluetooth вимкнено",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "Надайте доступ для сканування та відправки повідомлень у режимах 1M та Extended Range.",
                    color = Color(0xFFFFCCBC),
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(containerColor = TgAmberBurst)
            ) {
                Text("Увімкнути", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ChatListTab(
    conversations: List<ConversationSummary>,
    onOpenConversation: (Int) -> Unit,
    onSwitchToRadar: () -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filteredConversations = remember(conversations, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            conversations
        } else {
            conversations.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.lastMessageText.contains(q, ignoreCase = true)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 6.dp)
    ) {
        if (conversations.size > 2) {
            item(key = "chat_search_bar") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Пошук чатів...", color = TgTextSecondary, fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = TgTextSecondary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Очистити",
                                    tint = TgTextSecondary
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }

        items(filteredConversations, key = { it.conversationId }) { item ->
            TelegramConversationRow(
                summary = item,
                onClick = { onOpenConversation(item.conversationId) }
            )
            HorizontalDivider(
                color = TgSurfaceCard.copy(alpha = 0.6f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(start = 76.dp)
            )
        }

        if (conversations.size <= 1) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = TgSurfaceCard),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Як почати спілкування на двох телефонах?",
                            color = TgTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "1. Відкрийте «📢 Загальний Ефір (Mesh Chat)» вище — будь-який текст або стиснуте фото 📷 одразу прийде на другий телефон!\n" +
                                "2. Або перейдіть у вкладку «📡 Радар Зараз», щоб побачити телефони, які ловляться прямо в цю секунду, та їхню поточну відстань.",
                            color = TgTextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        TextButton(onClick = onSwitchToRadar) {
                            Text("Перейти до живого Радару →", color = TgAccentCyan)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelegramConversationRow(
    summary: ConversationSummary,
    onClick: () -> Unit
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        val avatarBrush = if (summary.isGlobalMesh) {
            Brush.linearGradient(listOf(Color(0xFF2AABEE), Color(0xFF229ED9)))
        } else {
            val hue = abs(summary.conversationId) % 3
            when (hue) {
                0 -> Brush.linearGradient(listOf(Color(0xFF7E57C2), Color(0xFF5E35B1)))
                1 -> Brush.linearGradient(listOf(Color(0xFF26A69A), Color(0xFF00897B)))
                else -> Brush.linearGradient(listOf(Color(0xFFEC407A), Color(0xFFD81B60)))
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(avatarBrush)
        ) {
            Text(
                text = if (summary.isGlobalMesh) "📢" else summary.title.take(2).uppercase(),
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = summary.title,
                    color = TgTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                if (summary.lastMessageTime > 0L) {
                    Text(
                        text = timeFormatter.format(Date(summary.lastMessageTime)),
                        color = TgTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = summary.subtitle,
                color = TgAccentCyan,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (summary.isLastMessageOutgoing && summary.lastMessageStatus != null) {
                    Text(
                        text = when (summary.lastMessageStatus) {
                            DeliveryStatus.QUEUED -> "🕒 "
                            DeliveryStatus.SENDING_BURST -> "📡 "
                            DeliveryStatus.SENT_TO_AIR -> "✓ "
                            DeliveryStatus.DELIVERED_ACK -> "✓✓ "
                            DeliveryStatus.FAILED -> "⚠️ "
                        },
                        color = if (summary.lastMessageStatus == DeliveryStatus.DELIVERED_ACK) TgGreenPulse else TgAccentCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = summary.lastMessageText,
                    color = TgTextSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (summary.unreadCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(TgPrimaryBlue)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = summary.unreadCount.toString(),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RadarScannerTab(
    peers: List<NearbyPeer>,
    config: RadioConfig,
    isCodedSupported: Boolean,
    onEmitBeacon: () -> Unit,
    onOpenPeerChat: (NearbyPeer) -> Unit,
    onToggleShowAllBle: (Boolean) -> Unit
) {
    // Only devices actively caught right now (within 15s) are in `peers`
    val pulsegramPeers = peers.filter { it.isPulsegramPeer }
    val otherBlePeers = peers.filter { !it.isPulsegramPeer }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = TgSurfaceCard),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Живий Радар (тільки активні ЗАРАЗ)",
                                color = TgTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Показує пристрої, які ловляться прямо зараз, і реальну відстань у поточний момент.",
                                color = TgGreenPulse,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onEmitBeacon,
                            colors = ButtonDefaults.buttonColors(containerColor = TgPrimaryBlue),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Маячок", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Показувати також чужі BLE-пристрої поруч",
                            color = TgTextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = config.showAllBleDevicesInRadar,
                            onCheckedChange = onToggleShowAllBle
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "ТЕЛЕФОНИ PULSELINK НА ЗВ'ЯЗКУ ЗАРАЗ (${pulsegramPeers.size})",
                color = TgAccentCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
        }

        if (pulsegramPeers.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = TgSurfaceHeader),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Прямо зараз у радіусі не зафіксовано активних маячків PulseLink.\nУвімкніть додаток на другому телефоні — він автоматично з'явиться тут протягом кількох секунд.",
                        color = TgTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(pulsegramPeers, key = { "pg_${it.nodeId}" }) { peer ->
                PeerRadarCard(
                    peer = peer,
                    showDebugRssi = config.showDebugInfo,
                    onClick = { onOpenPeerChat(peer) }
                )
            }
        }

        if (config.showAllBleDevicesInRadar) {
            item {
                Text(
                    text = "ІНШІ BLUETOOTH ПРИСТРОЇ ЗАРАЗ (${otherBlePeers.size})",
                    color = TgTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                )
            }
            items(otherBlePeers, key = { "ble_${it.macAddress}" }) { peer ->
                PeerRadarCard(
                    peer = peer,
                    showDebugRssi = config.showDebugInfo,
                    onClick = {}
                )
            }
        }
    }
}

@Composable
private fun PeerRadarCard(
    peer: NearbyPeer,
    showDebugRssi: Boolean,
    onClick: () -> Unit
) {
    val ageSec = ((System.currentTimeMillis() - peer.lastSeenTimestamp) / 1000L).coerceAtLeast(0L)
    val distMeters = peer.estimatedDistanceMeters
    val distanceColor = when {
        distMeters <= 5.0 -> TgGreenPulse
        distMeters <= 25.0 -> TgAmberBurst
        else -> TgCodedPurple
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = TgSurfaceCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = peer.isPulsegramPeer, onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (peer.isPulsegramPeer) TgPrimaryBlue.copy(alpha = 0.22f)
                        else TgSurfaceHeader
                    )
            ) {
                Text(
                    text = if (peer.isPulsegramPeer) "📱" else "🔹",
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = peer.nickname,
                        color = TgTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    if (peer.isPulsegramPeer && peer.hasEncryptionKey) {
                        Spacer(modifier = Modifier.width(6.dp))
                        PhyTagBadge(text = "🔒 E2EE", color = TgGreenPulse)
                    }
                    if (peer.isPulsegramPeer && showDebugRssi) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "#${peer.nodeId}",
                            color = TgTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (peer.seenOn1M) {
                        PhyTagBadge(text = "1M Standard", color = TgGreenPulse)
                    }
                    if (peer.seenOnCoded || peer.peerSupportsCoded) {
                        PhyTagBadge(text = "Extended LR", color = TgCodedPurple)
                    }
                    Text(
                        text = if (ageSec <= 2L) "• зараз" else "• ${ageSec}с тому",
                        color = TgGreenPulse,
                        fontSize = 11.sp
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SignalStrengthBars(
                        bars = peer.signalStrengthBars,
                        activeColor = distanceColor
                    )
                    Text(
                        text = "~${"%.1f".format(distMeters)} м",
                        color = distanceColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                if (showDebugRssi) {
                    Text(
                        text = "${peer.lastRssi} dBm",
                        color = TgTextSecondary,
                        fontSize = 11.sp
                    )
                }
                if (peer.isPulsegramPeer) {
                    Text(
                        text = "Написати →",
                        color = TgAccentCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SignalStrengthBars(bars: Int, activeColor: Color) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(bottom = 3.dp)
    ) {
        val heights = listOf(5.dp, 8.dp, 11.dp, 14.dp)
        heights.forEachIndexed { idx, h ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(h)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(if (idx < bars) activeColor else TgTextSecondary.copy(alpha = 0.25f))
            )
        }
    }
}

@Composable
fun PhyTagBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.18f))
            .border(0.5.dp, color.copy(alpha = 0.5f), RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TelegramChatDetailScreen(
    conversationId: Int,
    title: String,
    subtitle: String,
    isE2eeActive: Boolean,
    messages: List<ChatMessage>,
    pulseState: RadioPulseState,
    config: RadioConfig,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSendSticker: (PulseSticker) -> Unit,
    onSendPhotoUri: (Uri) -> Unit,
    onSendLocation: (Double, Double) -> Unit,
    onSendSos: (String, Double?, Double?) -> Unit,
    onRetryMessage: (String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onClearChat: () -> Unit,
    onSelectPhyMode: (BtPhyMode) -> Unit
) {
    val context = LocalContext.current
    val uiScope = rememberCoroutineScope()

    var inputText by rememberSaveable { mutableStateOf("") }
    var showAttachDialog by remember { mutableStateOf(false) }
    var showStickerTray by rememberSaveable { mutableStateOf(false) }
    var showSosDialog by remember { mutableStateOf(false) }
    var showClearChatConfirm by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var fullscreenPhoto by remember { mutableStateOf<Bitmap?>(null) }
    var contextMenuMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var pendingLocationAction by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onSendPhotoUri(uri)
        }
    }

    // Full-resolution camera capture via FileProvider (fixes tiny 120x160 TakePicturePreview thumbnail bug!)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            onSendPhotoUri(uri)
        }
        pendingCameraUri = null
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val action = pendingLocationAction
        pendingLocationAction = null
        if (granted && action == "LOC") {
            fetchCurrentOrLastLocation(context) { loc ->
                if (loc != null) {
                    onSendLocation(loc.latitude, loc.longitude)
                } else {
                    Toast.makeText(context, "Увімкніть GPS/Геолокацію для координат", Toast.LENGTH_SHORT).show()
                }
            }
        } else if (action == "LOC") {
            Toast.makeText(context, "Потрібен дозвіл на геолокацію", Toast.LENGTH_SHORT).show()
        }
    }

    fun triggerShareGpsLocation() {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            pendingLocationAction = "LOC"
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
            return
        }
        fetchCurrentOrLastLocation(context) { loc ->
            if (loc != null) {
                onSendLocation(loc.latitude, loc.longitude)
            } else {
                Toast.makeText(context, "GPS ще шукає супутники або вимкнено в системі", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        containerColor = TgDarkBackground,
        topBar = {
            Surface(
                color = TgSurfaceHeader,
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 8.dp)
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад",
                                tint = TgTextPrimary
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = title,
                                    color = TgTextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (isE2eeActive && conversationId != PulsePacketCodec.BROADCAST_NODE_ID) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    PhyTagBadge(text = "🔒 E2EE", color = TgGreenPulse)
                                }
                            }
                            Text(
                                text = "$subtitle • ${config.phyMode.shortBadge}",
                                color = TgAccentCyan,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        TextButton(
                            onClick = {
                                val next = when (config.phyMode) {
                                    BtPhyMode.OFF -> BtPhyMode.STANDARD_1M
                                    BtPhyMode.STANDARD_1M -> BtPhyMode.EXTENDED_CODED
                                    BtPhyMode.EXTENDED_CODED -> BtPhyMode.BOTH
                                    BtPhyMode.BOTH -> BtPhyMode.OFF
                                }
                                onSelectPhyMode(next)
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            PhyTagBadge(
                                text = config.phyMode.shortBadge,
                                color = when (config.phyMode) {
                                    BtPhyMode.OFF -> Color(0xFFFF8A80)
                                    BtPhyMode.STANDARD_1M -> TgGreenPulse
                                    BtPhyMode.EXTENDED_CODED -> TgCodedPurple
                                    BtPhyMode.BOTH -> TgAccentCyan
                                }
                            )
                        }

                        if (messages.isNotEmpty()) {
                            IconButton(onClick = { showClearChatConfirm = true }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Очистити чат",
                                    tint = TgTextSecondary
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = pulseState == RadioPulseState.TX_BURST) {
                        LinearProgressIndicator(
                            color = TgAmberBurst,
                            trackColor = TgSurfaceCard,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = TgSurfaceHeader,
                tonalElevation = 3.dp,
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    AnimatedVisibility(visible = showStickerTray) {
                        PulseStickerPickerTray(
                            onSelectSticker = { sticker ->
                                onSendSticker(sticker)
                                showStickerTray = false
                            },
                            onClose = { showStickerTray = false }
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        // Rich Attachment Button (Camera / Gallery / Offline GPS / SOS)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(TgSurfaceCard)
                                .clickable {
                                    showStickerTray = false
                                    showAttachDialog = true
                                }
                        ) {
                            Text(text = "📎", fontSize = 19.sp)
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Sticker Picker Toggle Button
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (showStickerTray) TgPrimaryBlue.copy(alpha = 0.28f) else TgSurfaceCard)
                                .border(
                                    width = if (showStickerTray) 1.dp else 0.dp,
                                    color = if (showStickerTray) TgPrimaryBlue else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable {
                                    showStickerTray = !showStickerTray
                                }
                        ) {
                            Text(text = "😊", fontSize = 19.sp)
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    if (isE2eeActive) "🔒 Зашифровано..." else "Текст, 😊 стікер або 📷...",
                                    color = TgTextSecondary,
                                    fontSize = 14.sp
                                )
                            },
                            maxLines = 4,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(
                                    if (inputText.isNotBlank()) TgPrimaryBlue else TgSurfaceCard
                                )
                                .clickable(enabled = inputText.isNotBlank()) {
                                    onSendMessage(inputText)
                                    inputText = ""
                                }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Надіслати",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(messages, key = { _, msg -> msg.id }) { index, msg ->
                val showDateHeader = index == 0 || !isSameCalendarDay(messages[index - 1].timestamp, msg.timestamp)
                if (showDateHeader) {
                    DateDividerChip(timestamp = msg.timestamp)
                }
                TelegramMessageBubble(
                    message = msg,
                    isGlobalMesh = conversationId == PulsePacketCodec.BROADCAST_NODE_ID,
                    showDebugInfo = config.showDebugInfo,
                    onPhotoClick = { bmp -> fullscreenPhoto = bmp },
                    onLocationClick = { lat, lon ->
                        val geoUri = Uri.parse(String.format(Locale.US, "geo:%.5f,%.5f?q=%.5f,%.5f(PulseLink)", lat, lon, lat, lon))
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, geoUri))
                        }.onFailure {
                            Toast.makeText(context, "Карти не встановлено ($lat, $lon)", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onLongPress = { contextMenuMessage = msg }
                )
            }
        }
    }

    // Rich Attachment Menu Dialog
    if (showAttachDialog) {
        AlertDialog(
            onDismissRequest = { showAttachDialog = false },
            containerColor = TgSurfaceCard,
            title = {
                Text("Додати вкладення", color = TgTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            showAttachDialog = false
                            val uri = createCameraTempUri(context)
                            if (uri != null) {
                                pendingCameraUri = uri
                                runCatching { cameraLauncher.launch(uri) }.onFailure {
                                    Toast.makeText(context, "Не вдалося відкрити камеру", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TgPrimaryBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📸 Фото з камери (HD 800px)", color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Button(
                        onClick = {
                            showAttachDialog = false
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TgSurfaceHeader),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🖼️ Фото з галереї (HD 800px)", color = TgAccentCyan, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Button(
                        onClick = {
                            showAttachDialog = false
                            showStickerTray = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TgSurfaceHeader),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("😊 Відкрити панель стікерів", color = TgCodedPurple, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Button(
                        onClick = {
                            showAttachDialog = false
                            triggerShareGpsLocation()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TgSurfaceHeader),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📍 Офлайн GPS координати", color = TgGreenPulse, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Button(
                        onClick = {
                            showAttachDialog = false
                            showSosDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🚨 Екстрений сигнал SOS", color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAttachDialog = false }) {
                    Text("Скасувати", color = TgTextSecondary)
                }
            }
        )
    }

    // SOS Confirmation Dialog
    if (showSosDialog) {
        var sosNote by remember { mutableStateOf("Потрібна допомога! Зв'яжіться зі мною.") }
        AlertDialog(
            onDismissRequest = { showSosDialog = false },
            containerColor = TgSurfaceCard,
            title = { Text("🚨 Екстрений сигнал SOS", color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Повідомлення буде виділене червоним і надіслане з автоматичним повтором (та GPS координатами, якщо доступні).",
                        color = TgTextSecondary,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = sosNote,
                        onValueChange = { sosNote = it.take(120) },
                        label = { Text("Коротке повідомлення") },
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val loc = fetchLastKnownLocation(context)
                        onSendSos(sosNote, loc?.latitude, loc?.longitude)
                        showSosDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Надіслати SOS", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSosDialog = false }) {
                    Text("Скасувати", color = TgTextSecondary)
                }
            }
        )
    }

    // Clear Chat Confirmation Dialog
    if (showClearChatConfirm) {
        AlertDialog(
            onDismissRequest = { showClearChatConfirm = false },
            containerColor = TgSurfaceCard,
            title = { Text("Очистити історію чату?", color = TgTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text("Усі повідомлення та фото в цьому чаті будуть видалені з вашого телефону.", color = TgTextSecondary)
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearChat()
                        showClearChatConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Очистити", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatConfirm = false }) {
                    Text("Скасувати", color = TgTextSecondary)
                }
            }
        )
    }

    // Long-Press Message Context Menu Dialog
    val menuMsg = contextMenuMessage
    if (menuMsg != null) {
        AlertDialog(
            onDismissRequest = { contextMenuMessage = null },
            containerColor = TgSurfaceCard,
            title = { Text("Дії з повідомленням", color = TgTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!menuMsg.isPhotoMessage) {
                        Button(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("PulseLink", menuMsg.text))
                                Toast.makeText(context, "Скопійовано", Toast.LENGTH_SHORT).show()
                                contextMenuMessage = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TgSurfaceHeader),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("📋 Копіювати текст", color = TgTextPrimary)
                        }
                    } else {
                        Button(
                            onClick = {
                                val bmp = UltraImageCompressor.decodePayloadToBitmap(menuMsg.text)
                                contextMenuMessage = null
                                if (bmp != null) {
                                    uiScope.launch {
                                        val ok = withContext(Dispatchers.IO) {
                                            UltraImageCompressor.saveBitmapToGallery(context, bmp)
                                        }
                                        Toast.makeText(
                                            context,
                                            if (ok) "Фото збережено в Галерею (PulseLink)" else "Помилка збереження",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TgSurfaceHeader),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("💾 Зберегти фото в галерею", color = TgAccentCyan)
                        }
                    }

                    if (menuMsg.isOutgoing && menuMsg.status != DeliveryStatus.DELIVERED_ACK) {
                        Button(
                            onClick = {
                                onRetryMessage(menuMsg.id)
                                contextMenuMessage = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TgPrimaryBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🔄 Надіслати повторно зараз", color = Color.White)
                        }
                    }

                    Button(
                        onClick = {
                            onDeleteMessage(menuMsg.id)
                            contextMenuMessage = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🗑️ Видалити повідомлення", color = Color.White)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { contextMenuMessage = null }) {
                    Text("Закрити", color = TgTextSecondary)
                }
            }
        )
    }

    // Full-Screen Photo Viewer Dialog with Save to Gallery
    val previewBmp = fullscreenPhoto
    if (previewBmp != null) {
        Dialog(
            onDismissRequest = { fullscreenPhoto = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.94f))
                    .clickable { fullscreenPhoto = null }
            ) {
                Image(
                    bitmap = previewBmp.asImageBitmap(),
                    contentDescription = "Повноекранне фото",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .align(Alignment.TopCenter)
                ) {
                    Text(
                        text = "${previewBmp.width} × ${previewBmp.height} px",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    IconButton(onClick = { fullscreenPhoto = null }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрити",
                            tint = Color.White
                        )
                    }
                }

                Button(
                    onClick = {
                        uiScope.launch {
                            val saved = withContext(Dispatchers.IO) {
                                UltraImageCompressor.saveBitmapToGallery(context, previewBmp)
                            }
                            Toast.makeText(
                                context,
                                if (saved) "✅ Збережено в Галерею (Pictures/PulseLink)" else "Не вдалося зберегти фото",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TgPrimaryBlue),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp)
                ) {
                    Text("💾 Зберегти в галерею", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DateDividerChip(timestamp: Long) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(TgSurfaceHeader.copy(alpha = 0.9f))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = formatDateDivider(timestamp),
                color = TgTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun PulseStickerPickerTray(
    onSelectSticker: (PulseSticker) -> Unit,
    onClose: () -> Unit
) {
    val rows = remember { PulseSticker.entries.chunked(4) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TgSurfaceCard)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = "😊 Стікери PulseLink (миттєва доставка 1 пакетом)",
                color = TgAccentCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Закрити ✕",
                color = TgTextSecondary,
                fontSize = 12.sp,
                modifier = Modifier
                    .clickable { onClose() }
                    .padding(4.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { rowStickers ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowStickers.forEach { stk ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(68.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color(stk.colorStart).copy(alpha = 0.85f),
                                            Color(stk.colorEnd).copy(alpha = 0.9f)
                                        )
                                    )
                                )
                                .border(
                                    0.5.dp,
                                    Color.White.copy(alpha = 0.2f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onSelectSticker(stk) }
                                .padding(4.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(text = stk.emoji, fontSize = 26.sp)
                                Text(
                                    text = stk.title,
                                    color = Color.White,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TelegramMessageBubble(
    message: ChatMessage,
    isGlobalMesh: Boolean,
    showDebugInfo: Boolean,
    onPhotoClick: (Bitmap) -> Unit,
    onLocationClick: (Double, Double) -> Unit,
    onLongPress: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val isOut = message.isOutgoing
    val decodedPhoto = remember(message.text) {
        if (message.isPhotoMessage) {
            UltraImageCompressor.decodePayloadToBitmap(message.text)
        } else null
    }
    val photoByteSize = remember(message.text) {
        if (message.isPhotoMessage) {
            UltraImageCompressor.getCompressedByteSize(message.text)
        } else 0
    }
    val coords = remember(message.text) { message.parsedCoordinates }
    val sticker = remember(message.text) { message.parsedSticker }

    val bubbleBg = when {
        message.isSosMessage -> Color(0xFF5D1419)
        isOut -> TgOutgoingBubble
        else -> TgIncomingBubble
    }

    Column(
        horizontalAlignment = if (isOut) Alignment.End else Alignment.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 110.dp, max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isOut) 16.dp else 4.dp,
                        bottomEnd = if (isOut) 4.dp else 16.dp
                    )
                )
                .background(bubbleBg)
                .then(
                    if (message.isSosMessage) {
                        Modifier.border(
                            1.dp,
                            Color(0xFFEF5350),
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isOut) 16.dp else 4.dp,
                                bottomEnd = if (isOut) 4.dp else 16.dp
                            )
                        )
                    } else Modifier
                )
                .combinedClickable(
                    onClick = {
                        when {
                            decodedPhoto != null -> onPhotoClick(decodedPhoto)
                            coords != null -> onLocationClick(coords.first, coords.second)
                        }
                    },
                    onLongClick = onLongPress
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                if (!isOut && isGlobalMesh) {
                    Text(
                        text = message.senderName,
                        color = if (message.isSosMessage) Color(0xFFFF8A80) else TgAccentCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                when {
                    sticker != null -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .width(176.dp)
                                .height(132.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color(sticker.colorStart),
                                            Color(sticker.colorEnd)
                                        )
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.24f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(10.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = sticker.emoji,
                                    fontSize = 52.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.Black.copy(alpha = 0.28f))
                                        .padding(horizontal = 10.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = sticker.title,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    decodedPhoto != null -> {
                        val ratio = (decodedPhoto.width.toFloat() / decodedPhoto.height.coerceAtLeast(1).toFloat())
                            .coerceIn(0.5f, 2.2f)
                        Image(
                            bitmap = decodedPhoto.asImageBitmap(),
                            contentDescription = "Фото ${decodedPhoto.width}x${decodedPhoto.height}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(ratio)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val dimLabel = "${decodedPhoto.width}×${decodedPhoto.height}"
                        Text(
                            text = if (showDebugInfo) "📷 $dimLabel ($photoByteSize B) • Натисніть для збільшення" else "📷 Фото $dimLabel • Натисніть щоб відкрити",
                            color = TgTextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    message.isPhotoMessage -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(TgSurfaceHeader)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "📷 Фото пошкоджено при передачі\n(затисніть для дій)",
                                color = TgAmberBurst,
                                fontSize = 12.sp
                            )
                        }
                    }

                    coords != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(TgSurfaceHeader.copy(alpha = 0.7f))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "📍 Офлайн GPS Координати",
                                color = TgGreenPulse,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = String.format(Locale.US, "%.5f, %.5f", coords.first, coords.second),
                                color = TgTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Натисніть, щоб відкрити на карті 🗺️",
                                color = TgAccentCyan,
                                fontSize = 11.sp
                            )
                        }
                    }

                    message.isSosMessage -> {
                        Text(
                            text = "🚨 ЕКСТРЕНИЙ СИГНАЛ SOS",
                            color = Color(0xFFFF8A80),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = message.text.removePrefix("SOS:").ifBlank { "Потрібна допомога!" },
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 20.sp
                        )
                    }

                    else -> {
                        Text(
                            text = message.text,
                            color = TgTextPrimary,
                            fontSize = 15.sp,
                            lineHeight = 20.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    if (message.isEncrypted) {
                        Text(
                            text = "🔒",
                            fontSize = 10.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    if (showDebugInfo) {
                        Text(
                            text = buildString {
                                append(message.receivedViaPhy)
                                if (message.rssiAtReceipt != null) {
                                    append(" (${message.rssiAtReceipt}dBm)")
                                }
                                if (message.hopCount > 0) {
                                    append(" • Hops:${message.hopCount}")
                                }
                            },
                            color = TgTextSecondary.copy(alpha = 0.85f),
                            fontSize = 10.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Text(
                        text = timeFormat.format(Date(message.timestamp)),
                        color = TgTextSecondary,
                        fontSize = 10.sp
                    )

                    if (isOut) {
                        Spacer(modifier = Modifier.width(5.dp))
                        val statusLabel = when (message.status) {
                            DeliveryStatus.QUEUED -> "🕒"
                            DeliveryStatus.SENDING_BURST -> {
                                if (showDebugInfo) "📡 Спроба ${message.retryCount} (${message.burstProgressPercent}%)" else "🕒"
                            }
                            DeliveryStatus.SENT_TO_AIR -> "✓"
                            DeliveryStatus.DELIVERED_ACK -> "✓✓"
                            DeliveryStatus.FAILED -> "🕒"
                        }
                        val statusColor = when (message.status) {
                            DeliveryStatus.DELIVERED_ACK -> TgGreenPulse
                            DeliveryStatus.SENDING_BURST -> TgAmberBurst
                            else -> TgAccentCyan
                        }
                        Text(
                            text = statusLabel,
                            color = statusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RadioAndEnergySettingsTab(
    config: RadioConfig,
    energyStats: EnergyStats,
    isCodedSupported: Boolean,
    isExtendedSupported: Boolean,
    onUpdateNickname: (String) -> Unit,
    onSelectPreset: (PowerPresetMode) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onToggleDebugInfo: (Boolean) -> Unit,
    onToggleMeshRelay: (Boolean) -> Unit,
    onToggleAutoRetry: (Boolean) -> Unit,
    onSetMaxRetries: (Int) -> Unit,
    onPanicWipe: () -> Unit
) {
    val context = LocalContext.current
    var showNicknameDialog by remember { mutableStateOf(false) }
    var showPanicWipeConfirm by remember { mutableStateOf(false) }

    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    val isBatteryExempt = remember {
        runCatching { powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true }.getOrDefault(false)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Profile Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = TgSurfaceCard),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ваш позивний: ${config.nickname}",
                                color = TgTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Node ID: #${config.localNodeId} • 🔒 E2EE ECDH P-256",
                                color = TgGreenPulse,
                                fontSize = 12.sp
                            )
                        }
                        Button(
                            onClick = { showNicknameDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = TgPrimaryBlue)
                        ) {
                            Text("Змінити ім'я")
                        }
                    }
                }
            }
        }

        // 2. Simple Power & Speed Modes
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = TgSurfaceCard),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "⚡ Режим роботи радіо та батареї",
                        color = TgTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    PowerPresetMode.entries.forEach { preset ->
                        val selected = config.powerPreset == preset
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) TgPrimaryBlue.copy(alpha = 0.22f) else TgSurfaceHeader
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .border(
                                    width = if (selected) 1.5.dp else 0.dp,
                                    color = if (selected) TgPrimaryBlue else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onSelectPreset(preset) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = preset.title,
                                        color = if (selected) TgAccentCyan else TgTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = preset.subtitle,
                                        color = TgTextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                                if (selected) {
                                    Text("✅", fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Notifications, Debug Toggle & Mesh Options
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = TgSurfaceCard),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "🔔 Сповіщення та відображення",
                        color = TgTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Сповіщення про нові повідомлення", color = TgTextPrimary, fontSize = 14.sp)
                            Text(
                                "Звук і спливаюче сповіщення коли приходить текст або фото",
                                color = TgTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = config.notificationsEnabled,
                            onCheckedChange = onToggleNotifications
                        )
                    }

                    HorizontalDivider(
                        color = TgSurfaceHeader,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Режим налагодження (Debug %)", color = TgTextPrimary, fontSize = 14.sp)
                            Text(
                                "Показувати відсотки відправки пакету (📡 45%), dBm та статистику",
                                color = TgTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = config.showDebugInfo,
                            onCheckedChange = onToggleDebugInfo
                        )
                    }

                    HorizontalDivider(
                        color = TgSurfaceHeader,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Авто-повтор кожні 0.6с до доставки (✓✓)", color = TgTextPrimary, fontSize = 14.sp)
                            Text(
                                "Саме повторює відправку раз у пів секунди-секунду поки інший телефон у сусідній кімнаті не підтвердить прийом",
                                color = TgTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(checked = config.autoRetryUnacked, onCheckedChange = onToggleAutoRetry)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mesh-ретрансляція (Multi-Hop)", color = TgTextPrimary, fontSize = 14.sp)
                            Text(
                                "Передає повідомлення далі через сусідні телефони",
                                color = TgTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(checked = config.enableMeshRelay, onCheckedChange = onToggleMeshRelay)
                    }
                }
            }
        }

        // 4. Background Reliability Helper (Samsung / Xiaomi / Doze)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = TgSurfaceCard),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "🔋 Надійна робота у фоні (коли екран вимкнено)",
                        color = TgTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isBatteryExempt) {
                            "✅ Оптимізацію батареї вимкнено — PulseLink стабільно приймає повідомлення навіть із вимкненим екраном."
                        } else {
                            "На Samsung / Xiaomi система може присипляти Bluetooth-сканер при вимкненому екрані. Додайте PulseLink у «Без обмежень» для миттєвої доставки."
                        },
                        color = if (isBatteryExempt) TgGreenPulse else TgTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    if (!isBatteryExempt) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                }.onFailure {
                                    Toast.makeText(context, "Відкрийте Налаштування → Батарея", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Відкрити налаштування батареї", color = TgAccentCyan, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // 5. Security, E2EE & Panic Wipe
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = TgSurfaceCard),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "🔒 Приватність та Безпека",
                        color = TgTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Приватні чати автоматично шифруються наскрізним шифруванням ECDH P-256 + AES-128-GCM.\n" +
                            "• Жодних серверів, реєстрації чи хмарного резервного копіювання — дані зберігаються лише на вашому пристрої.",
                        color = TgTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showPanicWipeConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🗑️ Швидке очищення (Panic Wipe)", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 6. Debug Telemetry (Only shown when Debug Mode is enabled)
        if (config.showDebugInfo) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = TgSurfaceCard),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "📊 Дебаг: Статистика радіо та чипа",
                            color = TgAccentCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "• Extended Advertising: ${if (isExtendedSupported) "✅ Так" else "Legacy 31B"}\n" +
                                "• LE Coded PHY (Long Range S=8): ${if (isCodedSupported) "✅ Підтримується" else "1M + Mesh"}\n" +
                                "• Середній Duty Cycle: ${"%.1f".format(energyStats.dutyCyclePercent)}%\n" +
                                "• Імпульсів сканування: ${energyStats.scanPulsesCount}\n" +
                                "• Імпульсів передачі: ${energyStats.txBurstsCount} (Fast-ACK збережень: ${energyStats.earlyAckSavesCount})\n" +
                                "• Пакетів TX / RX / ACK: ${energyStats.packetsSent} / ${energyStats.packetsReceived} / ${energyStats.acksReceived}",
                            color = TgTextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }

    if (showNicknameDialog) {
        var tempNick by remember { mutableStateOf(config.nickname) }
        AlertDialog(
            onDismissRequest = { showNicknameDialog = false },
            title = { Text("Ваш позивний в ефірі") },
            text = {
                OutlinedTextField(
                    value = tempNick,
                    onValueChange = { tempNick = it.take(14) },
                    label = { Text("Ім'я (до 14 символів)") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateNickname(tempNick)
                        showNicknameDialog = false
                    }
                ) {
                    Text("Зберегти")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNicknameDialog = false }) {
                    Text("Скасувати")
                }
            }
        )
    }

    if (showPanicWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showPanicWipeConfirm = false },
            containerColor = TgSurfaceCard,
            title = { Text("⚠️ Стерти всі дані (Panic Wipe)?", color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Це миттєво видалить УСІ чати, фото, список знайдених пристроїв та збережені E2EE-ключі з цього телефону. Відновити їх буде неможливо.",
                    color = TgTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onPanicWipe()
                        showPanicWipeConfirm = false
                        Toast.makeText(context, "Усі чати та ключі стерто", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Стерти все", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPanicWipeConfirm = false }) {
                    Text("Скасувати", color = TgTextSecondary)
                }
            }
        )
    }
}

private fun createCameraTempUri(context: Context): Uri? {
    return runCatching {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()
}

@SuppressLint("MissingPermission")
private fun fetchCurrentOrLastLocation(context: Context, onResult: (Location?) -> Unit) {
    val cached = fetchLastKnownLocation(context)
    if (cached != null) {
        onResult(cached)
        return
    }
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val provider = when {
            lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true -> LocationManager.GPS_PROVIDER
            lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (lm != null && provider != null) {
            runCatching {
                lm.getCurrentLocation(
                    provider,
                    null,
                    ContextCompat.getMainExecutor(context)
                ) { loc ->
                    onResult(loc ?: fetchLastKnownLocation(context))
                }
                return
            }
        }
    }
    onResult(null)
}

@SuppressLint("MissingPermission")
private fun fetchLastKnownLocation(context: Context): Location? {
    val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!hasFine && !hasCoarse) return null

    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
    var best: Location? = null
    for (p in providers) {
        val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull() ?: continue
        if (best == null || loc.time > best.time) {
            best = loc
        }
    }
    return best
}

private fun isSameCalendarDay(t1: Long, t2: Long): Boolean {
    val c1 = Calendar.getInstance().apply { timeInMillis = t1 }
    val c2 = Calendar.getInstance().apply { timeInMillis = t2 }
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
        c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
}

private fun formatDateDivider(timestamp: Long): String {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameYear = now.get(Calendar.YEAR) == target.get(Calendar.YEAR)
    val dayDiff = now.get(Calendar.DAY_OF_YEAR) - target.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 -> "Сьогодні"
        sameYear && dayDiff == 1 -> "Вчора"
        else -> SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date(timestamp))
    }
}
