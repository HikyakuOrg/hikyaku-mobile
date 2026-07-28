package org.hikyaku.mobile.shift.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.hikyaku.mobile.shift.session.model.ShiftSession

/**
 * An in-process live view of the persisted [ShiftSession]. It is **not** the source of truth —
 * [ShiftSessionStore] (disk) is — but it lets an open shift screen react when the background
 * service advances or completes the shift while the app is backgrounded. [ShiftSessionStore]
 * publishes here on every save/clear, and the value is rehydrated from disk on a cold start.
 */
object ShiftSessionState {
    private val _sessions = MutableStateFlow<ShiftSession?>(null)
    val sessions: StateFlow<ShiftSession?> = _sessions.asStateFlow()

    fun publish(session: ShiftSession?) {
        _sessions.value = session
    }
}
