package com.ringly.app.onboarding

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ringly.app.R

class PermissionRowBinder(
    private val inflater: LayoutInflater,
    private val container: ViewGroup,
    private val onAllow: (PermissionKind) -> Unit,
    private val onOpenAppSettings: (PermissionKind) -> Unit
) {

    fun render(steps: List<PermissionStep>) {
        container.removeAllViews()
        steps.forEach { step -> container.addView(bind(step)) }
    }

    private fun bind(step: PermissionStep): View {
        val view = inflater.inflate(R.layout.item_permission_step, container, false)
        val kind = step.kind

        view.findViewById<TextView>(R.id.permission_title).setText(titleRes(kind))
        view.findViewById<TextView>(R.id.permission_desc).setText(descRes(kind))

        val allowButton = view.findViewById<Button>(R.id.permission_allow_button)
        if (step.isPermanentlyDenied) {
            allowButton.setText(R.string.rationale_open_app_settings)
            allowButton.setOnClickListener { onOpenAppSettings(kind) }
        } else {
            allowButton.setText(allowRes(kind))
            allowButton.setOnClickListener { onAllow(kind) }
        }
        allowButton.visibility = if (step.isGranted) View.GONE else View.VISIBLE

        val status = view.findViewById<TextView>(R.id.permission_status)
        if (step.isGranted) {
            status.setText(R.string.rationale_granted)
            status.setTextColor(
                ContextCompat.getColor(container.context, R.color.granted_green)
            )
        } else if (step.isPermanentlyDenied) {
            status.setText(R.string.rationale_denied)
            status.setTextColor(
                ContextCompat.getColor(container.context, R.color.denied_text)
            )
        } else {
            status.setText(R.string.rationale_denied_retry)
            status.setTextColor(
                ContextCompat.getColor(container.context, R.color.denied_text)
            )
        }

        return view
    }

    private fun titleRes(kind: PermissionKind): Int = when (kind) {
        PermissionKind.CONTACTS -> R.string.rationale_contacts_title
        PermissionKind.PHONE_STATE -> R.string.rationale_phone_title
        PermissionKind.NOTIFICATIONS -> R.string.rationale_notifications_title
        PermissionKind.OVERLAY -> R.string.rationale_overlay_title
    }

    private fun descRes(kind: PermissionKind): Int = when (kind) {
        PermissionKind.CONTACTS -> R.string.rationale_contacts_desc
        PermissionKind.PHONE_STATE -> R.string.rationale_phone_desc
        PermissionKind.NOTIFICATIONS -> R.string.rationale_notifications_desc
        PermissionKind.OVERLAY -> R.string.rationale_overlay_desc
    }

    private fun allowRes(kind: PermissionKind): Int = when (kind) {
        PermissionKind.CONTACTS -> R.string.rationale_contacts_allow
        PermissionKind.PHONE_STATE -> R.string.rationale_phone_allow
        PermissionKind.NOTIFICATIONS -> R.string.rationale_notifications_allow
        PermissionKind.OVERLAY -> R.string.rationale_overlay_open_settings
    }
}