package com.octopus.pigeon.post.service

import java.util.Properties

/**
 * How the app talks to the SMTP server.
 *
 * These are not user settings, they are how this build connects, so they live
 * here rather than in the config UI. The diagnostic report reads them from the
 * same place, which is the only way to stop the report from claiming a
 * transport setting that the client does not actually use.
 */
object SmtpTransport {
    /** AUTH is always on: without it there is nothing to authenticate with. */
    const val AUTH_ENABLED = true

    /**
     * Implicit TLS, on by default.
     *
     * A hosted mail server on 465 expects the connection to be encrypted before
     * the login is sent, so this cannot be turned off without downgrading the
     * credentials onto the wire.
     */
    const val SSL_ENABLED = true

    /**
     * STARTTLS is not used. Most providers take either implicit TLS on 465 or
     * STARTTLS on 587, and the port the user configures decides which, so
     * enabling both would break the 465 case.
     */
    const val STARTTLS_ENABLED = false

    fun applyTo(properties: Properties) {
        properties.put("mail.smtp.auth", AUTH_ENABLED.toString())
        properties.put("mail.smtp.ssl.enable", SSL_ENABLED.toString())
        properties.put("mail.smtp.starttls.enable", STARTTLS_ENABLED.toString())
    }
}
