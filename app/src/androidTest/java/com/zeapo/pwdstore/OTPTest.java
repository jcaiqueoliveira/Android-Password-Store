/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore;

import com.zeapo.pwdstore.utils.OTP;
import junit.framework.TestCase;

public class OTPTest extends TestCase {
    public void testOtp() {
        String code = OTP.calculateCode("JBSWY3DPEHPK3PXP", 0L, "sha1", "s");
        assertEquals("282760", code);
    }
}
