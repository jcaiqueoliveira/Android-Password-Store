/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.crypto

import android.os.Bundle
import android.view.Menu
import androidx.appcompat.widget.AppCompatTextView
import com.zeapo.pwdstore.R

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
}
