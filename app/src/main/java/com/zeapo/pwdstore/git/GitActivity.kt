/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.UserPreference
import com.zeapo.pwdstore.git.config.ConnectionMode
import com.zeapo.pwdstore.git.config.SshApiSessionFactory
import com.zeapo.pwdstore.utils.PasswordRepository
import java.io.File
import java.io.IOException
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.lib.Constants
import timber.log.Timber

open class GitActivity : AbstractGitActivity() {
    private var identityBuilder: SshApiSessionFactory.IdentityBuilder? = null
    private var identity: SshApiSessionFactory.ApiIdentity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val operationCode = intent.extras!!.getInt("Operation")

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        when (operationCode) {
            EDIT_GIT_CONFIG -> {
                setContentView(R.layout.activity_git_config)
                setTitle(R.string.title_activity_git_config)

                showGitConfig()
            }
            REQUEST_PULL -> syncRepository(REQUEST_PULL)

            REQUEST_PUSH -> syncRepository(REQUEST_PUSH)

            REQUEST_SYNC -> syncRepository(REQUEST_SYNC)
        }
    }

    public override fun onResume() {
        super.onResume()
    }

    override fun onDestroy() {
        // Do not leak the service connection
        if (identityBuilder != null) {
            identityBuilder!!.close()
            identityBuilder = null
        }
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.git_clone, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.user_pref -> try {
                val intent = Intent(this, UserPreference::class.java)
                startActivity(intent)
                true
            } catch (e: Exception) {
                println("Exception caught :(")
                e.printStackTrace()
                false
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showGitConfig() {
        // init the server information
        val username = findViewById<TextInputEditText>(R.id.git_user_name)
        val email = findViewById<TextInputEditText>(R.id.git_user_email)
        val abort = findViewById<MaterialButton>(R.id.git_abort_rebase)

        username.setText(settings.getString("git_config_user_name", ""))
        email.setText(settings.getString("git_config_user_email", ""))

        // git status
        val repo = PasswordRepository.getRepository(PasswordRepository.getRepositoryDirectory(this))
        if (repo != null) {
            val commitHash = findViewById<AppCompatTextView>(R.id.git_commit_hash)
            try {
                val objectId = repo.resolve(Constants.HEAD)
                val ref = repo.getRef("refs/heads/master")
                val head = if (ref.objectId.equals(objectId)) ref.name else "DETACHED"
                commitHash.text = String.format("%s (%s)", objectId.abbreviate(8).name(), head)

                // enable the abort button only if we're rebasing
                val isRebasing = repo.repositoryState.isRebasing
                abort.isEnabled = isRebasing
                abort.alpha = if (isRebasing) 1.0f else 0.5f
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun saveGitConfigs(): Boolean {
        // remember the settings
        val editor = settings.edit()

        val email = (findViewById<View>(R.id.git_user_email) as TextInputEditText).text!!.toString()
        editor.putString("git_config_user_email", email)
        editor.putString("git_config_user_name", (findViewById<View>(R.id.git_user_name) as TextInputEditText).text.toString())

        if (!email.matches(emailPattern.toRegex())) {
            MaterialAlertDialogBuilder(this)
                    .setMessage(getString(R.string.invalid_email_dialog_text))
                    .setPositiveButton(getString(R.string.dialog_oops), null)
                    .show()
            return false
        }

        editor.apply()
        return true
    }

    @Suppress("UNUSED_PARAMETER")
    fun applyGitConfigs(view: View) {
        if (!saveGitConfigs())
            return
        PasswordRepository.setUserName(settings.getString("git_config_user_name", null) ?: "")
        PasswordRepository.setUserEmail(settings.getString("git_config_user_email", null) ?: "")
        finish()
    }

    @Suppress("UNUSED_PARAMETER")
    fun abortRebase(view: View) {
        launchGitOperation(BREAK_OUT_OF_DETACHED)
    }

    @Suppress("UNUSED_PARAMETER")
    fun resetToRemote(view: View) {
        launchGitOperation(REQUEST_RESET)
    }

    /**
     * Clones the repository, the directory exists, deletes it
     */
    @Suppress("UNUSED_PARAMETER")
    fun cloneRepository(view: View) {
        if (PasswordRepository.getRepository(null) == null) {
            PasswordRepository.initialize(this)
        }
        val localDir = requireNotNull(PasswordRepository.getRepositoryDirectory(this))

        // Warn if non-empty folder unless it's a just-initialized store that has just a .git folder
        if (localDir.exists() && localDir.listFiles()!!.isNotEmpty() &&
                !(localDir.listFiles()!!.size == 1 && localDir.listFiles()!![0].name == ".git")) {
            MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.dialog_delete_title)
                    .setMessage(resources.getString(R.string.dialog_delete_msg) + " " + localDir.toString())
                    .setCancelable(false)
                    .setPositiveButton(R.string.dialog_delete
                    ) { dialog, _ ->
                        try {
                            FileUtils.deleteDirectory(localDir)
                            launchGitOperation(REQUEST_CLONE)
                        } catch (e: IOException) {
                            // TODO Handle the exception correctly if we are unable to delete the directory...
                            e.printStackTrace()
                            MaterialAlertDialogBuilder(this).setMessage(e.message).show()
                        }

                        dialog.cancel()
                    }
                    .setNegativeButton(R.string.dialog_do_not_delete
                    ) { dialog, _ -> dialog.cancel() }
                    .show()
        } else {
            try {
                // Silently delete & replace the lone .git folder if it exists
                if (localDir.exists() && localDir.listFiles()!!.size == 1 && localDir.listFiles()!![0].name == ".git") {
                    try {
                        FileUtils.deleteDirectory(localDir)
                    } catch (e: IOException) {
                        e.printStackTrace()
                        MaterialAlertDialogBuilder(this).setMessage(e.message).show()
                    }
                }
            } catch (e: Exception) {
                // This is what happens when jgit fails :(
                // TODO Handle the diffent cases of exceptions
                e.printStackTrace()
                MaterialAlertDialogBuilder(this).setMessage(e.message).show()
            }

            launchGitOperation(REQUEST_CLONE)
        }
    }

    /**
     * Syncs the local repository with the remote one (either pull or push)
     *
     * @param operation the operation to execute can be REQUEST_PULL or REQUEST_PUSH
     */
    private fun syncRepository(operation: Int) {
        if (serverUser.isEmpty() || serverUrl.isEmpty() || hostname.isEmpty())
            MaterialAlertDialogBuilder(this)
                    .setMessage(getString(R.string.set_information_dialog_text))
                    .setPositiveButton(getString(R.string.dialog_positive)) { _, _ ->
                        val intent = Intent(this, UserPreference::class.java)
                        startActivityForResult(intent, REQUEST_PULL)
                    }
                    .setNegativeButton(getString(R.string.dialog_negative)) { _, _ ->
                        // do nothing :(
                        setResult(RESULT_OK)
                        finish()
                    }
                    .show()
        else {
            // check that the remote origin is here, else add it
            PasswordRepository.addRemote("origin", hostname, false)
            launchGitOperation(operation)
        }
    }

    /**
     * Attempt to launch the requested GIT operation. Depending on the configured auth, it may not
     * be possible to launch the operation immediately. In that case, this function may launch an
     * intermediate activity instead, which will gather necessary information and post it back via
     * onActivityResult, which will then re-call this function. This may happen multiple times,
     * until either an error is encountered or the operation is successfully launched.
     *
     * @param operation The type of GIT operation to launch
     */
    private fun launchGitOperation(operation: Int) {
        val op: GitOperation
        val localDir = requireNotNull(PasswordRepository.getRepositoryDirectory(this))

        try {

            // Before launching the operation with OpenKeychain auth, we need to issue several requests
            // to the OpenKeychain API. IdentityBuild will take care of launching the relevant intents,
            // we just need to keep calling it until it returns a completed ApiIdentity.
            if (connectionMode == ConnectionMode.OpenKeychain && identity == null) {
                // Lazy initialization of the IdentityBuilder
                if (identityBuilder == null) {
                    identityBuilder = SshApiSessionFactory.IdentityBuilder(this)
                }

                // Try to get an ApiIdentity and bail if one is not ready yet. The builder will ensure
                // that onActivityResult is called with operation again, which will re-invoke us here
                identity = identityBuilder!!.tryBuild(operation)
                if (identity == null)
                    return
            }

            when (operation) {
                REQUEST_CLONE, GitOperation.GET_SSH_KEY_FROM_CLONE -> op = CloneOperation(localDir, this).setCommand(hostname)

                REQUEST_PULL -> op = PullOperation(localDir, this).setCommand()

                REQUEST_PUSH -> op = PushOperation(localDir, this).setCommand()

                REQUEST_SYNC -> op = SyncOperation(localDir, this).setCommands()

                BREAK_OUT_OF_DETACHED -> op = BreakOutOfDetached(localDir, this).setCommands()

                REQUEST_RESET -> op = ResetToRemoteOperation(localDir, this).setCommands()

                SshApiSessionFactory.POST_SIGNATURE -> return

                else -> {
                    Timber.tag(TAG).e("Operation not recognized : $operation")
                    setResult(RESULT_CANCELED)
                    finish()
                    return
                }
            }

            op.executeAfterAuthentication(connectionMode,
                    settings.getString("git_remote_username", "git")!!,
                    File("$filesDir/.ssh_key"),
                    identity)
        } catch (e: Exception) {
            e.printStackTrace()
            MaterialAlertDialogBuilder(this).setMessage(e.message).show()
        }
    }

    public override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        // In addition to the pre-operation-launch series of intents for OpenKeychain auth
        // that will pass through here and back to launchGitOperation, there is one
        // synchronous operation that happens /after/ the operation has been launched in the
        // background thread - the actual signing of the SSH challenge. We pass through the
        // completed signature to the ApiIdentity, which will be blocked in the other thread
        // waiting for it.
        if (requestCode == SshApiSessionFactory.POST_SIGNATURE && identity != null) {
            identity!!.postSignature(data)

            // If the signature failed (usually because it was cancelled), reset state
            if (data == null) {
                identity = null
                identityBuilder = null
            }
            return
        }

        if (resultCode == RESULT_CANCELED) {
            setResult(RESULT_CANCELED)
            finish()
        } else if (resultCode == RESULT_OK) {
            // If an operation has been re-queued via this mechanism, let the
            // IdentityBuilder attempt to extract some updated state from the intent before
            // trying to re-launch the operation.
            if (identityBuilder != null) {
                identityBuilder!!.consume(data)
            }
            launchGitOperation(requestCode)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    companion object {
        const val REQUEST_PULL = 101
        const val REQUEST_PUSH = 102
        const val REQUEST_CLONE = 103
        const val REQUEST_INIT = 104
        const val REQUEST_SYNC = 106

        @Suppress("Unused")
        const val REQUEST_CREATE = 107
        const val EDIT_GIT_CONFIG = 108
        const val BREAK_OUT_OF_DETACHED = 109
        const val REQUEST_RESET = 110
        private const val TAG = "GitAct"
        private const val emailPattern = "^[^@]+@[^@]+$"
    }
}
