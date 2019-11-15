/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.utils

import android.util.Log
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.util.Arrays
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.and
import org.apache.commons.codec.binary.Base32

object OTP {
    private val BASE_32 = Base32()
    fun calculateCode(secret: String?, counter: Long, algorithm: String, digits: String): String? {
        val steam = arrayOf(
                "2", "3", "4", "5", "6", "7", "8", "9", "B", "C", "D", "F", "G", "H", "J", "K", "M",
                "N", "P", "Q", "R", "T", "V", "W", "X", "Y")
        val algo = "Hmac" + algorithm.toUpperCase(Locale.getDefault())
        val signingKey = SecretKeySpec(BASE_32.decode(secret), algo)
        val mac: Mac
        try {
            mac = Mac.getInstance(algo)
            mac.init(signingKey)
        } catch (e: NoSuchAlgorithmException) {
            Log.e("TOTP", "$algo unavailable - should never happen", e)
            return null
        } catch (e: InvalidKeyException) {
            Log.e("TOTP", "Key is malformed", e)
            return null
        }
        return if (digits == "s") {
            val output = StringBuilder()
            var bigInt: Int = getBigIntCode(mac, counter).toInt()
            for (i in 0..4) {
                output.append(steam[bigInt % 26])
                bigInt /= 26
            }
            output.toString()
        } else {
            val strCode = getBigIntCode(mac, counter).toString()
            strCode.substring(strCode.length - digits.toInt())
        }
    }

    private fun getBigIntCode(mac: Mac, counter: Long): BigInteger {
        val digest = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array())
        val offset: Int = (digest.last() and 0xf).toInt()
        val code = Arrays.copyOfRange(digest, offset, offset + 4)
        code[0] = (code.first() and 0x7f)
        return BigInteger(code)
    }
}
