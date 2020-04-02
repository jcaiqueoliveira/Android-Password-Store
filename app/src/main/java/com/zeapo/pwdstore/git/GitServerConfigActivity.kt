package com.zeapo.pwdstore.git

import android.os.Bundle
import androidx.core.content.edit
import androidx.core.widget.doOnTextChanged
import com.github.ajalt.timberkt.Timber
import com.github.ajalt.timberkt.d
import com.google.android.material.snackbar.Snackbar
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.databinding.ActivityGitCloneBinding
import com.zeapo.pwdstore.git.config.ConnectionMode
import com.zeapo.pwdstore.git.config.Protocol

/**
 * Activity that encompasses both the initial clone as well as editing the server config for future
 * changes.
 */
class GitServerConfigActivity : AbstractGitActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityGitCloneBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setTitle(R.string.title_activity_git_clone)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        when (protocol) {
            Protocol.Ssh -> binding.cloneProtocolSsh.isChecked = true
            Protocol.Https -> binding.cloneProtocolHttps.isChecked = true
        }

        when (connectionMode) {
            ConnectionMode.Username -> binding.connectionModeUsername.isChecked = true
            ConnectionMode.OpenKeychain -> binding.connectionModeOpenkeychain.isChecked = true
            ConnectionMode.Ssh -> binding.connectionModeSsh.isChecked = true
        }

        binding.cloneProtocolGroup.addOnButtonCheckedListener { _, checkedId, _ ->
            protocol = when (checkedId) {
                R.id.clone_protocol_https -> Protocol.Https
                R.id.clone_protocol_ssh -> Protocol.Ssh
                else -> protocol
            }
        }

        binding.connectionModeGroup.addOnButtonCheckedListener {_ , checkedId, _ ->
            connectionMode = when(checkedId) {
                R.id.connection_mode_ssh -> ConnectionMode.Ssh
                R.id.connection_mode_openkeychain -> ConnectionMode.OpenKeychain
                R.id.connection_mode_username -> ConnectionMode.Username
                else -> connectionMode
            }
        }

        binding.serverUrl.apply {
            setText(serverUrl)
            doOnTextChanged { text, _, _, _ ->
                serverUrl = text.toString()
            }
        }

        binding.serverPort.apply {
            setText(serverPort)
            doOnTextChanged { text, _, _, _ ->
                serverPort = text.toString()
            }
        }

        binding.serverUser.apply {
            setText(serverUser)
            doOnTextChanged { text, _, _, _ ->
                serverUser = text.toString()
            }
        }

        binding.serverPath.apply {
            setText(serverPath)
            doOnTextChanged { text, _, _, _ ->
                serverPath = text.toString()
            }
        }

        binding.saveButton.setOnClickListener {
            // Heavily simplified to drop all error checking.
            hostname = when(protocol) {
                Protocol.Ssh -> {
                    "$serverUser@${serverUrl.trim { it <= ' '}}:$serverPort/$serverPath"
                }
                Protocol.Https -> {
                    "${serverUrl.trim { it <= ' '}}/$serverPort/$serverPath"
                }
            }
            settings.edit(true) {
                putString("git_remote_protocol", protocol.toString())
                putString("git_remote_auth", connectionMode.toString())
                Timber.tag("GitServerConfigActivity").d { "hostname=$hostname" }
                putString("git_remote_location", hostname)
                putString("git_remote_server", serverUrl)
                putString("git_remote_port", serverPort)
                putString("git_remote_username", serverUser)
                putString("git_remote_location", serverPath)
            }
            Timber.tag("GitServerConfigActivity").d { settings.getString("git_remote_location", "") ?: "" }
            Snackbar.make(binding.root, "Successfully saved configuration", Snackbar.LENGTH_SHORT).show()
        }
    }
}
