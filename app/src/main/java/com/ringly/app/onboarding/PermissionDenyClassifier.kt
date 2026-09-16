package com.ringly.app.onboarding

enum class DenyState {
    REQUESTABLE,
    PERMANENT
}

object PermissionDenyClassifier {

    fun classify(granted: Boolean, shouldShowRationale: Boolean): DenyState =
        when {
            granted -> DenyState.REQUESTABLE
            !shouldShowRationale -> DenyState.PERMANENT
            else -> DenyState.REQUESTABLE
        }
}