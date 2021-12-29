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

package com.amarland.iconvector.androidcompose

import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import com.amarland.iconvector.lib.IconVGIntermediateRepresentation as IR
import android.graphics.Matrix as AndroidMatrix
import android.graphics.RadialGradient as AndroidRadialGradient
import android.graphics.Shader.TileMode as AndroidTileMode
import androidx.compose.ui.graphics.Matrix as ComposeMatrix

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

        val colorsAsList = colors.map { argb -> Color(argb) }
        val transparentColorCount = countTransparentColors(colorsAsList)
        return AndroidRadialGradient(
            centerX,
            centerY,
            radius,
            makeTransparentColors(colorsAsList, transparentColorCount),
            makeTransparentStops(stops?.toList(), colorsAsList, transparentColorCount),
            when (tileMode) {
                IR.TileMode.REPEAT -> AndroidTileMode.REPEAT
                IR.TileMode.MIRROR -> AndroidTileMode.MIRROR
                else -> AndroidTileMode.CLAMP
            }
        ).apply {
            val composeMatrix = ComposeMatrix().apply {
                values[ComposeMatrix.ScaleX] = matrix[IR.Matrix.INDEX_SCALE_X]
                values[ComposeMatrix.ScaleY] = matrix[IR.Matrix.INDEX_SCALE_Y]
                values[ComposeMatrix.SkewX] = matrix[IR.Matrix.INDEX_SKEW_X]
                values[ComposeMatrix.SkewY] = matrix[IR.Matrix.INDEX_SKEW_Y]
                values[ComposeMatrix.TranslateX] = matrix[IR.Matrix.INDEX_TRANSLATE_X]
                values[ComposeMatrix.TranslateY] = matrix[IR.Matrix.INDEX_TRANSLATE_Y]
            }
            if (!composeMatrix.isIdentity()) {
                setLocalMatrix(AndroidMatrix().apply { setFrom(composeMatrix) })
            }
        }
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

// Source: https://cs.android.com/androidx/platform/frameworks/support/+/1ac23550d12fc382ce65f7207c9fd89abe6afa62:compose/ui/ui-graphics/src/androidMain/kotlin/androidx/compose/ui/graphics/AndroidShader.android.kt;l=102
private fun countTransparentColors(colors: List<Color>): Int {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        return 0
    }
    var transparentColorCount = 0
    for (i in 1 until colors.lastIndex) {
        if (colors[i].alpha == 0F) {
            transparentColorCount++
        }
    }
    return transparentColorCount
}

// Source: https://cs.android.com/androidx/platform/frameworks/support/+/1ac23550d12fc382ce65f7207c9fd89abe6afa62:compose/ui/ui-graphics/src/androidMain/kotlin/androidx/compose/ui/graphics/AndroidShader.android.kt;l=130
private fun makeTransparentColors(
    colors: List<Color>,
    transparentColorCount: Int
): IntArray {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        return IntArray(colors.size) { i -> colors[i].toArgb() }
    }
    val values = IntArray(colors.size + transparentColorCount)
    var valuesIndex = 0
    val lastColorsIndex = colors.lastIndex
    @Suppress("UseWithIndex") // `fastForEachIndexed` is used in the source
    for (colorsIndex in colors.indices) {
        values[valuesIndex++] =
            if (colors[colorsIndex].alpha == 0F) {
                when (colorsIndex) {
                    0 -> {
                        colors[1].copy(alpha = 0F)
                    }
                    lastColorsIndex -> {
                        colors[colorsIndex - 1].copy(alpha = 0F)
                    }
                    else -> {
                        colors[colorsIndex - 1].copy(alpha = 0F)
                        colors[colorsIndex + 1].copy(alpha = 0F)
                    }
                }
            } else {
                colors[colorsIndex]
            }.toArgb()
    }
    return values
}

// Source: https://cs.android.com/androidx/platform/frameworks/support/+/1ac23550d12fc382ce65f7207c9fd89abe6afa62:compose/ui/ui-graphics/src/androidMain/kotlin/androidx/compose/ui/graphics/AndroidShader.android.kt;l=168
private fun makeTransparentStops(
    stops: List<Float>?,
    colors: List<Color>,
    transparentColorCount: Int
): FloatArray? {
    if (transparentColorCount == 0) {
        return stops?.toFloatArray()
    }
    val newStops = FloatArray(colors.size + transparentColorCount)
    newStops[0] = stops?.get(0) ?: 0F
    var newStopsIndex = 1
    val lastColorsIndex = colors.lastIndex
    for (i in 1 until lastColorsIndex) {
        val color = colors[i]
        val stop = stops?.get(i) ?: (i.toFloat() / lastColorsIndex)
        newStops[newStopsIndex++] = stop
        if (color.alpha == 0F) {
            newStops[newStopsIndex++] = stop
        }
    }
    newStops[newStopsIndex] = stops?.get(lastColorsIndex) ?: 1F
    return newStops
}
