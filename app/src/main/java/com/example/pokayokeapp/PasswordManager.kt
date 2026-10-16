package com.example.pokayokeapp

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordManager {

    private const val PREF_NAME = "HistoryPasswordSettings"
    private const val KEY_SALT = "PASSWORD_SALT"
    private const val KEY_HASH = "PASSWORD_HASH"

    private const val SALT_SIZE = 16
    private const val HASH_BITS = 256
    private const val ITERATIONS = 120_000

    private fun preferences(context: Context) =
        context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )

    fun isPasswordSet(context: Context): Boolean {
        val prefs = preferences(context)

        return !prefs.getString(KEY_SALT, null).isNullOrEmpty() &&
                !prefs.getString(KEY_HASH, null).isNullOrEmpty()
    }

    fun setPassword(context: Context, password: String): Boolean {
        return try {
            val salt = ByteArray(SALT_SIZE)
            SecureRandom().nextBytes(salt)

            val hash = hashPassword(password, salt)

            preferences(context).edit()
                .putString(KEY_SALT, toHex(salt))
                .putString(KEY_HASH, toHex(hash))
                .commit()
        } catch (e: Exception) {
            false
        }
    }

    fun verifyPassword(context: Context, password: String): Boolean {
        return try {
            val prefs = preferences(context)

            val saltHex = prefs.getString(KEY_SALT, null)
                ?: return false

            val savedHashHex = prefs.getString(KEY_HASH, null)
                ?: return false

            val salt = fromHex(saltHex)
            val savedHash = fromHex(savedHashHex)
            val enteredHash = hashPassword(password, salt)

            MessageDigest.isEqual(savedHash, enteredHash)
        } catch (e: Exception) {
            false
        }
    }

    private fun hashPassword(
        password: String,
        salt: ByteArray
    ): ByteArray {

        val spec = PBEKeySpec(
            password.toCharArray(),
            salt,
            ITERATIONS,
            HASH_BITS
        )

        return try {
            SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun toHex(bytes: ByteArray): String {
        return bytes.joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }
    }

    private fun fromHex(value: String): ByteArray {
        require(value.length % 2 == 0)

        return ByteArray(value.length / 2) { index ->
            value.substring(index * 2, index * 2 + 2)
                .toInt(16)
                .toByte()
        }
    }
}