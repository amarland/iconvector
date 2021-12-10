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

package com.amarland.iconvector.android

import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import com.amarland.iconvector.lib.AbstractRadialGradientDelegate
import com.amarland.iconvector.lib.RadialGradientDelegateCreator
import com.amarland.iconvector.lib.RadialGradientDelegateOwner
import android.graphics.Matrix as AndroidMatrix
import android.graphics.RadialGradient as AndroidRadialGradient

internal class RadialGradientDelegate(
    colors: List<Color>,
    stops: List<Float>? = null,
    center: Offset,
    radius: Float,
    tileMode: TileMode = TileMode.Clamp,
    matrix: Matrix
) : AbstractRadialGradientDelegate<Shader>(
    colors,
    stops,
    center,
    radius,
    tileMode,
    matrix
) {

    override fun createShaderInternal(
        colors: List<Color>,
        stops: List<Float>?,
        center: Offset,
        radius: Float,
        tileMode: TileMode,
        matrix: Matrix
    ): Shader {
        val transparentColorCount = countTransparentColors(colors)
        return AndroidRadialGradient(
            center.x,
            center.y,
            radius,
            makeTransparentColors(colors, transparentColorCount),
            makeTransparentStops(stops, colors, transparentColorCount),
            tileMode.toAndroidTileMode()
        ).apply {
            if (!matrix.isIdentity()) {
                setLocalMatrix(AndroidMatrix().apply { setFrom(matrix) })
            }
        }
    }

    override fun asBrush() = ActualRadialGradient(this)

    private companion object {

        // Source: https://cs.android.com/androidx/platform/frameworks/support/+/1ac23550d12fc382ce65f7207c9fd89abe6afa62:compose/ui/ui-graphics/src/androidMain/kotlin/androidx/compose/ui/graphics/AndroidShader.android.kt;l=102
        @JvmStatic
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
        @JvmStatic
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
        @JvmStatic
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
    }
}

@Immutable
internal class ActualRadialGradient(
    override val delegate: RadialGradientDelegate
) : ShaderBrush(), RadialGradientDelegateOwner<Shader> {

    override val intrinsicSize = delegate.intrinsicSize

    override fun createShader(size: Size) = delegate.createShader(size)

    override fun equals(other: Any?) = delegate == other

    override fun hashCode() = delegate.hashCode()

    override fun toString() = delegate.toString()
}

internal object RadialGradientDelegateCreatorImpl : RadialGradientDelegateCreator<Shader> {

    override fun create(
        colors: List<Color>,
        stops: List<Float>?,
        center: Offset,
        radius: Float,
        tileMode: TileMode,
        matrix: Matrix
    ) = RadialGradientDelegate(
        colors,
        stops,
        center,
        radius,
        tileMode,
        matrix
    )
}
