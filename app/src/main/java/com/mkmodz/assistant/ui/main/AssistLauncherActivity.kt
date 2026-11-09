package com.mkmodz.assistant.ui.main

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class AssistLauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("TRIGGER_MIC", true)
        }
        startActivity(intent)
        finish()
    }
}
