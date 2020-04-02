package com.zeapo.pwdstore.git

import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.github.ajalt.timberkt.d
import com.zeapo.pwdstore.git.config.ConnectionMode
import com.zeapo.pwdstore.git.config.Protocol

/**
 * Abstract AppCompatActivity that holds some information that is commonly shared across git-related
 * tasks and makes sense to be held here.
 */
abstract class AbstractGitActivity : AppCompatActivity() {
    lateinit var protocol: Protocol
    lateinit var connectionMode: ConnectionMode
    lateinit var hostname: String
    lateinit var serverUrl: String
    lateinit var serverPort: String
    lateinit var serverUser: String
    lateinit var serverPath: String
    lateinit var settings: SharedPreferences
        private set

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settings = PreferenceManager.getDefaultSharedPreferences(this)
        protocol = Protocol.fromString(settings.getString("git_remote_protocol", null))
        connectionMode = ConnectionMode.fromString(settings.getString("git_remote_auth", null))
        hostname = settings.getString("git_remote_location", null) ?: ""
        serverUrl = settings.getString("git_remote_server", null) ?: ""
        serverPort = settings.getString("git_remote_port", null) ?: ""
        serverUser = settings.getString("git_remote_username", null) ?: ""
        serverPath = settings.getString("git_remote_location", null) ?: ""
        d { "hostname=$hostname,serverUrl=$serverUrl,serverPort=$serverPort,serverUser=$serverUser,serverPath=$serverPath" }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
