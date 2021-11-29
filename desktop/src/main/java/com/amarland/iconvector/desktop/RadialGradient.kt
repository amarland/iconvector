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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import com.amarland.iconvector.lib.AbstractRadialGradient
import com.amarland.iconvector.lib.RadialGradientCreator
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.GradientStyle
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Shader

@Immutable
internal class ActualRadialGradient(
    colors: List<Color>,
    stops: List<Float>? = null,
    center: Offset,
    radius: Float,
    tileMode: TileMode = TileMode.Clamp,
    matrix: Matrix
) : AbstractRadialGradient<Shader>(
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
    ) = Shader.makeRadialGradient(
        center.x,
        center.y,
        radius,
        IntArray(colors.size) { i -> colors[i].toArgb() },
        stops?.toFloatArray(),
        GradientStyle(
            tileMode.toSkiaTileMode(),
            isPremul = true,
            Matrix33(
                matrix[0, 0], matrix[0, 1], matrix[0, 3],
                matrix[1, 0], matrix[1, 1], matrix[1, 3],
                matrix[3, 0], matrix[3, 1], matrix[3, 3]
            )
        )
    )

    override fun asBrush() = object : ShaderBrush() {

        override val intrinsicSize: Size
            get() = this@ActualRadialGradient.intrinsicSize

        override fun createShader(size: Size) = this@ActualRadialGradient.createShader(size)
    }

    companion object {

        @JvmStatic
        private fun TileMode.toSkiaTileMode() =
            when (this) {
                TileMode.Repeated -> FilterTileMode.REPEAT
                TileMode.Mirror -> FilterTileMode.MIRROR
                TileMode.Decal -> FilterTileMode.DECAL
                else -> FilterTileMode.CLAMP
            }
    }
}

internal object RadialGradientCreatorImpl : RadialGradientCreator<Shader> {

    override fun create(
        colors: List<Color>,
        stops: List<Float>?,
        center: Offset,
        radius: Float,
        tileMode: TileMode,
        matrix: Matrix
    ) = ActualRadialGradient(
        colors,
        stops,
        center,
        radius,
        tileMode,
        matrix
    )
}
