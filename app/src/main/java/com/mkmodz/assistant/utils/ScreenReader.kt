package com.mkmodz.assistant.utils

import android.view.accessibility.AccessibilityNodeInfo

object ScreenReader {

    fun dump(root: AccessibilityNodeInfo?): String? {
        if (root == null) return null
        val sb = StringBuilder()
        try {
            collect(root, sb, 0)
        } catch (e: Exception) {
            return null
        }
        val result = sb.toString().trim()
        return if (result.isEmpty()) null else result
    }

    private fun collect(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        if (node == null || depth > 12 || sb.length > 2000) return
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        if (!text.isNullOrEmpty()) sb.append(text).append('\n')
        else if (!desc.isNullOrEmpty()) sb.append(desc).append('\n')
        for (i in 0 until node.childCount) {
            collect(node.getChild(i), sb, depth + 1)
        }
    }
}
