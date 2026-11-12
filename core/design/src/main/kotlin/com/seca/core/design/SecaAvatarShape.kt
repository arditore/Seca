package com.seca.core.design

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape

/**
 * What shape a face is cut to, where the face is the centrepiece.
 *
 * [Playful] is the default and gives each person a shape of their own, picked from their name
 * so they always keep it. Asked for in #25: somebody wanted one shape for all of them, for a
 * more serious look, and somebody else will want the opposite — so the choice is theirs, with
 * the playful default left as it was.
 */
enum class SecaAvatarShape {
    /** A different shape for each person, picked from their name. */
    Playful,
    Circle,
    Squircle,
    Cookie,
    Clover,
    Sunny,
    Flower,
    ;

    companion object {
        /** The stored name, or [Playful] for anything unwritten or written by a later version. */
        fun of(name: String?): SecaAvatarShape = entries.firstOrNull { it.name == name } ?: Playful
    }
}

/**
 * The shape itself. [seed] only matters for [SecaAvatarShape.Playful], which picks from it so
 * that a person keeps the same shape wherever they appear.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SecaAvatarShape.shapeFor(seed: String): Shape = when (this) {
    SecaAvatarShape.Playful -> when (Math.floorMod(seed.hashCode(), 4)) {
        0 -> MaterialShapes.Cookie9Sided.toShape()
        1 -> MaterialShapes.Clover4Leaf.toShape()
        2 -> MaterialShapes.Sunny.toShape()
        else -> MaterialShapes.Cookie6Sided.toShape()
    }
    SecaAvatarShape.Circle -> CircleShape
    // A squircle is not one of Material's shapes; it is a square rounded until it almost is
    // not one, which is the shape people mean when they ask for it.
    SecaAvatarShape.Squircle -> RoundedCornerShape(percent = SQUIRCLE_PERCENT)
    SecaAvatarShape.Cookie -> MaterialShapes.Cookie9Sided.toShape()
    SecaAvatarShape.Clover -> MaterialShapes.Clover4Leaf.toShape()
    SecaAvatarShape.Sunny -> MaterialShapes.Sunny.toShape()
    SecaAvatarShape.Flower -> MaterialShapes.Flower.toShape()
}

private const val SQUIRCLE_PERCENT = 30
