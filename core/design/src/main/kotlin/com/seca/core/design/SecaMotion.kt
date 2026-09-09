package com.seca.core.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Shared motion specs.
 *
 * Every Seca animation comes from here so the three apps move alike.
 * [expressiveSpring] carries the slight overshoot that gives M3 Expressive
 * its character; use it for anything the user directly manipulates.
 */
object SecaMotion {

    val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val StandardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun <T> emphasized() = tween<T>(durationMillis = 500, easing = EmphasizedEasing)

    fun <T> standard() = tween<T>(durationMillis = 300, easing = StandardEasing)

    fun <T> expressiveSpring() = spring<T>(
        dampingRatio = 0.75f,
        stiffness = Spring.StiffnessMediumLow,
    )
}
