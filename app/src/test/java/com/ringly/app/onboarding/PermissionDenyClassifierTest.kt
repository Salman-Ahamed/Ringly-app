package com.ringly.app.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionDenyClassifierTest {

    @Test
    fun `granted never classifies permanent`() {
        assertEquals(
            DenyState.REQUESTABLE,
            PermissionDenyClassifier.classify(granted = true, shouldShowRationale = false)
        )
        assertEquals(
            DenyState.REQUESTABLE,
            PermissionDenyClassifier.classify(granted = true, shouldShowRationale = true)
        )
    }

    @Test
    fun `single denial stays requestable when rationale shown`() {
        assertEquals(
            DenyState.REQUESTABLE,
            PermissionDenyClassifier.classify(granted = false, shouldShowRationale = true)
        )
    }

    @Test
    fun `denied without rationale is permanent`() {
        assertEquals(
            DenyState.PERMANENT,
            PermissionDenyClassifier.classify(granted = false, shouldShowRationale = false)
        )
    }
}