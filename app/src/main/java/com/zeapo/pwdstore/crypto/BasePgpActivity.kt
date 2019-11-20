/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.crypto

import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.IntentSender
import android.content.SharedPreferences
import android.os.Bundle
import android.text.TextUtils
import android.text.format.DateUtils
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import com.zeapo.pwdstore.PasswordEntry
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.UserPreference
import kotlinx.android.synthetic.main.decrypt_layout.*
import me.msfjarvis.openpgpktx.OpenPgpError
import me.msfjarvis.openpgpktx.util.OpenPgpApi
import me.msfjarvis.openpgpktx.util.OpenPgpServiceConnection
import org.apache.commons.io.FilenameUtils
import org.openintents.openpgp.IOpenPgpService2

@Suppress("Registered")
open class BasePgpActivity : AppCompatActivity(), OpenPgpServiceConnection.OnBound {

    val clipboard by lazy { getSystemService<ClipboardManager>() }
    lateinit var settings: SharedPreferences

    var api: OpenPgpApi? = null
    var mServiceConnection: OpenPgpServiceConnection? = null
    var passwordEntry: PasswordEntry? = null

    var editName: String? = null
    var editPass: String? = null
    var editExtra: String? = null

    val repoPath: String by lazy { intent.getStringExtra("REPO_PATH") }
    val fullPath: String by lazy { intent.getStringExtra("FILE_PATH") }
    val name: String by lazy { getName(fullPath) }
    val lastChangedString: CharSequence by lazy {
        getLastChangedString(
            intent.getLongExtra(
                "LAST_CHANGED_TIMESTAMP",
                -1L
            )
        )
    }
    val relativeParentPath: String by lazy { PgpActivity.getParentPath(fullPath, repoPath) }

    val keyIDs: MutableSet<String> by lazy {
        settings.getStringSet("openpgp_key_ids_set", mutableSetOf()) ?: emptySet()
    }

    override fun onBound(service: IOpenPgpService2?) {
        initOpenPgpApi()
    }

    override fun onError(e: Exception?) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        settings = PreferenceManager.getDefaultSharedPreferences(this)

        // some persistence
        val providerPackageName = settings.getString("openpgp_provider_list", "")

        if (TextUtils.isEmpty(providerPackageName)) {
            showSnackbar(resources.getString(R.string.provider_toast_text), Snackbar.LENGTH_LONG)
            val intent = Intent(this, UserPreference::class.java)
            startActivityForResult(intent, OPEN_PGP_BOUND)
        } else {
            // bind to service
            mServiceConnection = OpenPgpServiceConnection(this, providerPackageName, this)
            mServiceConnection?.bindToService()
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (data == null) {
            setResult(RESULT_CANCELED, null)
            finish()
            return
        }
    }

    private fun initOpenPgpApi() {
        api = api ?: OpenPgpApi(this, mServiceConnection!!.service!!)
    }

    fun copyTextToClipboard(text: String?, @StringRes noticeTextRes: Int = 0, vararg formatArgs: Any = emptyArray()) {
        val clip = ClipData.newPlainText("pgp_handler_result_pm", text)
        clipboard?.setPrimaryClip(clip)
        if (noticeTextRes != 0) {
            showSnackbar(
                if (formatArgs.size > 1)
                    resources.getString(noticeTextRes, formatArgs)
                else
                    resources.getString(noticeTextRes)
            )
        }
    }

    /**
     * Handle the case where OpenKeychain returns that it needs to interact with the user
     *
     * @param result The intent returned by OpenKeychain
     * @param requestCode The code we'd like to use to identify the behaviour
     */
    fun handleUserInteractionRequest(result: Intent, requestCode: Int) {
        Log.i(TAG, "RESULT_CODE_USER_INTERACTION_REQUIRED")

        val pi: PendingIntent? = result.getParcelableExtra(OpenPgpApi.RESULT_INTENT)
        try {
            this.startIntentSenderFromChild(
                this, pi?.intentSender, requestCode,
                null, 0, 0, 0
            )
        } catch (e: IntentSender.SendIntentException) {
            Log.e(TAG, "SendIntentException", e)
        }
    }

    /**
     * Handle the error returned by OpenKeychain
     *
     * @param result The intent returned by OpenKeychain
     */
    fun handleError(result: Intent) {
        // TODO show what kind of error it is
        /* For example:
         * No suitable key found -> no key in OpenKeyChain
         *
         * Check in open-pgp-lib how their definitions and error code
         */
        val error: OpenPgpError? = result.getParcelableExtra(OpenPgpApi.RESULT_ERROR)
        if (error != null) {
            showSnackbar("Error from OpenKeyChain : " + error.message)
            Log.e(TAG, "onError getErrorId:" + error.message)
            Log.e(TAG, "onError getMessage:" + error.message)
        }
    }

    /**
     * Shows a simple toast message
     */
    fun showSnackbar(message: String, length: Int = Snackbar.LENGTH_SHORT) {
        runOnUiThread { Snackbar.make(findViewById(android.R.id.content), message, length).show() }
    }

    /**
     * Gets a relative string describing when this shape was last changed
     * (e.g. "one hour ago")
     */
    private fun getLastChangedString(timeStamp: Long): CharSequence {
        if (timeStamp < 0) {
            throw RuntimeException()
        }

        return DateUtils.getRelativeTimeSpanString(this, timeStamp, true)
    }

    inner class HoldToShowPasswordTransformation constructor(button: Button, private val onToggle: Runnable) :
        PasswordTransformationMethod(), View.OnTouchListener {
        private var shown = false

        init {
            button.setOnTouchListener(this)
        }

        override fun getTransformation(charSequence: CharSequence, view: View): CharSequence {
            return if (shown) charSequence else super.getTransformation("12345", view)
        }

        @Suppress("ClickableViewAccessibility")
        override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    shown = true
                    onToggle.run()
                }
                MotionEvent.ACTION_UP -> {
                    shown = false
                    onToggle.run()
                }
            }
            return false
        }
    }

    companion object {
        const val OPEN_PGP_BOUND = 101
        const val REQUEST_DECRYPT = 202
        const val REQUEST_KEY_ID = 203

        const val TAG = "BasePgpActivity"

        /**
         * Gets the relative path to the repository
         */
        fun getRelativePath(fullPath: String, repositoryPath: String): String =
            fullPath.replace(repositoryPath, "").replace("/+".toRegex(), "/")

        /**
         * Gets the Parent path, relative to the repository
         */
        fun getParentPath(fullPath: String, repositoryPath: String): String {
            val relativePath = getRelativePath(fullPath, repositoryPath)
            val index = relativePath.lastIndexOf("/")
            return "/${relativePath.substring(startIndex = 0, endIndex = index + 1)}/".replace("/+".toRegex(), "/")
        }

        /**
         * Gets the name of the password (excluding .gpg)
         */
        fun getName(fullPath: String): String {
            return FilenameUtils.getBaseName(fullPath)
        }

        /**
         * /path/to/store/social/facebook.gpg -> social/facebook
         */
        @JvmStatic
        fun getLongName(fullPath: String, repositoryPath: String, basename: String): String {
            var relativePath = getRelativePath(fullPath, repositoryPath)
            return if (relativePath.isNotEmpty() && relativePath != "/") {
                // remove preceding '/'
                relativePath = relativePath.substring(1)
                if (relativePath.endsWith('/')) {
                    relativePath + basename
                } else {
                    "$relativePath/$basename"
                }
            } else {
                basename
            }
        }
    }
}
