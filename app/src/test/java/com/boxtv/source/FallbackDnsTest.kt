package com.boxtv.source

import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class FallbackDnsTest {

    private val primaryAddress = InetAddress.getByAddress(byteArrayOf(10, 0, 0, 1))
    private val fallbackAddress = InetAddress.getByAddress(byteArrayOf(10, 0, 0, 2))

    /** Knows only [known]; counts lookups. */
    private class FakeDns(private val known: Map<String, InetAddress>) : Dns {
        var lookups = 0

        override fun lookup(hostname: String): List<InetAddress> {
            lookups++
            return listOf(known[hostname] ?: throw UnknownHostException(hostname))
        }
    }

    @Test
    fun `uses the primary answer without asking the fallback`() {
        val fallback = FakeDns(mapOf("site.example" to fallbackAddress))
        val dns = FallbackDns(FakeDns(mapOf("site.example" to primaryAddress)), fallback)

        assertEquals(listOf(primaryAddress), dns.lookup("site.example"))
        assertEquals(0, fallback.lookups)
    }

    @Test
    fun `asks the fallback when the primary does not know the host`() {
        val dns = FallbackDns(FakeDns(emptyMap()), FakeDns(mapOf("blocked.example" to fallbackAddress)))

        assertEquals(listOf(fallbackAddress), dns.lookup("blocked.example"))
    }

    @Test
    fun `fails when neither knows the host`() {
        val dns = FallbackDns(FakeDns(emptyMap()), FakeDns(emptyMap()))

        try {
            dns.lookup("nowhere.example")
            fail("Expected UnknownHostException")
        } catch (_: UnknownHostException) {
            // Expected.
        }
    }
}
