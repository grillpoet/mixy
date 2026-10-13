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
     */
    fun sortFolders(
        folders: List<MediaFolder>,
        spec: SortSpec
    ): List<MediaFolder> {
        // 名称排序不需要聚合，走简单的比较器路径
        if (spec.mode == SortMode.NAME) {
            val cmp: Comparator<MediaFolder> =
                compareBy(String.CASE_INSENSITIVE_ORDER) { f -> f.folderName }
            return folders.sortedWith(
                if (spec.order == SortOrder.ASC) cmp else cmp.reversed()
            )
        }

        // 其他模式：先算 key，再排序
        val keyed: List<Pair<MediaFolder, Long>> = when (spec.mode) {
            SortMode.MODIFIED -> folders.map { f ->
                f to (f.items.maxOfOrNull { it.dateModified } ?: 0L)
            }
            SortMode.SIZE -> folders.map { f ->
                f to f.items.sumOf { it.size }
            }
            SortMode.DURATION -> folders.map { f ->
                f to f.items.sumOf { it.duration }
            }
            SortMode.NAME -> folders.map { f -> f to 0L }  // 不会走到
        }

        val sorted = if (spec.order == SortOrder.ASC) {
            keyed.sortedBy { it.second }
        } else {
            keyed.sortedByDescending { it.second }
        }
        return sorted.map { it.first }
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
}
