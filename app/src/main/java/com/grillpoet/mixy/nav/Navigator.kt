package com.grillpoet.mixy.nav

import androidx.compose.runtime.mutableStateListOf

class Navigator {
    private companion object { const val MAX_STACK = 20 }

    private val _stack = mutableStateListOf<Screen>(Screen.FolderList)
    val current: Screen get() = _stack.last()

    fun push(screen: Screen) {
        if (_stack.size >= MAX_STACK) return
        if (_stack.lastOrNull() == screen) return
        _stack.add(screen)
    }

    fun pop(): Boolean {
        if (_stack.size <= 1) return false
        _stack.removeAt(_stack.lastIndex)
        return true
    }
}