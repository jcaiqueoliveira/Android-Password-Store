/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.passwordstore.android.db.dao

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.asLiveData
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.passwordstore.android.db.TestDatabase
import com.passwordstore.android.db.blockingObserve
import com.passwordstore.android.db.entity.PasswordEntity
import com.passwordstore.android.db.entity.StoreEntity
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PasswordDaoTest {
    @get:Rule
    var rule: TestRule = InstantTaskExecutorRule()

    private lateinit var passwordDao: PasswordDao
    private lateinit var storeDao: StoreDao
    private lateinit var db: TestDatabase

    @Before
    fun createDB() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TestDatabase::class.java).allowMainThreadQueries().build()
        passwordDao = db.getPasswordDao()
        storeDao = db.getStoreDao()
    }

    @Test
    fun testInsertPassword() {
        val store = StoreEntity(name = "store", external = false, initialized = true)
        storeDao.insertStore(store)
        val retrievedStore = storeDao.getStoreByName("store").asLiveData().blockingObserve()
        assertThat(retrievedStore?.get(0)?.name, equalTo(store.name))
        val storeId = retrievedStore?.get(0)?.id!!
        val password = PasswordEntity(storeId = storeId, name = "amazon", username = "test@aps.authors.pass", passwordLocation = "personal/amazon", notes = "")
        passwordDao.insertPassword(password)
        val byName = passwordDao.getPasswordsByName("amazon").asLiveData().blockingObserve()
        assertThat(byName?.size, equalTo(1))
        assertThat(byName?.get(0)?.name, equalTo(password.name))
        assertThat(byName?.get(0)?.notes, equalTo(""))
        assertThat(byName?.get(0)?.passwordLocation, equalTo("personal/amazon"))
        assertThat(byName?.get(0)?.username, equalTo("test@aps.authors.pass"))
    }
}
