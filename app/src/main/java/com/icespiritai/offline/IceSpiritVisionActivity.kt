package com.icespiritai.offline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.icespiritai.offline.ui.MainScreen

/**
 * Entry Activity of IceSpiritAI_Vision (冰灵锐目).
 *
 * Compose host — wires [MainScreen] as the root composition. Phase 1 UI is a
 * text-only summary of the analysis pipeline; future tasks add image preview,
 * rule-edit, and history screens.
 */
class IceSpiritVisionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MainScreen() }
    }
}
