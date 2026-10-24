package com.poc.doomguard.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import com.poc.doomguard.data.Template
import com.poc.doomguard.data.TemplateStore

// Checkpoint screen: launched by the service once the count reaches threshold + 10.
// Blocks for 10s in the user's chosen template (Bubble / Reel / Depth), then the reel count resets.
class QuoteBreakActivity : ComponentActivity() {
    companion object {
        /** True while the break is on screen. The service hides its overlay and stops counting. */
        @Volatile var visible = false
    }

    override fun onStart() {
        super.onStart()
        visible = true
    }

    override fun onStop() {
        visible = false
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val template = remember { TemplateStore.get(this) }
            // Own task (taskAffinity="" in the manifest): removing it returns to Instagram, not MainActivity.
            BreakHost(onDone = { finishAndRemoveTask() }) { state, done ->
                when (template) {
                    Template.BUBBLE -> BreakBubble(state, done)
                    Template.REEL -> BreakReel(state, done)
                    Template.DEPTH -> BreakDepth(state, done)
                }
            }
        }
    }
}
