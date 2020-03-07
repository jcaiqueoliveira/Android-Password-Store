/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.crypto

import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.text.format.DateUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import com.zeapo.pwdstore.PasswordEntry
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.UserPreference
import com.zeapo.pwdstore.ui.dialogs.PasswordGeneratorDialogFragment
import com.zeapo.pwdstore.ui.dialogs.XkPasswordGeneratorDialogFragment
import kotlinx.android.synthetic.main.encrypt_layout.crypto_extra_edit
import kotlinx.android.synthetic.main.encrypt_layout.crypto_password_category
import kotlinx.android.synthetic.main.encrypt_layout.crypto_password_edit
import kotlinx.android.synthetic.main.encrypt_layout.crypto_password_file_edit
import kotlinx.android.synthetic.main.encrypt_layout.generate_password
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import me.msfjarvis.openpgpktx.util.OpenPgpApi
import me.msfjarvis.openpgpktx.util.OpenPgpApi.Companion.RESULT_CODE
import me.msfjarvis.openpgpktx.util.OpenPgpApi.Companion.RESULT_CODE_ERROR
import me.msfjarvis.openpgpktx.util.OpenPgpApi.Companion.RESULT_CODE_SUCCESS
import me.msfjarvis.openpgpktx.util.OpenPgpApi.Companion.RESULT_ERROR
import me.msfjarvis.openpgpktx.util.OpenPgpApi.Companion.RESULT_INTENT
import me.msfjarvis.openpgpktx.util.OpenPgpServiceConnection
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.openintents.openpgp.IOpenPgpService2
import org.openintents.openpgp.OpenPgpError
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset

class EncryptActivity : AppCompatActivity(), OpenPgpServiceConnection.OnBound {
    private val clipboard: ClipboardManager by lazy {
        getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    private var passwordEntry: PasswordEntry? = null
    private var api: OpenPgpApi? = null

    private var editName: String? = null
    private var editPass: String? = null
    private var editExtra: String? = null

    private val repoPath: String by lazy { intent.getStringExtra("REPO_PATH") }
    private val fullPath: String by lazy { intent.getStringExtra("FILE_PATH") }
    private val name: String by lazy { getName(fullPath) }
    private val lastChangedString: CharSequence by lazy {
        getLastChangedString(
                intent.getLongExtra(
                        "LAST_CHANGED_TIMESTAMP",
                        -1L
                )
        )
    }
    private val relativeParentPath: String by lazy { getParentPath(fullPath, repoPath) }

    val settings: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }
    private val keyIDs get() = _keyIDs
    private var _keyIDs = emptySet<String>()
    private var mServiceConnection: OpenPgpServiceConnection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        Timber.tag(TAG)

        // some persistence
        _keyIDs = settings.getStringSet("openpgp_key_ids_set", null) ?: emptySet()
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

        setContentView(R.layout.encrypt_layout)

        generate_password?.setOnClickListener {
            when (settings.getString("pref_key_pwgen_type", KEY_PWGEN_TYPE_CLASSIC)) {
                KEY_PWGEN_TYPE_CLASSIC -> PasswordGeneratorDialogFragment()
                        .show(supportFragmentManager, "generator")
                KEY_PWGEN_TYPE_XKPASSWD -> XkPasswordGeneratorDialogFragment()
                        .show(supportFragmentManager, "xkpwgenerator")
            }
        }

        title = getString(R.string.new_password_title)
        crypto_password_category.text = getRelativePath(fullPath, repoPath)
    }

    override fun onDestroy() {
        super.onDestroy()
        mServiceConnection?.unbindFromService()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        // Do not use the value `operation` in this case as it is not valid when editing
        val menuId = when (intent.getStringExtra("OPERATION")) {
            "ENCRYPT", "EDIT" -> R.menu.pgp_handler_new_password
            "DECRYPT" -> R.menu.pgp_handler
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
     * Shows a simple toast message
     */
    private fun showSnackbar(message: String, length: Int = Snackbar.LENGTH_SHORT) {
        runOnUiThread { Snackbar.make(findViewById(android.R.id.content), message, length).show() }
    }

    /**
     * Handle the case where OpenKeychain returns that it needs to interact with the user
     *
     * @param result The intent returned by OpenKeychain
     * @param requestCode The code we'd like to use to identify the behaviour
     */
    private fun handleUserInteractionRequest(result: Intent, requestCode: Int) {
        Timber.i("RESULT_CODE_USER_INTERACTION_REQUIRED")

        val pi: PendingIntent? = result.getParcelableExtra(RESULT_INTENT)
        try {
            this@EncryptActivity.startIntentSenderFromChild(
                    this@EncryptActivity, pi?.intentSender, requestCode,
                    null, 0, 0, 0
            )
        } catch (e: IntentSender.SendIntentException) {
            Timber.e(e, "SendIntentException")
        }
    }

    /**
     * Handle the error returned by OpenKeychain
     *
     * @param result The intent returned by OpenKeychain
     */
    private fun handleError(result: Intent) {
        // TODO show what kind of error it is
        /* For example:
         * No suitable key found -> no key in OpenKeyChain
         *
         * Check in open-pgp-lib how their definitions and error code
         */
        val error: OpenPgpError? = result.getParcelableExtra(RESULT_ERROR)
        if (error != null) {
            showSnackbar("Error from OpenKeyChain : " + error.message)
            Timber.e("onError getErrorId: ${error.errorId}")
            Timber.e("onError getMessage: ${error.message}")
        }
    }

    private fun initOpenPgpApi() {
        api = api ?: OpenPgpApi(this, mServiceConnection!!.service!!)
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

        lifecycleScope.launch(IO) {
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
                                Timber.e(e, "An Exception occurred")
                            }
                        }
                        RESULT_CODE_ERROR -> handleError(result)
                    }
                }
            })
        }
    }

    /**
     * Opens EncryptActivity with the information for this file to be edited
     */
    private fun editPassword() {
        setContentView(R.layout.encrypt_layout)
        generate_password?.setOnClickListener {
            when (settings.getString("pref_key_pwgen_type", KEY_PWGEN_TYPE_CLASSIC)) {
                KEY_PWGEN_TYPE_CLASSIC -> PasswordGeneratorDialogFragment()
                        .show(supportFragmentManager, "generator")
                KEY_PWGEN_TYPE_XKPASSWD -> XkPasswordGeneratorDialogFragment()
                        .show(supportFragmentManager, "xkpwgenerator")
            }
        }

        title = getString(R.string.edit_password_title)

        val monoTypeface = Typeface.createFromAsset(assets, "fonts/sourcecodepro.ttf")
        crypto_password_edit.setText(passwordEntry?.password)
        crypto_password_edit.typeface = monoTypeface
        crypto_extra_edit.setText(passwordEntry?.extraContent)
        crypto_extra_edit.typeface = monoTypeface

        crypto_password_category.text = relativeParentPath
        crypto_password_file_edit.setText(name)
        crypto_password_file_edit.isEnabled = false

        val data = Intent(this, EncryptActivity::class.java)
        data.putExtra("OPERATION", "EDIT")
        data.putExtra("fromDecrypt", true)
        intent = data
        invalidateOptionsMenu()
    }

    override fun onError(e: Exception) {}

    /**
     * The action to take when the PGP service is bound
     */
    override fun onBound(service: IOpenPgpService2) {
        initOpenPgpApi()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (data == null) {
            setResult(RESULT_CANCELED, null)
            finish()
            return
        }

        // try again after user interaction
        if (resultCode == RESULT_OK) {
            setResult(RESULT_OK)
            finish()
        } else if (resultCode == RESULT_CANCELED) {
            setResult(RESULT_CANCELED, data)
            finish()
        }
    }

    private fun copyPasswordToClipBoard() {
        var pass = passwordEntry?.password

        if (findViewById<TextView>(R.id.crypto_password_show) == null) {
            if (editPass == null) {
                return
            } else {
                pass = editPass
            }
        }

        val clip = ClipData.newPlainText("pgp_handler_result_pm", pass)
        clipboard.primaryClip = clip

        var clearAfter = 45
        try {
            clearAfter = Integer.parseInt(settings.getString("general_show_time", "45") as String)
        } catch (e: NumberFormatException) {
            // ignore and keep default
        }

        if (settings.getBoolean("clear_after_copy", true) && clearAfter != 0) {
            showSnackbar(this.resources.getString(R.string.clipboard_password_toast_text, clearAfter))
        } else {
            showSnackbar(this.resources.getString(R.string.clipboard_password_no_clear_toast_text))
        }
    }

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

        const val TAG = "EncryptActivity"

        const val KEY_PWGEN_TYPE_CLASSIC = "classic"
        const val KEY_PWGEN_TYPE_XKPASSWD = "xkpasswd"

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
