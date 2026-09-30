package com.orbital.bridge.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.orbital.bridge.OrbitalBridgeClient
import com.orbital.bridge.OrbitalDiscoveryService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LaptopBridgeActivity : ComponentActivity() {

    @Inject
    lateinit var bridgeClient: OrbitalBridgeClient

    @Inject
    lateinit var discoveryService: OrbitalDiscoveryService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF0A0C14),
                    surface = Color(0xFF131625),
                    primary = Color(0xFF7C3AED)
                )
            ) {
                LaptopBridgeScreen(
                    bridgeClient = bridgeClient,
                    discoveryService = discoveryService,
                    onNavigateBack = { finish() }
                )
            }
        }
    }
}
