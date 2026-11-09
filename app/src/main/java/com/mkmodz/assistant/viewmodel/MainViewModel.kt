package com.mkmodz.assistant.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.ai.client.generativeai.GenerativeModel
import com.mkmodz.assistant.automation.CallAutomation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _aiResponse = MutableLiveData<String?>()
    val aiResponse: LiveData<String?> = _aiResponse

    private val ctx: Context get() = getApplication<Application>().applicationContext

    companion object {
        private const val TAG = "MainViewModel"
        private val PREFIXES = listOf(
            "OPEN_APP", "YOUTUBE_PLAY", "SPOTIFY_PLAY", "WHATSAPP_MSG", "WHATSAPP_CALL",
            "CALL ", "SMS ", "FLASHLIGHT_ON", "FLASHLIGHT_OFF", "VOLUME_UP", "VOLUME_DOWN"
        )
    }

    /** True when the text is already a device command (no AI needed). */
    fun isDirectCommand(text: String): Boolean {
        val up = text.trim().uppercase()
        if (PREFIXES.any { up.startsWith(it) }) return true
        val lower = text.lowercase()
        if (lower.contains("flashlight") || lower.contains("torch")) return true
        if (lower.endsWith(" kholo") || lower.endsWith(" open karo")) return true
        return false
    }

    suspend fun processCommand(input: String) {
        val text = input.trim()
        if (text.isEmpty()) return
        val cmd = normalize(text)
        val reply: String? = try {
            when {
                cmd.startsWith("OPEN_APP", true) -> openApp(arg(cmd, "OPEN_APP"))
                cmd.startsWith("YOUTUBE_PLAY", true) -> youtube(arg(cmd, "YOUTUBE_PLAY"))
                cmd.startsWith("SPOTIFY_PLAY", true) -> spotify(arg(cmd, "SPOTIFY_PLAY"))
                cmd.startsWith("WHATSAPP_MSG", true) -> whatsappMsg(arg(cmd, "WHATSAPP_MSG"))
                cmd.startsWith("WHATSAPP_CALL", true) -> whatsappMsg(arg(cmd, "WHATSAPP_CALL"))
                cmd.startsWith("CALL ", true) -> call(arg(cmd, "CALL"))
                cmd.startsWith("SMS ", true) -> sms(arg(cmd, "SMS"))
                cmd.startsWith("FLASHLIGHT_ON", true) -> flashlight(true)
                cmd.startsWith("FLASHLIGHT_OFF", true) -> flashlight(false)
                cmd.startsWith("VOLUME_UP", true) -> volume(true)
                cmd.startsWith("VOLUME_DOWN", true) -> volume(false)
                else -> askGemini(text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "processCommand error: ${e.message}")
            "Sorry, kuch gadbad ho gayi."
        }
        if (!reply.isNullOrEmpty()) _aiResponse.postValue(reply)
    }

    // ── Parsing helpers ─────────────────────────────────────────────

    private fun normalize(text: String): String {
        val up = text.uppercase()
        if (PREFIXES.any { up.startsWith(it) }) return text
        val lower = text.lowercase()
        if (lower.contains("flashlight") || lower.contains("torch")) {
            val off = lower.contains(" off") || lower.contains("band")
            return if (off) "FLASHLIGHT_OFF" else "FLASHLIGHT_ON"
        }
        if (lower.endsWith(" kholo")) return "OPEN_APP " + text.dropLast(6).trim()
        if (lower.endsWith(" open karo")) return "OPEN_APP " + text.dropLast(10).trim()
        return text
    }

    private fun arg(cmd: String, prefix: String): String =
        cmd.substring(prefix.length).trim()

    // ── Actions ─────────────────────────────────────────────────────

    private fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }

    private fun openApp(name: String): String {
        if (name.isEmpty()) return "Kaunsa app kholu?"
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
        val q = name.lowercase()
        val match = apps.firstOrNull { it.loadLabel(pm).toString().lowercase() == q }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) }
        val pkg = match?.activityInfo?.packageName ?: return "$name app nahi mila."
        val launchIntent = pm.getLaunchIntentForPackage(pkg) ?: return "$name khul nahi paya."
        launch(launchIntent)
        return "$name khol diya."
    }

    private fun youtube(query: String): String {
        val uri = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))
        val i = Intent(Intent.ACTION_VIEW, uri)
        try {
            i.setPackage("com.google.android.youtube")
            launch(i)
        } catch (e: Exception) {
            launch(Intent(Intent.ACTION_VIEW, uri))
        }
        return "YouTube par $query chala rahi hoon."
    }

    private fun spotify(query: String): String {
        launch(Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:" + Uri.encode(query))))
        return "Spotify par $query dhoondh rahi hoon."
    }

    private fun findNumber(name: String): String? {
        if (name.isEmpty()) return null
        if (name.any { it.isDigit() } && name.count { it.isDigit() } >= 6) {
            return name.filter { it.isDigit() || it == '+' }
        }
        if (ctx.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val cursor = ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
            arrayOf("%$name%"),
            null
        )
        cursor?.use { if (it.moveToFirst()) return it.getString(0) }
        return null
    }

    private fun call(name: String): String {
        val number = findNumber(name) ?: return "$name ka number nahi mila."
        CallAutomation.makeCall(ctx, number)
        return "$name ko call laga rahi hoon."
    }

    private fun whatsappMsg(args: String): String {
        val parts = args.split(" ", limit = 2)
        val name = parts.firstOrNull().orEmpty()
        val message = parts.getOrNull(1).orEmpty()
        val raw = findNumber(name) ?: return "$name ka number nahi mila."
        var digits = raw.filter { it.isDigit() }
        if (digits.length == 10) digits = "91$digits"
        val url = "https://wa.me/$digits" + if (message.isNotEmpty()) "?text=" + Uri.encode(message) else ""
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            i.setPackage("com.whatsapp")
            launch(i)
        } catch (e: Exception) {
            launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
        return "$name ki WhatsApp chat khol di."
    }

    private fun sms(args: String): String {
        val parts = args.split(" ", limit = 2)
        val name = parts.firstOrNull().orEmpty()
        val message = parts.getOrNull(1).orEmpty()
        if (message.isEmpty()) return "Message kya bhejna hai?"
        val number = findNumber(name) ?: return "$name ka number nahi mila."
        val sm: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ctx.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        sm.sendTextMessage(number, null, message, null, null)
        return "$name ko SMS bhej diya."
    }

    private fun flashlight(on: Boolean): String {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull() ?: return "Flashlight nahi mili."
        cm.setTorchMode(id, on)
        return if (on) "Flashlight on kar di." else "Flashlight band kar di."
    }

    private fun volume(up: Boolean): String {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )
        return if (up) "Volume badha diya." else "Volume kam kar diya."
    }

    private suspend fun askGemini(prompt: String): String {
        val key = ctx.getSharedPreferences("mkmodz_prefs", Context.MODE_PRIVATE)
            .getString("api_key", "").orEmpty()
        if (key.isEmpty()) return "Settings me pehle Gemini API key daalo."
        return withContext(Dispatchers.IO) {
            val model = GenerativeModel(modelName = "gemini-2.0-flash", apiKey = key)
            model.generateContent(prompt).text ?: "Mujhe samajh nahi aaya."
        }
    }
}
