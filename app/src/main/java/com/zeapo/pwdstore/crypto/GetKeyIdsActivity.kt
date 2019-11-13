/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.crypto

import android.content.Intent
import android.util.Log
import me.msfjarvis.openpgpktx.util.OpenPgpApi
import org.openintents.openpgp.IOpenPgpService2

class GetKeyIdsActivity : BasePgpActivity() {

    /**
     * Get the Key ids from OpenKeychain
     */
    private fun getKeyIds(receivedIntent: Intent? = null) {
        val data = receivedIntent ?: Intent()
        data.action = OpenPgpApi.ACTION_GET_KEY_IDS
        api?.executeApiAsync(data, null, null, object : OpenPgpApi.IOpenPgpCallback {
            override fun onReturn(result: Intent?) {
                when (result?.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
                    OpenPgpApi.RESULT_CODE_SUCCESS -> {
                        try {
                            val ids = result.getLongArrayExtra(OpenPgpApi.RESULT_KEY_IDS)
                                ?: LongArray(0)
                            val keys = ids.map { it.toString() }.toSet()

                            // use Long
                            settings.edit().putStringSet("openpgp_key_ids_set", keys).apply()

                            showSnackbar("PGP keys selected")

                            setResult(RESULT_OK)
                            finish()
                        } catch (e: Exception) {
                            Log.e(TAG, "An Exception occurred", e)
                        }
                    }
                    OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED -> handleUserInteractionRequest(result,
                        REQUEST_KEY_ID
                    )
                    OpenPgpApi.RESULT_CODE_ERROR -> handleError(result)
                }
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) {
            getKeyIds(data)
        } else if (resultCode == RESULT_CANCELED) {
            setResult(RESULT_CANCELED, data)
            finish()
        }
    }

    override fun onBound(service: IOpenPgpService2?) {
        super.onBound(service)
        getKeyIds()
    }
}
