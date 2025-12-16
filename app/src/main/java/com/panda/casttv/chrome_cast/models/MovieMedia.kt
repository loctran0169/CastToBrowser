package com.panda.casttv.chrome_cast.models

data class MovieMedia(
    var id: Long = 0,
    var title: String? = null,
    var description: String? = null,
    var bgImageUrl: String? = null,
    var cardImageUrl: String? = null,
    var videoUrl: String? = null,
    var studio: String? = null,
)