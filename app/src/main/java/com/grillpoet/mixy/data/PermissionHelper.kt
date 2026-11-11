package com.grillpoet.mixy.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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

    fun forType(type: MediaType): Array<String> = when (type) {
        MediaType.PDF -> emptyArray()
        MediaType.MUSIC -> audioPermissions()
        MediaType.IMAGE -> visualPermissions(Manifest.permission.READ_MEDIA_IMAGES)
        MediaType.VIDEO -> visualPermissions(Manifest.permission.READ_MEDIA_VIDEO)
    }

    private fun audioPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= API_33 ->
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        else ->
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
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

    fun hasForType(context: Context, type: MediaType): Boolean = when (type) {
        MediaType.PDF -> true
        MediaType.MUSIC -> hasAudio(context)
        MediaType.IMAGE -> hasVisual(context, Manifest.permission.READ_MEDIA_IMAGES)
        MediaType.VIDEO -> hasVisual(context, Manifest.permission.READ_MEDIA_VIDEO)
    }

    private fun hasAudio(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= API_33) {
            has(context, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            has(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    /**
     * 图片/视频：完全授权或 API 34+ 部分授权都算「可用」。
     *
     * ## 修复说明
     *
     * READ_MEDIA_VISUAL_USER_SELECTED 是图片和视频**共享**的「部分授权」标志。
     * 在 Android 14 上，用户对图片选「允许全部」时，系统会同时授予
     * READ_MEDIA_IMAGES + READ_MEDIA_VISUAL_USER_SELECTED。
     * 此时若切到视频，只检查 READ_MEDIA_VISUAL_USER_SELECTED 会误判为
     * 「视频有权限」，实际 READ_MEDIA_VIDEO 未授予，MediaStore 返回空列表，
     * 导致「没有权限按钮，但视频列表空」。
     *
     * 判断规则：
     *  - 当前类型的完全权限授予 → 有权限
     *  - 当前类型未授予，但另一类的完全权限授予 → 用户明确只授权了另一类，
     *    当前类型视为**未授权**（让用户去请求当前类型的权限）
     *  - 两类完全权限都未授予，但 READ_MEDIA_VISUAL_USER_SELECTED 授予
     *    → 用户选了「部分」，当前类型可能有部分内容 → 有权限
     */
    private fun hasVisual(context: Context, fullPermission: String): Boolean {
        if (Build.VERSION.SDK_INT < API_33) {
            return has(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        // 完全授权
        if (has(context, fullPermission)) return true

        // API 34+ 部分授权
        if (Build.VERSION.SDK_INT >= API_34 &&
            has(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        ) {
            val otherFull = if (fullPermission == Manifest.permission.READ_MEDIA_IMAGES) {
                Manifest.permission.READ_MEDIA_VIDEO
            } else {
                Manifest.permission.READ_MEDIA_IMAGES
            }
            // 另一类有完全授权 → 用户只授权了另一类，当前类未授权
            if (has(context, otherFull)) return false
            // 两类都只部分授权 → 用户选了「部分」，当前类可用
            return true
        }

        return false
    }
}