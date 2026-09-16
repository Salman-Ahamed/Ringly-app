package com.ringly.app.onboarding

import com.ringly.app.sync.SyncOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncFlowRouterTest {

    @Test
    fun `success with contacts permission goes ready`() {
        assertEquals(
            SyncFlowResult.READY,
            SyncFlowRouter.decide(SyncOutcome.SUCCESS, hasContactsPermission = true)
        )
    }

    @Test
    fun `success without contacts permission degrades ready`() {
        assertEquals(
            SyncFlowResult.READY_DEGRADED,
            SyncFlowRouter.decide(SyncOutcome.SUCCESS, hasContactsPermission = false)
        )
    }

    @Test
    fun `retry outcome fails`() {
        assertEquals(
            SyncFlowResult.FAILED,
            SyncFlowRouter.decide(SyncOutcome.RETRY, hasContactsPermission = true)
        )
    }

    @Test
    fun `failure outcome fails even with permission`() {
        assertEquals(
            SyncFlowResult.FAILED,
            SyncFlowRouter.decide(SyncOutcome.FAILURE, hasContactsPermission = true)
        )
    }
}