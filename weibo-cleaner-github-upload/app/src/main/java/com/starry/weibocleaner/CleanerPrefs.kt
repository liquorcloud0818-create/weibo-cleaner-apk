package com.starry.weibocleaner

object CleanerPrefs {
    const val FILE = "weibo_cleaner_local"
    const val KEY_MODE = "mode"
    const val MODE_LIKES = "likes"
    const val MODE_IMAGE_LIKES = "image_likes"
    const val MODE_FOLLOWS = "follows"
    const val MODE_HIDDEN_LIKES = "hidden_likes"
    const val WEIBO_PACKAGE = "com.sina.weibo"

    fun modeLabel(mode: String): String = when (mode) {
        MODE_FOLLOWS -> "清除关注"
        MODE_IMAGE_LIKES -> "清除图片赞"
        MODE_HIDDEN_LIKES -> "清理不可见微博残留"
        else -> "清除微博点赞（含图片微博）"
    }
}
