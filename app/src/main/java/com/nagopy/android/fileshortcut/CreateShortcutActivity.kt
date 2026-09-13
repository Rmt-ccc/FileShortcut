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

import android.Manifest
import android.content.Intent
import android.content.pm.ShortcutManager
import androidx.databinding.DataBindingUtil
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import com.nagopy.android.fileshortcut.databinding.ActivityCreateShortcutBinding
import timber.log.Timber
import java.io.File


class CreateShortcutActivity : AppCompatActivity(), View.OnClickListener {

    lateinit var binding: ActivityCreateShortcutBinding

    private val contentHelper by lazy { asApp().contentHelper }
    private val shortcutCreator by lazy { asApp().shortcutCreator }

    private val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (!granted) {
                    onPermissionDenied()
                }
            }

    private val filePickerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                handleFileResult(result)
            }

    private val iconPickerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    binding.shortcutIcon = result.data?.data
                }
            }

    private val historyLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    handleHistoryResult(result.data)
                }
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_create_shortcut)
        binding.onClickListener = this

        handleSharedIntent(intent)
    }

    fun handleSharedIntent(intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_SEND) {
            return
        }

        val extra = intent.extras
        extra?.keySet()?.forEach {
            if (it == Intent.EXTRA_STREAM) {
                val es = extra.get(Intent.EXTRA_STREAM) ?: return@forEach
                val uri = Uri.parse(es.toString())
                val data = Intent()
                data.data = uri
                handleFileResult(ActivityResult(RESULT_OK, data))
                return@forEach
            }
        }
    }

    override fun onStart() {
        super.onStart()
        requestReadStoragePermission()
    }

    private fun requestReadStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun withReadStoragePermission(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    override fun onClick(v: View?) {
        Timber.d("onClick %d", v?.id)
        when (v?.id) {
            R.id.filePickerButton -> withReadStoragePermission { startFilePicker() }
            R.id.iconPickerButton -> withReadStoragePermission { startIconPicker() }
            R.id.createShortcutButton -> createShortcut()
        }
    }

    private fun startFilePicker() {
        Timber.d("startFilePicker")
        val intent = Intent(Intent.ACTION_GET_CONTENT).setType("*/*")
        filePickerLauncher.launch(intent)
    }

    private fun startIconPicker() {
        Timber.d("startIconPicker")
        val intent = Intent(Intent.ACTION_GET_CONTENT).setType("image/*")
        iconPickerLauncher.launch(intent)
    }

    fun createShortcut() {
        val id = binding.id
        val pathString = binding.targetFilePath.text.toString()
        val shortcutName = binding.targetShortcutName.text.toString()
        val mimeType = contentHelper.getMimeType(pathString)
        val iconBitmap = getIconBitmap(binding.targetShortcutIcon)
        if (id == null) {
            shortcutCreator.create(this, pathString, shortcutName, mimeType, iconBitmap)
        } else {
            shortcutCreator.update(this, id, pathString, shortcutName, mimeType, iconBitmap)
        }
    }

    fun getIconBitmap(imageView: ImageView): Bitmap {
        val backup_isDrawingCacheEnabled = imageView.isDrawingCacheEnabled
        if (imageView.isDrawingCacheEnabled) {
            imageView.destroyDrawingCache()
        }
        imageView.isDrawingCacheEnabled = true
        val bitmap = Bitmap.createBitmap(imageView.drawingCache)
        imageView.isDrawingCacheEnabled = backup_isDrawingCacheEnabled
        return bitmap
    }

    private fun handleFileResult(result: ActivityResult) {
        if (result.resultCode != RESULT_OK) {
            return
        }
        val data = result.data ?: return
        Timber.d("handleFileResult %s", data)
        val pathString = contentHelper.getPath(data.data)
        val mimeType = contentHelper.getMimeType(pathString)
        binding.filePath = pathString
        binding.mimeType = mimeType
        binding.shortcutName = if (contentHelper.isLocal(pathString)) {
            File(pathString).name.toString()
        } else {
            pathString
        }
        if (mimeType.startsWith("image")) {
            binding.shortcutIcon = data.data
        } else if (mimeType.startsWith("video")) {
            val thumbnail = ThumbnailUtils.createVideoThumbnail(pathString, MediaStore.Video.Thumbnails.MICRO_KIND)
            binding.targetShortcutIcon.setImageBitmap(thumbnail)
        } else {
            val bundledId = contentHelper.getBundledIconId(mimeType)
            if (bundledId != null) {
                binding.targetShortcutIcon.setImageResource(bundledId)
            }
        }
    }

    private fun handleHistoryResult(data: Intent?) {
        val id = data?.getStringExtra(CreatedShortcutListActivity.EXTRA_RESULT_ID)
        val path = data?.getStringExtra(CreatedShortcutListActivity.EXTRA_RESULT_PATH)
        val name = data?.getStringExtra(CreatedShortcutListActivity.EXTRA_RESULT_NAME)
        val icon = data?.getParcelableExtra(CreatedShortcutListActivity.EXTRA_RESULT_ICON) as? Bitmap
        binding.id = id
        binding.filePath = path
        binding.shortcutName = name
        binding.mimeType = contentHelper.getMimeType(path)
        binding.targetShortcutIcon.setImageBitmap(icon)
    }

    private fun onPermissionDenied() {
        Timber.d("onPermissionDenied")
        AlertDialog.Builder(this)
                .setTitle(R.string.need_permission)
                .setMessage(R.string.msg_need_permission)
                .setPositiveButton(R.string.app_setting) { _, _ ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                }
                .setNegativeButton(R.string.close) { _, _ -> finish() }
                .show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.activity_create_shortcut, menu)

        menu?.findItem(R.id.menu_history)?.isVisible = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutManager = getSystemService(ShortcutManager::class.java)
            if (intent?.categories?.contains(Intent.CATEGORY_LAUNCHER) == true // from home app
                    && shortcutManager.pinnedShortcuts.isNotEmpty()) {
                menu?.findItem(R.id.menu_history)?.isVisible = true
            }
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_license -> {
                startActivity(Intent(this, LicenseActivity::class.java))
            }
            R.id.menu_history -> {
                historyLauncher.launch(Intent(this, CreatedShortcutListActivity::class.java))
            }
        }
        return super.onOptionsItemSelected(item)
    }
}
