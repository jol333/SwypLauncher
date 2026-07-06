package com.joyal.swyplauncher.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.joyal.swyplauncher.R

/**
 * Starts an activity for the given [intent], guarding against devices where no
 * activity can handle it (e.g. no browser installed for a web ACTION_VIEW intent,
 * or no app to handle a share).
 *
 * Without this guard, [Context.startActivity] throws [ActivityNotFoundException]
 * which crashes the app. Instead, a short toast is shown.
 *
 * @return true if the activity was started, false if it could not be handled.
 */
fun Context.safeStartActivity(
    intent: Intent,
    errorMessageRes: Int = R.string.no_app_to_handle_action
): Boolean {
    return try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, errorMessageRes, Toast.LENGTH_SHORT).show()
        false
    } catch (_: Exception) {
        Toast.makeText(this, errorMessageRes, Toast.LENGTH_SHORT).show()
        false
    }
}

/** Intent that opens a Google web search for [query] in the user's browser. */
fun webSearchIntent(query: String): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}"))

/** Intent that opens a Play Store app search for [query]. */
fun playStoreSearchIntent(query: String): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=${Uri.encode(query)}&c=apps"))

/** Plain-text share intent for [text]. Wrap with [Intent.createChooser] before launching. */
fun shareTextIntent(text: String): Intent =
    Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }

/** Copies [text] to the system clipboard. */
fun Context.copyToClipboard(text: String, label: String = "Copied Text") {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

/** Shares [text] via the system share sheet, guarding against devices with no handler. */
fun Context.shareText(text: String, chooserTitle: String) {
    safeStartActivity(Intent.createChooser(shareTextIntent(text), chooserTitle))
}
