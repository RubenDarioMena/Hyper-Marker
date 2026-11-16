package com.hypermarker

import com.hypermarker.browser.BrowserCookies
import org.junit.Assert.*
import org.junit.Test

class BrowserCookiesTest {
    @Test fun onlyCloudflareChallengesAreTreatedAsVerification() {
        assertTrue(BrowserCookies.isChallenge(403, mapOf("Server" to "cloudflare", "cf-mitigated" to "challenge")))
        assertTrue(BrowserCookies.isChallenge(503, mapOf("CF-Mitigated" to "Challenge")))
        assertFalse(BrowserCookies.isChallenge(403, mapOf("Server" to "cloudflare", "CF-RAY" to "abc")))
        assertFalse(BrowserCookies.isChallenge(200, mapOf("cf-mitigated" to "challenge")))
        assertFalse(BrowserCookies.isChallenge(404, null))
    }
}
