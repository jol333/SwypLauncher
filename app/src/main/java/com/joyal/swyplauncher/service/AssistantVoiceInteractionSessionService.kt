package com.joyal.swyplauncher.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.util.Log

class AssistantVoiceInteractionSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        Log.d("AssistantSession", "Creating new session")
        return AssistantVoiceInteractionSession(this)
    }

    private class AssistantVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

        override fun onShow(args: Bundle?, showFlags: Int) {
            super.onShow(args, showFlags)
            Log.d("AssistantSession", "onShow called with flags: $showFlags")
            
            // Launch the AssistActivity as an overlay using assistant channel to avoid triggering PiP
            val intent = Intent(context, com.joyal.swyplauncher.ui.AssistActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION) // Prevents onUserLeaveHint in background app (avoids PiP)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("SHOW_FLAGS", showFlags)
            }
            val assistantStarted = try {
                startAssistantActivity(intent)
                true
            } catch (e: Exception) {
                Log.w("AssistantSession", "startAssistantActivity failed, falling back", e)
                false
            }
            if (!assistantStarted) {
                try {
                    // Falls back to standard startActivity if startAssistantActivity fails
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e("AssistantSession", "Fallback startActivity failed", e)
                }
            }
            
            // Hide the voice interaction session window with a slight delay to ensure it doesn't
            // interfere with the activity launch, while still clearing the way quickly.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                hide()
            }, 50)
        }

        override fun onHide() {
            super.onHide()
            Log.d("AssistantSession", "onHide called")
        }
        
        override fun onDestroy() {
            super.onDestroy()
            Log.d("AssistantSession", "onDestroy called")
        }
    }
}
