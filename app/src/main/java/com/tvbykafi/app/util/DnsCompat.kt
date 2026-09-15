package com.tvbykafi.app.util

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Inet4Address
import java.net.UnknownHostException
import java.io.ByteArrayOutputStream

/**
 * Purona Android TV/device gulo IPv6 route chara IPv6 address e connect korte
 * chay (ENETUNREACH) — playlist/stream download fail kore. Ei helper hostname
 * resolve kore IPv4 address gulo age dey. Jodi system DNS IPv4 na dey (DNS64
 * ba filtering), tahole Google Public DNS (8.8.8.8) e directly A-record query
 * kore real IPv4 ber kore.
 */
object DnsCompat {

    fun resolveIPv4First(host: String): List<InetAddress> {
        val system = try {
            InetAddress.getAllByName(host).toList()
        } catch (_: UnknownHostException) {
            emptyList()
        }
        val v4 = system.filter { it is Inet4Address }
        if (v4.isNotEmpty()) return v4

        // System DNS theke IPv4 paoa jay nai — Google DNS e directly query
        for (dns in listOf("8.8.8.8", "8.8.4.4")) {
            val addr = queryARecord(host, dns)
            if (addr != null) return listOf(addr)
        }

        // Kono IPv4 paoa jay nai — ja ache seta diye try koro
        return system
    }

    // ---- Minimal DNS A-record query over UDP (port 53) ----

    private fun queryARecord(host: String, dnsServer: String): InetAddress? {
        return try {
            DatagramSocket().use { sock ->
                sock.soTimeout = 4000
                val query = buildQuery(host)
                sock.send(DatagramPacket(query, query.size, InetAddress.getByName(dnsServer), 53))
                val buf = ByteArray(512)
                val resp = DatagramPacket(buf, 512)
                sock.receive(resp)
                parseARecords(buf, resp.length).firstOrNull()
                    ?.let { InetAddress.getByAddress(it) }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildQuery(host: String): ByteArray {
        val bos = ByteArrayOutputStream()
        bos.write(0x12); bos.write(0x34) // transaction id
        bos.write(0x01); bos.write(0x00) // recursion desired
        bos.write(0x00); bos.write(0x01) // qdcount = 1
        bos.write(0x00); bos.write(0x00) // ancount
        bos.write(0x00); bos.write(0x00) // nscount
        bos.write(0x00); bos.write(0x00) // arcount
        host.split(".").forEach { label ->
            bos.write(label.length)
            bos.write(label.toByteArray(Charsets.US_ASCII))
        }
        bos.write(0)                      // qname terminator
        bos.write(0x00); bos.write(0x01)  // qtype = A
        bos.write(0x00); bos.write(0x01)  // qclass = IN
        return bos.toByteArray()
    }

    private fun parseARecords(data: ByteArray, len: Int): List<ByteArray> {
        val out = mutableListOf<ByteArray>()
        if (len < 12) return out
        // skip question section
        var i = 12
        while (i < len && data[i].toInt() != 0) i += (data[i].toInt() and 0xFF) + 1
        i += 5 // null byte + qtype(2) + qclass(2)
        val ancount = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
        repeat(ancount) {
            if (i >= len) return out
            if (data[i].toInt() and 0xC0 == 0xC0) {
                i += 2 // compressed name pointer
            } else {
                while (i < len && data[i].toInt() != 0) i += (data[i].toInt() and 0xFF) + 1
                i++
            }
            if (i + 10 > len) return out
            val type = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            val rdlength = ((data[i + 8].toInt() and 0xFF) shl 8) or (data[i + 9].toInt() and 0xFF)
            i += 10
            if (type == 1 && rdlength == 4 && i + 4 <= len) {
                out.add(data.copyOfRange(i, i + 4))
            }
            i += rdlength
        }
        return out
    }
}
