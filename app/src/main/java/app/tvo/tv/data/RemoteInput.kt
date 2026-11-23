package app.tvo.tv.data

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlin.concurrent.thread

/**
 * Tiny local web page so the user can paste an add-on URL from the phone
 * (same Wi-Fi) instead of typing it with the TV remote.
 */
class RemoteInputServer(private val onText: (String) -> Unit) {
    private var server: ServerSocket? = null

    fun start(): Int? = runCatching {
        val s = ServerSocket(0)
        server = s
        thread(isDaemon = true, name = "tvo-remote-input") {
            while (!s.isClosed) {
                val c = runCatching { s.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { runCatching { handle(c) }; runCatching { c.close() } }
            }
        }
        s.localPort
    }.getOrNull()

    fun stop() { runCatching { server?.close() } }

    private fun handle(c: Socket) {
        c.soTimeout = 10_000
        val reader = BufferedReader(InputStreamReader(c.getInputStream(), Charsets.UTF_8))
        val requestLine = reader.readLine() ?: return
        var length = 0
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            if (line.lowercase().startsWith("content-length:")) length = line.substringAfter(':').trim().toIntOrNull() ?: 0
        }
        var sent = false
        if (requestLine.startsWith("POST") && length in 1..8192) {
            val buf = CharArray(length)
            var read = 0
            while (read < length) { val n = reader.read(buf, read, length - read); if (n < 0) break; read += n }
            val body = String(buf, 0, read)
            val value = body.split('&').firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
                ?.let { URLDecoder.decode(it, "UTF-8").trim() }
            if (!value.isNullOrBlank()) { onText(value); sent = true }
        }
        val html = page(sent)
        val bytes = html.toByteArray(Charsets.UTF_8)
        val out = c.getOutputStream()
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray())
        out.write(bytes); out.flush()
    }

    private fun page(sent: Boolean) = """
<!doctype html><html lang="fr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="apple-mobile-web-app-capable" content="yes"><meta name="apple-mobile-web-app-status-bar-style" content="default">
<title>TVO</title><style>
:root{--bg:#0b0b12;--card:#161622;--text:#f5f5fa;--muted:#a6a6bc;--a:#8b5cf6;--b:#22d3ee}
*{box-sizing:border-box}html,body{margin:0;background:var(--bg);color:var(--text);font-family:-apple-system,system-ui,sans-serif}
main{min-height:100dvh;padding:calc(24px + env(safe-area-inset-top)) calc(20px + env(safe-area-inset-right)) calc(24px + env(safe-area-inset-bottom)) calc(20px + env(safe-area-inset-left));max-width:560px;margin:0 auto}
h1{font-size:28px;margin:0 0 6px}p{color:var(--muted);line-height:1.45;margin:0 0 20px}
.card{background:var(--card);border-radius:16px;padding:18px}
textarea{width:100%;min-height:120px;font-size:16px;color:var(--text);background:#0f0f18;border:1px solid #2c2c40;border-radius:12px;padding:12px;resize:vertical}
textarea:focus{outline:2px solid var(--b)}
button{margin-top:14px;width:100%;font-size:17px;font-weight:700;border:0;border-radius:12px;padding:14px;color:#fff;background:linear-gradient(90deg,var(--a),var(--b))}
button:active{opacity:.85}.ok{color:#34d399;font-weight:700;margin-bottom:14px}
</style></head><body><main>
<h1>TVO</h1><p>Colle ici l'adresse de l'add-on (elle finit souvent par <b>manifest.json</b>). Elle s'affichera aussitôt sur ta TV.</p>
${if (sent) "<div class=\"ok\">✓ Envoyé à la TV</div>" else ""}
<form method="post" class="card"><textarea name="v" placeholder="https://…/manifest.json" autocapitalize="off" autocorrect="off" spellcheck="false"></textarea>
<button type="submit">Envoyer à la TV</button></form></main></body></html>
""".trimIndent()

    companion object {
        fun localIp(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }?.hostAddress
        }.getOrNull()

        fun qr(text: String, size: Int = 360): Bitmap {
            val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
            val px = IntArray(size * size) { i -> if (m[i % size, i / size]) Color.BLACK else Color.WHITE }
            return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
        }
    }
}
