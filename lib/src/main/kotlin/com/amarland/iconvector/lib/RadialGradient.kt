/*
 * Copyright 2019 The Android Open Source Project
 * Copyright 2021 Anthony Marland
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.amarland.iconvector.lib

import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.TileMode

// Copy of https://cs.android.com/androidx/platform/frameworks/support/+/1ac23550d12fc382ce65f7207c9fd89abe6afa62:compose/ui/ui-graphics/src/commonMain/kotlin/androidx/compose/ui/graphics/Brush.kt;l=505,
// but modified to add the ability to specify a transform matrix.
abstract class AbstractRadialGradientDelegate<S>(
    private val colors: List<Color>,
    private val stops: List<Float>?,
    private val center: Offset,
    private val radius: Float,
    private val tileMode: TileMode,
    private val matrix: Matrix
) {

    val intrinsicSize = if (radius.isFinite()) Size(radius * 2, radius * 2) else Size.Unspecified

    fun createShader(size: Size): S {
        val centerX: Float
        val centerY: Float
        if (center.isUnspecified) {
            val drawCenter = size.center
            centerX = drawCenter.x
            centerY = drawCenter.y
        } else {
            centerX = if (center.x == Float.POSITIVE_INFINITY) size.width else center.x
            centerY = if (center.y == Float.POSITIVE_INFINITY) size.height else center.y
        }

        return createShaderInternal(
            colors,
            stops,
            Offset(centerX, centerY),
            if (radius == Float.POSITIVE_INFINITY) size.minDimension / 2 else radius,
            tileMode,
            matrix
        )
    }

    protected abstract fun createShaderInternal(
        colors: List<Color>,
        stops: List<Float>?,
        center: Offset,
        radius: Float,
        tileMode: TileMode,
        matrix: Matrix
    ): S

    abstract fun asBrush(): Brush

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AbstractRadialGradientDelegate<*>) return false

        if (colors != other.colors) return false
        if (stops != other.stops) return false
        if (center != other.center) return false
        if (radius != other.radius) return false
        if (tileMode != other.tileMode) return false
        if (matrix != other.matrix) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + (stops?.hashCode() ?: 0)
        result = 31 * result + center.hashCode()
        result = 31 * result + radius.hashCode()
        result = 31 * result + tileMode.hashCode()
        result = 31 * result + matrix.hashCode()
        return result
    }

    override fun toString(): String {
        val centerValue = if (center.isSpecified) "center=$center" else ""
        val radiusValue = if (radius.isFinite()) "radius=$radius" else ""
        return """
            !RadialGradient(
            !  colors=$colors,
            !  stops=$stops,
            !  $centerValue,
            !  $radiusValue,
            !  tileMode=$tileMode
            !  matrix=
            !${matrix.toString().prependIndent("    ")}
            !)""".trimMargin("!") // '|' is used by Matrix::toString
    }
}
