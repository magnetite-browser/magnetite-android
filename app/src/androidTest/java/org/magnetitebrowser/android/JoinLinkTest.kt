package org.magnetitebrowser.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.magnetitebrowser.android.model.JoinLink
import org.magnetitebrowser.android.ui.qrText

/** The link in the QR code a computer shows beside its sync code. */
@RunWith(AndroidJUnit4::class)
class JoinLinkTest {
    @Test
    fun readsServerAndWords() {
        val link = JoinLink.parse("magnetite://sync?server=http%3A%2F%2Flocalhost%3A8787%2Fa%20b%2Bc&words=violin%20area%20display")
        assertEquals(JoinLink("http://localhost:8787/a b+c", "violin area display"), link)
    }

    /** A camera frame's rows are longer than the picture is wide. */
    @Test
    fun aPictureOfTheCodeIsRead() {
        val text = "magnetite://sync?server=https%3A%2F%2Fsync.magnetitebrowser.org&words=" +
            (List(23) { "abandon" } + "art").joinToString("%20")
        val side = 600
        val stride = side + 40
        val code = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, side, side)
        val brightness = ByteArray(stride * side) { i ->
            val x = i % stride
            if (x < side && code[x, i / stride]) 0 else 255.toByte()
        }
        assertEquals(text, qrText(brightness, stride, side, side))
        assertNull(qrText(ByteArray(stride * side), stride, side, side))
    }

    @Test
    fun otherTextIsNoLink() {
        assertNull(JoinLink.parse("https://example.org/?server=a&words=b"))
        assertNull(JoinLink.parse("magnetite://sync?server=a"))
        assertNull(JoinLink.parse("mailto:someone"))
        assertNull(JoinLink.parse("hello"))
    }
}
