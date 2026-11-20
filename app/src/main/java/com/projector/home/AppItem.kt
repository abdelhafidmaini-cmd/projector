package com.projector.home

import android.graphics.drawable.Drawable

enum class SpecialAction {
    NONE,
    FILES,
    SETTINGS,
    ADD_APP
}

data class AppItem(
    val label: String,
    val packageName: String,
    val icon: Drawable?,
    val specialAction: SpecialAction = SpecialAction.NONE,
    val customIconRes: Int? = null
)
