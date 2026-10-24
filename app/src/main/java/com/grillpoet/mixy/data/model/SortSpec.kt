package com.grillpoet.mixy.data.model


data class SortSpec(
    val mode: SortMode = SortMode.NAME,
    val order: SortOrder = SortOrder.ASC
)
