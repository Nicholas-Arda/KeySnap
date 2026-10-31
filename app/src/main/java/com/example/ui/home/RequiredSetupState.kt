package com.example.ui.home

enum class SetupStep { Accessibility, Background, Notifications }
enum class SetupPill { Setup, Live, Paused }
enum class SetupBanner { NotRunning, BackgroundRisk }

data class RequiredSetupState(
    val accessibility: Boolean,
    val background: Boolean,
    val notifications: Boolean,
    val notificationsDismissed: Boolean = false,
) {
    val completeCount: Int get() = listOf(accessibility, background).count { it }
    val nextStep: SetupStep? get() = when {
        !accessibility -> SetupStep.Accessibility
        !background -> SetupStep.Background
        else -> null
    }
    val banner: SetupBanner? get() = when (nextStep) {
        SetupStep.Accessibility -> SetupBanner.NotRunning
        SetupStep.Background -> SetupBanner.BackgroundRisk
        else -> null
    }
    val showNotifications: Boolean get() = nextStep == null && !notifications && !notificationsDismissed
    fun pill(mappingEnabled: Boolean): SetupPill = when {
        nextStep != null -> SetupPill.Setup
        mappingEnabled -> SetupPill.Live
        else -> SetupPill.Paused
    }
}
