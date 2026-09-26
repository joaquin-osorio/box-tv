package com.boxtv.source

import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps

/** Resolves with [primary] and, only when it doesn't know the host, with [fallback]. */
class FallbackDns(private val primary: Dns, private val fallback: Dns) : Dns {

    override fun lookup(hostname: String): List<InetAddress> = try {
        primary.lookup(hostname)
    } catch (_: UnknownHostException) {
        fallback.lookup(hostname)
    }
}

/**
 * The system DNS, falling back to Cloudflare's DNS-over-HTTPS for hosts it can't resolve. Some ISPs'
 * DNS answer NXDOMAIN for streaming sites; DoH goes over HTTPS to a public resolver, so the ISP can't
 * rewrite the answer. The resolver is reached by IP, so it needs no DNS itself.
 */
fun dnsWithDohFallback(): Dns {
    val doh = DnsOverHttps.Builder()
        .client(OkHttpClient())
        .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1)),
            InetAddress.getByAddress(byteArrayOf(1, 0, 0, 1))
        )
        .build()
    return FallbackDns(Dns.SYSTEM, doh)
}
