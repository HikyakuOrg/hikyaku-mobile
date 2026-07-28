package org.hikyaku.mobile.net

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO

internal actual fun httpClientEngine(): HttpClientEngine = CIO.create()
