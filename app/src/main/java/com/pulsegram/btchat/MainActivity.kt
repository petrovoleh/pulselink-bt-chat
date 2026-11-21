package com.pulsegram.btchat

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.pulsegram.btchat.ui.PulseViewModel
import com.pulsegram.btchat.ui.PulsegramApp
import com.pulsegram.btchat.ui.theme.PulsegramTheme

class MainActivity : ComponentActivity() {

    private val viewModel: PulseViewModel by viewModels()

    private var hasRequiredPermissions by mutableStateOf(false)
    private var isBluetoothEnabled by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        refreshPermissionAndBtState()
        if (hasRequiredPermissions) {
            if (!isBluetoothEnabled) {
                promptEnableBluetooth()
            } else {
                viewModel.onPermissionsGrantedAndBtReady()
            }
        }
    }

    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        refreshPermissionAndBtState()
        if (hasRequiredPermissions && isBluetoothEnabled) {
            viewModel.onPermissionsGrantedAndBtReady()
        }
    }

    private val btStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                refreshPermissionAndBtState()
                if (hasRequiredPermissions && isBluetoothEnabled) {
                    viewModel.onPermissionsGrantedAndBtReady()
                } else if (!isBluetoothEnabled) {
                    viewModel.onBluetoothDisabled()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        ContextCompat.registerReceiver(
            this,
            btStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        handleNotificationIntent(intent)

        refreshPermissionAndBtState()
        if (hasRequiredPermissions && isBluetoothEnabled) {
            viewModel.onPermissionsGrantedAndBtReady()
        } else {
            requestPermissionsAndBluetooth()
        }

        setContent {
            PulsegramTheme {
                PulsegramApp(
                    viewModel = viewModel,
                    hasPermissions = hasRequiredPermissions,
                    isBluetoothEnabled = isBluetoothEnabled,
                    onRequestPermissionsAndBt = { requestPermissionsAndBluetooth() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val convId = intent?.getIntExtra("open_conversation_id", -1) ?: -1
        if (convId != -1) {
            viewModel.openConversation(convId)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppForegroundChanged(true)
        refreshPermissionAndBtState()
        if (hasRequiredPermissions && isBluetoothEnabled) {
            viewModel.onPermissionsGrantedAndBtReady()
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.onAppForegroundChanged(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(btStateReceiver) }
    }

    private fun requiredPermissions(): Array<String> {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
            perms.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
            perms.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return perms.toTypedArray()
    }

    private fun refreshPermissionAndBtState() {
        val corePerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        hasRequiredPermissions = corePerms.all { perm ->
            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        }

        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        isBluetoothEnabled = btManager?.adapter?.isEnabled == true
    }

    private fun requestPermissionsAndBluetooth() {
        refreshPermissionAndBtState()
        if (!hasRequiredPermissions) {
            permissionLauncher.launch(requiredPermissions())
        } else if (!isBluetoothEnabled) {
            promptEnableBluetooth()
        } else {
            viewModel.onPermissionsGrantedAndBtReady()
        }
    }

    private fun promptEnableBluetooth() {
        runCatching {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBtLauncher.launch(enableBtIntent)
        }
    }
}
