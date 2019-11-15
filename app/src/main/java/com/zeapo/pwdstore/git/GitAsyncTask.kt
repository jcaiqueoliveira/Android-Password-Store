/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git

import android.app.Activity
import android.app.ProgressDialog
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import com.zeapo.pwdstore.PasswordStore
import com.zeapo.pwdstore.R
import java.lang.ref.WeakReference
import org.eclipse.jgit.api.CommitCommand
import org.eclipse.jgit.api.GitCommand
import org.eclipse.jgit.api.PullCommand
import org.eclipse.jgit.api.PushCommand
import org.eclipse.jgit.api.RebaseResult
import org.eclipse.jgit.api.StatusCommand
import org.eclipse.jgit.transport.RemoteRefUpdate

class GitAsyncTask(
    activity: Activity,
    private val finishOnEnd: Boolean,
    private val refreshListOnEnd: Boolean,
    private val operation: GitOperation
) : AsyncTask<GitCommand<*>?, Int?, String>() {

    private val activity = WeakReference(activity).get()
    private var dialog = ProgressDialog(activity)

    override fun onPreExecute() {
        if (activity is AppCompatActivity) {
            dialog.setMessage(activity.getString(R.string.running_dialog_text))
            dialog.setCancelable(false)
            dialog.show()
        }
    }

    override fun doInBackground(vararg commands: GitCommand<*>?): String? {
        var nbChanges: Int? = null
        for (command in commands) {
            try {
                if (command is StatusCommand) {
                    // in case we have changes, we want to keep track of it
                    val status = command.call()
                    nbChanges = status.changed.size + status.missing.size
                } else if (command is CommitCommand) {
                    // the previous status will eventually be used to avoid a commit
                    if (nbChanges == null || nbChanges > 0) command.call()
                } else if (command is PullCommand) {
                    val rr = command.call().rebaseResult
                    if (rr.status === RebaseResult.Status.STOPPED) {
                        if (activity is AppCompatActivity) {
                            return activity.getString(R.string.git_pull_fail_error)
                        }
                    }
                } else if (command is PushCommand) {
                    for (result in command.call()) { // Code imported (modified) from Gerrit PushOp, license Apache v2
                        for (rru in result.remoteUpdates) {
                            if (activity is AppCompatActivity) {
                                when (rru.status) {
                                    RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD ->
                                        return activity.getString(R.string.git_push_nff_error)
                                    RemoteRefUpdate.Status.REJECTED_NODELETE,
                                    RemoteRefUpdate.Status.REJECTED_REMOTE_CHANGED,
                                    RemoteRefUpdate.Status.NON_EXISTING,
                                    RemoteRefUpdate.Status.NOT_ATTEMPTED ->
                                        return activity.getString(R.string.git_push_generic_error) + rru.status.name
                                    RemoteRefUpdate.Status.REJECTED_OTHER_REASON ->
                                        return if ("non-fast-forward" == rru.message) {
                                            activity.getString(R.string.git_push_other_error)
                                        } else {
                                            activity.getString(R.string.git_push_generic_error) + rru.message
                                        }
                                    else -> {
                                    }
                                }
                            }
                        }
                    }
                } else {
                    command?.call()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return e.message + "\nCaused by:\n" + e.cause
            }
        }
        return ""
    }

    override fun onPostExecute(result: String) {
        dialog.dismiss()
        if (result.isNotEmpty()) {
            operation.onError(result)
        } else {
            operation.onSuccess()
            if (finishOnEnd) {
                if (activity is AppCompatActivity) {
                    activity.setResult(Activity.RESULT_OK)
                    activity.finish()
                }
            }
            if (refreshListOnEnd) {
                try {
                    if (activity is PasswordStore) { activity.updateListAdapter() }
                } catch (e: ClassCastException) {
                    // oops, mistake
                }
            }
        }
    }
}
