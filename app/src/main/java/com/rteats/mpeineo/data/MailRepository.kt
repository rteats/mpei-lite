package com.rteats.mpeineo.data

import android.content.Context

/**
 * Compatibility shim for pre-OWA settings cleanup and existing diagnostic callers.
 * No IMAP connection, authentication or plaintext password access remains.
 */
class MailRepository(
    private val context: Context,
    private val diagnostics: DiagnosticLog? = null,
) {
    internal fun logState(event: String) { diagnostics?.log("MAIL", event) }

    val contextForNotifications: Context get() = context.applicationContext

    /** Older releases stored IMAP credentials; OWA no longer uses these. */
    val credentials = LegacyImapCredentialsCleanup(context)

    fun forgetTrustedCertificate() {
        context.getSharedPreferences("mail_tls_pin_v1", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}

class LegacyImapCredentialsCleanup(context: Context) {
    private val preferences =
        context.getSharedPreferences("mail_imap_auth", Context.MODE_PRIVATE)

    fun clear() {
        preferences.edit().clear().apply()
    }
}
