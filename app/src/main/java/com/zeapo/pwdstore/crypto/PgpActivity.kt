/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.crypto

import android.content.Intent
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.zeapo.pwdstore.R
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset
import kotlinx.android.synthetic.main.encrypt_layout.*
import me.msfjarvis.openpgpktx.util.OpenPgpApi
import me.msfjarvis.openpgpktx.util.OpenPgpApi.Companion.RESULT_CODE
import me.msfjarvis.openpgpktx.util.OpenPgpApi.Companion.RESULT_CODE_ERROR
import me.msfjarvis.openpgpktx.util.OpenPgpApi.Companion.RESULT_CODE_SUCCESS
import org.apache.commons.io.FileUtils

class PgpActivity : BasePgpActivity() {
    override fun onDestroy() {
        checkAndIncrementHotp()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        // Do not use the value `operation` in this case as it is not valid when editing
        val menuId = when (intent.getStringExtra("OPERATION")) {
            "ENCRYPT", "EDIT" -> R.menu.pgp_handler_new_password
            else -> R.menu.pgp_handler
        }

        menuInflater.inflate(menuId, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                if (passwordEntry?.hotpIsIncremented() == false) {
                    setResult(RESULT_CANCELED)
                }
                finish()
            }
            R.id.copy_password -> copyPasswordToClipBoard()
            R.id.share_password_as_plaintext -> shareAsPlaintext()
            R.id.edit_password -> editPassword()
            R.id.crypto_confirm_add -> encrypt()
            R.id.crypto_confirm_add_and_copy -> encrypt(true)
            R.id.crypto_cancel_add -> {
                if (passwordEntry?.hotpIsIncremented() == false) {
                    setResult(RESULT_CANCELED)
                }
                finish()
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    /**
     * Encrypts the password and the extra content
     */
    private fun encrypt(copy: Boolean = false) {
        // if HOTP was incremented, we leave fields as is; they have already been set
        if (intent.getStringExtra("OPERATION") != "INCREMENT") {
            editName = crypto_password_file_edit.text.toString().trim()
            editPass = crypto_password_edit.text.toString()
            editExtra = crypto_extra_edit.text.toString()
        }

        if (editName?.isEmpty() == true) {
            showSnackbar(resources.getString(R.string.file_toast_text))
            return
        }

        if (editPass?.isEmpty() == true && editExtra?.isEmpty() == true) {
            showSnackbar(resources.getString(R.string.empty_toast_text))
            return
        }

        if (copy) {
            copyPasswordToClipBoard()
        }

        val data = Intent()
        data.action = OpenPgpApi.ACTION_ENCRYPT

        // EXTRA_KEY_IDS requires long[]
        val longKeys = keyIDs.map { it.toLong() }
        data.putExtra(OpenPgpApi.EXTRA_KEY_IDS, longKeys.toLongArray())
        data.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true)

        // TODO Check if we could use PasswordEntry to generate the file
        val iStream = ByteArrayInputStream("$editPass\n$editExtra".toByteArray(Charset.forName("UTF-8")))
        val oStream = ByteArrayOutputStream()

        val path = if (intent.getBooleanExtra("fromDecrypt", false)) fullPath else "$fullPath/$editName.gpg"

        api?.executeApiAsync(data, iStream, oStream, object : OpenPgpApi.IOpenPgpCallback {
            override fun onReturn(result: Intent?) {
                when (result?.getIntExtra(RESULT_CODE, RESULT_CODE_ERROR)) {
                    RESULT_CODE_SUCCESS -> {
                        try {
                            // TODO This might fail, we should check that the write is successful
                            val outputStream = FileUtils.openOutputStream(File(path))
                            outputStream.write(oStream.toByteArray())
                            outputStream.close()

                            val returnIntent = Intent()
                            returnIntent.putExtra("CREATED_FILE", path)
                            returnIntent.putExtra("NAME", editName)
                            returnIntent.putExtra("LONG_NAME", getLongName(fullPath, repoPath, editName!!))

                            // if coming from decrypt screen->edit button
                            if (intent.getBooleanExtra("fromDecrypt", false)) {
                                returnIntent.putExtra("OPERATION", "EDIT")
                                returnIntent.putExtra("needCommit", true)
                            }
                            setResult(RESULT_OK, returnIntent)
                            finish()
                        } catch (e: Exception) {
                            Log.e(TAG, "An Exception occurred", e)
                        }
                    }
                    RESULT_CODE_ERROR -> handleError(result)
                }
            }
        })
    }

    override fun onError(e: Exception?) {}

    private fun shareAsPlaintext() {
        if (findViewById<View>(R.id.share_password_as_plaintext) == null)
            return

        val sendIntent = Intent()
        sendIntent.action = Intent.ACTION_SEND
        sendIntent.putExtra(Intent.EXTRA_TEXT, passwordEntry?.password)
        sendIntent.type = "text/plain"
        startActivity(
                Intent.createChooser(
                        sendIntent,
                        resources.getText(R.string.send_plaintext_password_to)
                )
        ) // Always show a picker to give the user a chance to cancel
    }
}
