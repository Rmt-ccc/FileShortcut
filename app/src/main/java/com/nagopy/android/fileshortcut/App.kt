/*
 * Copyright 2017 75py
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nagopy.android.fileshortcut

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import timber.log.Timber

/**
 * Kodein は現在メンテナンスされていないため、シンプルな手動DI（lazy プロパティ）に置き換え。
 */
class App : Application() {

    val contentHelper: ContentHelper by lazy { ContentHelper(this, contentResolver) }
    val shortcutCreator: ShortcutCreator by lazy { ShortcutCreator() }

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}

fun Context.asApp() = this.applicationContext as App

fun Context.showErrorMessage(resId: Int) {
    Toast.makeText(this, resId, Toast.LENGTH_LONG).show()
}

fun Context.startActivityIfLaunchable(intent: Intent) {
    if (packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()) {
        startActivity(intent)
    } else {
        showErrorMessage(R.string.msg_not_launchable)
    }
}
