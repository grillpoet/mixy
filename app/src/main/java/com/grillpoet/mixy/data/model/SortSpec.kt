package com.grillpoet.mixy.data.model

/**
 * 排序规格。把排序字段和顺序打包，方便作为数据类参数或 Flow key 传递。
 *
 * 与 [Settings] 分离的原因：[Settings] 是持久化模型，字段多；
 * [SortSpec] 只承载「排序」这一个关注点，让 [MediaSorter] 的签名干净。
 */
data class SortSpec(
    val mode: SortMode = SortMode.NAME,
    val order: SortOrder = SortOrder.ASC
) {
    companion object {
        val DEFAULT = SortSpec()
    }
}
