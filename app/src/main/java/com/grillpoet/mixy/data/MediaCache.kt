package com.grillpoet.mixy.data

import com.grillpoet.mixy.data.model.MediaFolder
import com.grillpoet.mixy.data.model.MediaType

class MediaCache {
    private data class Bucket(val folders: List<MediaFolder>, val cachedAt: Long)
    private val cache = mutableMapOf<MediaType, Bucket>()

    private val defaultTtlMs = 5 * 60 * 1000L
    private val pdfTtlMs = 30 * 60 * 1000L

    fun get(type: MediaType): List<MediaFolder>? {
        val b = cache[type] ?: return null
        val ttl = if (type == MediaType.PDF) pdfTtlMs else defaultTtlMs
        if (System.currentTimeMillis() - b.cachedAt > ttl) return null
        return b.folders
    }

    fun put(type: MediaType, folders: List<MediaFolder>) {
        cache[type] = Bucket(folders, System.currentTimeMillis())
    }

    fun invalidate(type: MediaType) { cache.remove(type) }
    fun invalidateAll() { cache.clear() }
}