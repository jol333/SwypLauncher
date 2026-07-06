package com.joyal.swyplauncher.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class AssistantVoiceInteractionSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return AssistantVoiceInteractionSession(this)
    }

    private class AssistantVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

        private val mainHandler = Handler(Looper.getMainLooper())

        override fun onPrepareShow(args: Bundle?, showFlags: Int) {
            // No UI of our own — disable the session window entirely so the system
            // doesn't have to lay out / draw an empty session surface on the cold path.
            setUiEnabled(false)
        }

        override fun onShow(args: Bundle?, showFlags: Int) {
            super.onShow(args, showFlags)

            val intent = Intent(context, com.joyal.swyplauncher.ui.AssistActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION) // Prevents onUserLeaveHint in background app (avoids PiP)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                putExtra("SHOW_FLAGS", showFlags)
            }

            val assistantStarted = try {
                startAssistantActivity(intent)
                true
            } catch (_: Exception) {
                // startAssistantActivity failed; fall back to context.startActivity below.
                false
            }
            if (!assistantStarted) {
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                    // Both the assistant launch and the fallback failed; nothing more we can do.
                }
            }

            // Defer tearing down the session: calling hide()/finish() in the same frame as
            // startAssistantActivity() races with the activity-launch binder call on the
            // cold path (after Doze / process death), and the system server can drop the
            // launch when the session goes away before the activity is attached as the
            // active assistant context. Posting to the next main-loop iteration lets the
            // launch reach ATMS first, then we end the session cleanly so the next
            // gesture isn't blocked by a lingering session.
            mainHandler.post {
                try {
                    finish()
                } catch (_: Exception) {
                    // finish() failed; fall back to hide().
                    try { hide() } catch (_: Exception) {}
                }
            }
        }

        override fun onHide() {
            super.onHide()
        }

        override fun onDestroy() {
            super.onDestroy()
            mainHandler.removeCallbacksAndMessages(null)
        }
    }
}
