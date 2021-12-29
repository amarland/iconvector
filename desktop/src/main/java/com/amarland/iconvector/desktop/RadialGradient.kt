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

package com.amarland.iconvector.desktop

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.ShaderBrush
import com.amarland.iconvector.lib.IconVGIntermediateRepresentation as IR
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.GradientStyle
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Shader

// Copy of https://cs.android.com/androidx/platform/frameworks/support/+/1ac23550d12fc382ce65f7207c9fd89abe6afa62:compose/ui/ui-graphics/src/commonMain/kotlin/androidx/compose/ui/graphics/Brush.kt;l=505,
// but modified to add the ability to specify a transform matrix.
@Immutable
class RadialGradient internal constructor(
    private val colors: IntArray,
    private val stops: FloatArray? = null,
    private val center: Offset,
    private val radius: Float,
    private val tileMode: IR.TileMode = IR.TileMode.CLAMP,
    private val matrix: IR.Matrix = IR.Matrix()
) : ShaderBrush() {

    override val intrinsicSize: Size
        get() = if (radius.isFinite()) Size(radius * 2, radius * 2) else Size.Unspecified

    override fun createShader(size: Size): Shader {
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

        return Shader.makeRadialGradient(
            centerX,
            centerY,
            radius,
            colors,
            stops,
            GradientStyle(
                when (tileMode) {
                    IR.TileMode.REPEAT -> FilterTileMode.REPEAT
                    IR.TileMode.MIRROR -> FilterTileMode.MIRROR
                    else -> FilterTileMode.CLAMP
                },
                isPremul = true,
                Matrix33(
                    // row-major order
                    matrix[IR.Matrix.INDEX_SCALE_X],
                    matrix[IR.Matrix.INDEX_SKEW_X],
                    matrix[IR.Matrix.INDEX_TRANSLATE_X],
                    matrix[IR.Matrix.INDEX_SKEW_Y],
                    matrix[IR.Matrix.INDEX_SCALE_Y],
                    matrix[IR.Matrix.INDEX_TRANSLATE_Y],
                    0F, 0F, 1F
                )
            )
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RadialGradient) return false

        if (!colors.contentEquals(other.colors)) return false
        if (stops?.contentEquals(other.stops) == false) return false
        if (center != other.center) return false
        if (radius != other.radius) return false
        if (tileMode.value != other.tileMode.value) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + (stops?.hashCode() ?: 0)
        result = 31 * result + center.hashCode()
        result = 31 * result + radius.hashCode()
        result = 31 * result + tileMode.value.hashCode()
        return result
    }

    override fun toString(): String {
        val centerValue = if (center.isSpecified) "center=$center, " else ""
        val radiusValue = if (radius.isFinite()) "radius=$radius, " else ""
        return "RadialGradient(" +
                "colors=$colors, " +
                "stops=$stops, " +
                centerValue +
                radiusValue +
                "tileMode=$tileMode, " +
                "matrix=${matrix.values})"
    }
}
