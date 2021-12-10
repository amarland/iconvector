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
import com.amarland.iconvector.lib.AbstractRadialGradientDelegate
import com.amarland.iconvector.lib.RadialGradientDelegateCreator
import com.amarland.iconvector.lib.RadialGradientDelegateOwner
import com.amarland.iconvector.lib.toMatrix33Values
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.GradientStyle
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Shader

@Immutable
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
    ) = Shader.makeRadialGradient(
        center.x,
        center.y,
        radius,
        IntArray(colors.size) { i -> colors[i].toArgb() },
        stops?.toFloatArray(),
        GradientStyle(
            tileMode.toSkiaTileMode(),
            isPremul = true,
            Matrix33(*matrix.toMatrix33Values())
        )
    )

    override fun asBrush() = ActualRadialGradient(this)

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
