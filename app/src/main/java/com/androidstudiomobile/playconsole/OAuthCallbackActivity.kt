package com.androidstudiomobile.playconsole

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Receives the OAuth 2.0 redirect from the browser after the user signs in
 * to Google Play Console. The authorization code is broadcast so any
 * running PlayConsoleScreen can pick it up.
 */
class OAuthCallbackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val code = intent?.data?.getQueryParameter("code")
        if (code != null) {
            sendBroadcast(Intent("com.androidstudiomobile.OAUTH_CODE").putExtra("code", code))
        }
        finish()
    }
}
