/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.crypto

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import androidx.appcompat.widget.AppCompatTextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.ui.dialogs.PasswordGeneratorDialogFragment

class PasswordEditActivity : BasePgpActivity() {
    private val monoTypeface = Typeface.createFromAsset(assets, "fonts/sourcecodepro.ttf")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.encrypt_layout)
        findViewById<MaterialButton>(R.id.generate_password)?.setOnClickListener {
            PasswordGeneratorDialogFragment()
                .show(supportFragmentManager, "generator")
        }

        title = getString(R.string.edit_password_title)

        val passwordView: TextInputEditText = findViewById(R.id.crypto_password_edit)
        val extraView: TextInputEditText = findViewById(R.id.crypto_extra_edit)
        val nameView: TextInputEditText = findViewById(R.id.crypto_password_file_edit)
        val passwordCategoryView: AppCompatTextView = findViewById(R.id.crypto_password_category)

        passwordView.setText(passwordEntry?.password)
        passwordView.typeface = monoTypeface
        extraView.setText(passwordEntry?.extraContent)
        extraView.typeface = monoTypeface

        passwordCategoryView.text = relativeParentPath
        nameView.setText(name)
        nameView.isEnabled = false

        DecryptActivity.delayTask?.cancelAndSignal(true)

        val data = Intent(this, DecryptActivity::class.java)
        data.putExtra("fromDecrypt", true)
        intent = data
        invalidateOptionsMenu()
    }
}
