package com.example.facecompare

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * 存储/媒体权限管理
 */
object PermissionHelper {

    fun needsPermission(activity: Activity): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(activity, perm) !=
                PackageManager.PERMISSION_GRANTED
    }

    fun requestPermission(activity: Activity) {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        ActivityCompat.requestPermissions(activity, perms, REQUEST_CODE)
    }

    fun isGranted(requestCode: Int, grantResults: IntArray): Boolean =
        requestCode == REQUEST_CODE &&
        grantResults.isNotEmpty() &&
        grantResults[0] == PackageManager.PERMISSION_GRANTED

    const val REQUEST_CODE = 1001
}
