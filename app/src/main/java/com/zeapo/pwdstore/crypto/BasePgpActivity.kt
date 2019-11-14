/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.crypto

import android.app.PendingIntent
import android.content.ClipboardManager
import android.content.Intent
import android.content.IntentSender
import android.content.SharedPreferences
import android.os.Bundle
import android.text.TextUtils
import android.text.format.DateUtils
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.UserPreference
import me.msfjarvis.openpgpktx.OpenPgpError
import me.msfjarvis.openpgpktx.util.OpenPgpApi
import me.msfjarvis.openpgpktx.util.OpenPgpServiceConnection
import org.openintents.openpgp.IOpenPgpService2

@Suppress("Registered")
open class BasePgpActivity : AppCompatActivity(), OpenPgpServiceConnection.OnBound {

    val clipboard by lazy { getSystemService<ClipboardManager>() }
    lateinit var settings: SharedPreferences

    var api: OpenPgpApi? = null
    var mServiceConnection: OpenPgpServiceConnection? = null

    val repoPath: String by lazy { intent.getStringExtra("REPO_PATH") }

    val fullPath: String by lazy { intent.getStringExtra("FILE_PATH") }
    val name: String by lazy { PgpActivity.getName(fullPath) }
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

    companion object {
        const val OPEN_PGP_BOUND = 101
        const val REQUEST_DECRYPT = 202
        const val REQUEST_KEY_ID = 203

        const val TAG = "BasePgpActivity"
    }
}
