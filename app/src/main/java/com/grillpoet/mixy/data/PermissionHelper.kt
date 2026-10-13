package com.grillpoet.mixy.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.grillpoet.mixy.data.model.MediaType

object PermissionHelper {

    /** 全部所需权限（用于一次性请求） */
    fun allPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VIDEO
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** 某媒体类型所需权限 */
    fun forType(type: MediaType): String = if (Build.VERSION.SDK_INT >= 33) {
        when (type) {
            MediaType.MUSIC -> Manifest.permission.READ_MEDIA_AUDIO
            MediaType.IMAGE -> Manifest.permission.READ_MEDIA_IMAGES
            MediaType.VIDEO -> Manifest.permission.READ_MEDIA_VIDEO
            MediaType.PDF   -> Manifest.permission.READ_MEDIA_IMAGES  // 走 Files 表，跟随 Images 权限
        }
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    fun has(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED

    fun hasAll(context: Context): Boolean =
        allPermissions().all { has(context, it) }
}