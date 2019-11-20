/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.crypto

import android.content.ClipData
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.AsyncTask
import android.os.Bundle
import android.os.ConditionVariable
import android.os.Handler
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.PasswordEntry
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.utils.Otp
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Date
import kotlinx.android.synthetic.main.decrypt_layout.*
import kotlinx.android.synthetic.main.encrypt_layout.*
import me.msfjarvis.openpgpktx.util.OpenPgpApi
import org.apache.commons.io.FileUtils
import org.openintents.openpgp.IOpenPgpService2

class DecryptActivity : BasePgpActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.decrypt_layout)
        findViewById<AppCompatTextView>(R.id.crypto_password_category_decrypt)?.text = relativeParentPath
        findViewById<AppCompatTextView>(R.id.crypto_password_file)?.text = name

        findViewById<AppCompatTextView>(R.id.crypto_password_last_changed)?.text = try {
            resources.getString(R.string.last_changed, lastChangedString)
        } catch (_: Exception) {
            showSnackbar(resources.getString(R.string.get_last_changed_failed))
            ""
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.pgp_handler, menu)
        return true
    }

    override fun onBound(service: IOpenPgpService2?) {
        super.onBound(service)
        decryptAndVerify()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // try again after user interaction
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_DECRYPT -> decryptAndVerify(data)
                else -> {
                    setResult(RESULT_OK)
                    finish()
                }
            }
        } else if (resultCode == RESULT_CANCELED) {
            setResult(RESULT_CANCELED, data)
            finish()
        }
    }

    private fun setTimer() {
        // make sure to cancel any running tasks as soon as possible
        // if the previous task is still running, do not ask it to clear the password
        delayTask?.cancelAndSignal(true)

        // launch a new one
        delayTask = DelayShow(this)
        delayTask?.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    private fun decryptAndVerify(receivedIntent: Intent? = null) {
        val data = receivedIntent ?: Intent()
        data.action = OpenPgpApi.ACTION_DECRYPT_VERIFY

        val iStream = FileUtils.openInputStream(File(fullPath))
        val oStream = ByteArrayOutputStream()

        api?.executeApiAsync(data, iStream, oStream, object : OpenPgpApi.IOpenPgpCallback {
            override fun onReturn(result: Intent?) {
                when (result?.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
                    OpenPgpApi.RESULT_CODE_SUCCESS -> {
                        try {
                            val showPassword = settings.getBoolean("show_password", true)
                            val showExtraContent = settings.getBoolean("show_extra_content", true)

                            crypto_container_decrypt.visibility = View.VISIBLE

                            val monoTypeface = Typeface.createFromAsset(assets, "fonts/sourcecodepro.ttf")
                            val entry = PasswordEntry(oStream)

                            passwordEntry = entry

                            if (intent.getStringExtra("OPERATION") == "EDIT") {
                                editPassword()
                                return
                            }

                            if (entry.password.isEmpty()) {
                                crypto_password_show.visibility = View.GONE
                                crypto_password_show_label.visibility = View.GONE
                            } else {
                                crypto_password_show.visibility = View.VISIBLE
                                crypto_password_show_label.visibility = View.VISIBLE
                                crypto_password_show.typeface = monoTypeface
                                crypto_password_show.text = entry.password
                            }
                            crypto_password_show.typeface = monoTypeface
                            crypto_password_show.text = entry.password

                            crypto_password_toggle_show.visibility = if (showPassword) View.GONE else View.VISIBLE
                            crypto_password_show.transformationMethod = if (showPassword) {
                                null
                            } else {
                                HoldToShowPasswordTransformation(
                                    crypto_password_toggle_show,
                                    Runnable { crypto_password_show.text = entry.password }
                                )
                            }

                            if (entry.hasExtraContent()) {
                                crypto_extra_show.typeface = monoTypeface
                                crypto_extra_show.text = entry.extraContent

                                if (showExtraContent) {
                                    crypto_extra_show_layout.visibility = View.VISIBLE
                                    crypto_extra_toggle_show.visibility = View.GONE
                                    crypto_extra_show.transformationMethod = null
                                } else {
                                    crypto_extra_show_layout.visibility = View.GONE
                                    crypto_extra_toggle_show.visibility = View.VISIBLE
                                    crypto_extra_toggle_show.setOnCheckedChangeListener { _, _ ->
                                        crypto_extra_show.text = entry.extraContent
                                    }

                                    crypto_extra_show.transformationMethod = object : PasswordTransformationMethod() {
                                        override fun getTransformation(source: CharSequence, view: View): CharSequence {
                                            return if (crypto_extra_toggle_show.isChecked) source else super.getTransformation(source, view)
                                        }
                                    }
                                }

                                if (entry.hasUsername()) {
                                    crypto_username_show.visibility = View.VISIBLE
                                    crypto_username_show_label.visibility = View.VISIBLE
                                    crypto_copy_username.visibility = View.VISIBLE

                                    crypto_copy_username.setOnClickListener { copyTextToClipboard(entry.username) }
                                    crypto_username_show.typeface = monoTypeface
                                    crypto_username_show.text = entry.username
                                } else {
                                    crypto_username_show.visibility = View.GONE
                                    crypto_username_show_label.visibility = View.GONE
                                    crypto_copy_username.visibility = View.GONE
                                }
                            }

                            if (entry.hasTotp() || entry.hasHotp()) {
                                crypto_extra_show_layout.visibility = View.VISIBLE
                                crypto_extra_show.typeface = monoTypeface
                                crypto_extra_show.text = entry.extraContent

                                crypto_otp_show.visibility = View.VISIBLE
                                crypto_otp_show_label.visibility = View.VISIBLE
                                crypto_copy_otp.visibility = View.VISIBLE

                                if (entry.hasTotp()) {
                                    crypto_copy_otp.setOnClickListener {
                                        copyOtpToClipBoard(
                                            Otp.calculateCode(
                                                entry.totpSecret,
                                                Date().time / (1000 * entry.totpPeriod),
                                                entry.totpAlgorithm,
                                                entry.digits)
                                        )
                                    }
                                    crypto_otp_show.text =
                                        Otp.calculateCode(
                                            entry.totpSecret,
                                            Date().time / (1000 * entry.totpPeriod),
                                            entry.totpAlgorithm,
                                            entry.digits)
                                } else {
                                    // we only want to calculate and show HOTP if the user requests it
                                    crypto_copy_otp.setOnClickListener {
                                        if (settings.getBoolean("hotp_remember_check", false)) {
                                            if (settings.getBoolean("hotp_remember_choice", false)) {
                                                calculateAndCommitHotp(entry)
                                            } else {
                                                calculateHotp(entry)
                                            }
                                        } else {
                                            // show a dialog asking permission to update the HOTP counter in the entry
                                            val checkInflater = LayoutInflater.from(this@PgpActivity)
                                            val checkLayout = checkInflater.inflate(R.layout.otp_confirm_layout, null)
                                            val rememberCheck: CheckBox =
                                                checkLayout.findViewById(R.id.hotp_remember_checkbox)
                                            val dialogBuilder = MaterialAlertDialogBuilder(this@PgpActivity)
                                            dialogBuilder.setView(checkLayout)
                                            dialogBuilder.setMessage(R.string.dialog_update_body)
                                                .setCancelable(false)
                                                .setPositiveButton(R.string.dialog_update_positive) { _, _ ->
                                                    run {
                                                        calculateAndCommitHotp(entry)
                                                        if (rememberCheck.isChecked) {
                                                            val editor = settings.edit()
                                                            editor.putBoolean("hotp_remember_check", true)
                                                            editor.putBoolean("hotp_remember_choice", true)
                                                            editor.apply()
                                                        }
                                                    }
                                                }
                                                .setNegativeButton(R.string.dialog_update_negative) { _, _ ->
                                                    run {
                                                        calculateHotp(entry)
                                                        val editor = settings.edit()
                                                        editor.putBoolean("hotp_remember_check", true)
                                                        editor.putBoolean("hotp_remember_choice", false)
                                                        editor.apply()
                                                    }
                                                }
                                            val updateDialog = dialogBuilder.create()
                                            updateDialog.setTitle(R.string.dialog_update_title)
                                            updateDialog.show()
                                        }
                                    }
                                    crypto_otp_show.setText(R.string.hotp_pending)
                                }
                                crypto_otp_show.typeface = monoTypeface
                            } else {
                                crypto_otp_show.visibility = View.GONE
                                crypto_otp_show_label.visibility = View.GONE
                                crypto_copy_otp.visibility = View.GONE
                            }

                            if (settings.getBoolean("copy_on_decrypt", true)) {
                                copyPasswordToClipBoard()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "An Exception occurred", e)
                        }
                    }
                    OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED -> handleUserInteractionRequest(result, REQUEST_DECRYPT)
                    OpenPgpApi.RESULT_CODE_ERROR -> handleError(result)
                }
            }
        })
    }

    fun copyPasswordToClipBoard() {
        var pass = passwordEntry?.password

        if (findViewById<TextView>(R.id.crypto_password_show) == null) {
            if (editPass == null) {
                return
            } else {
                pass = editPass
            }
        }

        var clearAfter = 45
        try {
            clearAfter = Integer.parseInt(settings.getString("general_show_time", "45") as String)
        } catch (e: NumberFormatException) {
            // ignore and keep default
        }

        if (settings.getBoolean("clear_after_copy", true) && clearAfter != 0) {
            setTimer()
            copyTextToClipboard(pass, R.string.clipboard_password_toast_text, clearAfter)
        } else {
            showSnackbar(resources.getString(R.string.clipboard_password_no_clear_toast_text))
        }
    }

    fun calculateHotp(entry: PasswordEntry) {
        copyTextToClipboard(Otp.calculateCode(entry.hotpSecret, entry.hotpCounter!! + 1, "sha1", entry.digits), R.string.clipboard_otp_toast_text)
        crypto_otp_show.text = Otp.calculateCode(entry.hotpSecret, entry.hotpCounter + 1, "sha1", entry.digits)
        crypto_extra_show.text = entry.extraContent
    }

    fun calculateAndCommitHotp(entry: PasswordEntry) {
        calculateHotp(entry)
        entry.incrementHotp()
        // we must set the result before encrypt() is called, since in
        // some cases it is called during the finish() sequence
        val returnIntent = Intent()
        returnIntent.putExtra("NAME", name.trim())
        returnIntent.putExtra("OPERATION", "INCREMENT")
        returnIntent.putExtra("needCommit", true)
        setResult(RESULT_OK, returnIntent)
    }

    @Suppress("StaticFieldLeak")
    inner class DelayShow(val activity: DecryptActivity) : AsyncTask<Void, Int, Boolean>() {
        private val pb: ProgressBar? by lazy { pbLoading }
        private var skip = false
        private var cancelNotify = ConditionVariable()

        private var showTime: Int = 0

        // Custom cancellation that can be triggered from another thread.
        //
        // This signals the DelayShow task to stop and avoids it having
        // to poll the AsyncTask.isCancelled() excessively. If skipClearing
        // is true, the cancelled task won't clear the clipboard?.
        fun cancelAndSignal(skipClearing: Boolean) {
            skip = skipClearing
            cancelNotify.open()
        }

        val settings: SharedPreferences by lazy {
            PreferenceManager.getDefaultSharedPreferences(activity)
        }

        override fun onPreExecute() {
            showTime = try {
                Integer.parseInt(settings.getString("general_show_time", "45") as String)
            } catch (e: NumberFormatException) {
                45
            }

            val container = findViewById<ConstraintLayout>(R.id.crypto_container_decrypt)
            container?.visibility = View.VISIBLE

            val extraText = findViewById<TextView>(R.id.crypto_extra_show)

            if (extraText?.text?.isNotEmpty() == true)
                findViewById<View>(R.id.crypto_extra_show_layout)?.visibility = View.VISIBLE

            if (showTime == 0) {
                // treat 0 as forever, and the user must exit and/or clear clipboard on their own
                cancel(true)
            } else {
                this.pb?.max = showTime
            }
        }

        override fun doInBackground(vararg params: Void): Boolean? {
            var current = 0
            while (current < showTime) {

                // Block for 1s or until cancel is signalled
                if (cancelNotify.block(1000)) {
                    return true
                }

                current++
                publishProgress(current)
            }
            return true
        }

        override fun onPostExecute(b: Boolean?) {
            if (skip) return
            checkAndIncrementHotp()

            // No need to validate clear_after_copy. It was validated in copyPasswordToClipBoard()
            Log.d("DELAY_SHOW", "Clearing the clipboard")
            val clip = ClipData.newPlainText("pgp_handler_result_pm", "")
            clipboard?.setPrimaryClip(clip)
            if (settings.getBoolean("clear_clipboard_20x", false)) {
                val handler = Handler()
                for (i in 0..19) {
                    val count = i.toString()
                    handler.postDelayed(
                        { clipboard?.setPrimaryClip(ClipData.newPlainText(count, count)) },
                        (i * 500).toLong()
                    )
                }
            }

            if (crypto_password_show != null) {
                // clear password; if decrypt changed to encrypt layout via edit button, no need
                if (passwordEntry?.hotpIsIncremented() == false) {
                    setResult(RESULT_CANCELED)
                }
                passwordEntry = null
                crypto_password_show.text = ""
                crypto_extra_show.text = ""
                crypto_extra_show_layout.visibility = View.INVISIBLE
                crypto_container_decrypt.visibility = View.INVISIBLE
                finish()
            }
        }

        override fun onProgressUpdate(vararg values: Int?) {
            this.pb?.progress = values[0] ?: 0
        }
    }

    companion object {
        var delayTask: DelayShow? = null
    }
}
