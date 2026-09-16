package com.ringly.app.onboarding

import com.ringly.app.sync.SyncOutcome

enum class SyncFlowResult {
    READY,
    READY_DEGRADED,
    FAILED
}

object SyncFlowRouter {

    fun decide(outcome: SyncOutcome, hasContactsPermission: Boolean): SyncFlowResult =
        when {
            outcome != SyncOutcome.SUCCESS -> SyncFlowResult.FAILED
            !hasContactsPermission -> SyncFlowResult.READY_DEGRADED
            else -> SyncFlowResult.READY
        }
}