package com.icespiritai.offline

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Entry Activity of IceSpiritAI_Vision (冰灵锐目).
 *
 * Initial scaffold (`modelProfile = "shell"`): renders a static placeholder
 * greeting so the project compiles + launches end-to-end before vision-model
 * profiles are wired in. Future PRs branch on BuildConfig.MODEL_PROFILE to
 * load the appropriate offline model and render the actual judgement UI.
 */
class IceSpiritVisionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val greeting = TextView(this).apply {
            textSize = 20f
            text = "Hello Vision\nprofile: ${BuildConfig.MODEL_PROFILE}"
            setPadding(48, 48, 48, 48)
        }
        setContentView(greeting)
    }
}