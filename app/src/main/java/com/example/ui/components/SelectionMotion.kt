package com.example.ui.components

import androidx.compose.animation.core.spring

// The leading edge arrives first; the softer trailing edge stretches the selected pill in flight.
internal val SelectionLeadSpring = spring<Float>(dampingRatio = 0.58f, stiffness = 307f)
internal val SelectionTrailSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 122f)
