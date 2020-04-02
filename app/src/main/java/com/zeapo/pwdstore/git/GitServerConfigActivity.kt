package com.zeapo.pwdstore.git

import android.os.Bundle
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
    }
}
