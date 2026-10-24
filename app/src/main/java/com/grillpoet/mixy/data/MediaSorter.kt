package com.grillpoet.mixy.data

import com.grillpoet.mixy.data.model.MediaFolder
import com.grillpoet.mixy.data.model.MediaItem
import com.grillpoet.mixy.data.model.SortMode
import com.grillpoet.mixy.data.model.SortOrder
import com.grillpoet.mixy.data.model.SortSpec

/**
 * 全局排序工具。两个浏览界面共用。
 *
 * 无子文件夹场景，不做 foldersFirst。
 * 排序字段和顺序由 [SortSpec] 打包传入。
 *
 * ## 为什么用 Schwartzian transform
 *
 * 文件夹的「修改时间 / 大小 / 时长」需要遍历其 items 聚合得到。
 * 如果直接在 comparator 里计算：
 *
 * ```
 * compareBy { f -> f.items.sumOf { it.size } }
 * ```
 *
 * 排序期间 comparator 被调用 O(n log n) 次，每次都要重新遍历 items，
 * 几百个文件夹时会消耗大量 CPU。
 *
 * Schwartzian transform 的做法是：先对每一项预先算好排序 key，
 * 再按 key 排序。这样聚合计算只发生 n 次，而不是 n log n 次。
 */
object MediaSorter {

    /**
     * 按 [spec] 排序文件夹。
     *
     * 聚合规则：
     *  - 修改时间：取 items 中最大的 dateModified
     *  - 大小 / 时长：items 求和
     *
     * 四种 [SortMode] 平铺在 when 里，每个分支独立完整。
     * 不用前置 if 提前返回，避免后续 when 出现 unreachable 分支。
     */
    fun sortFolders(
        folders: List<MediaFolder>,
        spec: SortSpec
    ): List<MediaFolder> = when (spec.mode) {
        SortMode.NAME -> {
            val cmp: Comparator<MediaFolder> =
                compareBy(String.CASE_INSENSITIVE_ORDER) { f -> f.folderName }
            folders.sortedWith(
                if (spec.order == SortOrder.ASC) cmp else cmp.reversed()
            )
        }
        SortMode.MODIFIED -> sortByAggregatedKey(folders, spec.order) {
            it.items.maxOfOrNull { i -> i.dateModified } ?: 0L
        }
        SortMode.SIZE -> sortByAggregatedKey(folders, spec.order) {
            it.items.sumOf { i -> i.size }
        }
        SortMode.DURATION -> sortByAggregatedKey(folders, spec.order) {
            it.items.sumOf { i -> i.duration }
        }
    }

    /**
     * 按 [spec] 排序媒体项。
     *
     * 每一项都有直接的排序字段，不需要聚合，因此直接排序即可。
     */
    fun sortItems(
        items: List<MediaItem>,
        spec: SortSpec
    ): List<MediaItem> {
        val cmp: Comparator<MediaItem> = when (spec.mode) {
            SortMode.NAME ->
                compareBy(String.CASE_INSENSITIVE_ORDER) { i -> i.displayName }
            SortMode.MODIFIED -> compareBy { i -> i.dateModified }
            SortMode.SIZE -> compareBy { i -> i.size }
            SortMode.DURATION -> compareBy { i -> i.duration }
        }
        return items.sortedWith(if (spec.order == SortOrder.ASC) cmp else cmp.reversed())
    }

    /**
     * 对每一项先算聚合 key，再按 key 排序（Schwartzian transform）。
     *
     * 聚合计算只发生 n 次，而不是 comparator 里的 n log n 次。
     */
    private fun <T> sortByAggregatedKey(
        items: List<T>,
        order: SortOrder,
        key: (T) -> Long
    ): List<T> {
        val keyed = items.map { it to key(it) }
        val sorted = if (order == SortOrder.ASC) {
            keyed.sortedBy { it.second }
        } else {
            keyed.sortedByDescending { it.second }
        }
        return sorted.map { it.first }
    }
}