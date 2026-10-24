package com.grillpoet.mixy.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.grillpoet.mixy.data.model.MediaType

object PermissionHelper {

    private const val API_33 = 33
    private const val API_34 = 34

    fun notificationPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= API_33) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }

    /**
     * 某媒体类型要请求的权限。
     *
     * API 34+ 图片/视频同时请求「完全」和「部分」权限，
     * 让系统弹出「完全允许 / 选择部分」的选择框。
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun forType(type: MediaType): Array<String> = when (type) {
        MediaType.PDF -> emptyArray()

        MediaType.MUSIC ->
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)

        MediaType.IMAGE -> visualPermissions(Manifest.permission.READ_MEDIA_IMAGES)
        MediaType.VIDEO -> visualPermissions(Manifest.permission.READ_MEDIA_VIDEO)
    }

    private fun visualPermissions(full: String): Array<String> = when {
        Build.VERSION.SDK_INT >= API_34 -> arrayOf(
            full,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= API_33 -> arrayOf(full)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun has(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED

    /** 某媒体类型的权限是否可用于展示内容。PDF 恒为 true。 */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun hasForType(context: Context, type: MediaType): Boolean = when (type) {
        MediaType.PDF -> true

        MediaType.MUSIC ->
            has(context, Manifest.permission.READ_MEDIA_AUDIO)

        MediaType.IMAGE -> hasVisual(
            context,
            Manifest.permission.READ_MEDIA_IMAGES
        )

        MediaType.VIDEO -> hasVisual(
            context,
            Manifest.permission.READ_MEDIA_VIDEO
        )
    }

    /**
     * 图片/视频：完全授权或 API 34+ 部分授权都算「可用」。
     * 部分授权时 MediaStore 只返回用户选中的项，属正常行为。
     */
    private fun hasVisual(context: Context, fullPermission: String): Boolean {
        if (Build.VERSION.SDK_INT < API_33) {
            return has(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (has(context, fullPermission)) return true
        if (Build.VERSION.SDK_INT >= API_34 &&
            has(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        ) return true
        return false
    }
}