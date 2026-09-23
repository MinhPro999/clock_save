package com.minh.statusbarclock

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.CompoundButton
import android.widget.Switch

/**
 * Single screen with a single ON/OFF switch.
 *
 * No settings screen, no extra buttons. The activity is only a UI control:
 * it can be destroyed/recreated and the clock keeps running, because the
 * accessibility service owns the feature lifecycle.
 */
class MainActivity : Activity() {

    companion object {
        private const val TAG = "StatusBarClock"
    }

    private lateinit var toggle: Switch

    private val listener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        Log.i(TAG, "Switch changed: $isChecked")
        if (isChecked) {
            ClockController.enable(this)
            if (!ClockAccessibilityService.isServiceEnabled(this)) {
                // Mandatory OS flow: take the user to system Accessibility
                // settings to grant the service.
                ClockAccessibilityService.openAccessibilitySettings(this)
            }
        } else {
            ClockController.disable(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        toggle = findViewById(R.id.switch_clock)
        toggle.setOnCheckedChangeListener(listener)
    }

    override fun onResume() {
        super.onResume()
        // The switch reflects the saved state. The actual accessibility
        // service state is always re-checked, never trusted blindly.
        val enabled = ClockStateStore.isEnabled(this)
        toggle.setOnCheckedChangeListener(null)
        toggle.isChecked = enabled
        toggle.setOnCheckedChangeListener(listener)
        Log.i(
            TAG,
            "onResume enabled=$enabled " +
                "serviceEnabled=${ClockAccessibilityService.isServiceEnabled(this)}"
        )
    }
}
