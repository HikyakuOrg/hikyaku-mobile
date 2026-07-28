package org.hikyaku.mobile

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform