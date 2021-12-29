/*
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

package com.amarland.iconvector.androidlegacy

import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.PictureDrawable
import androidx.annotation.ColorInt
import androidx.annotation.DimenRes
import androidx.annotation.Dimension
import androidx.annotation.RawRes
import com.amarland.iconvector.lib.IconVGIntermediateRepresentation
import com.amarland.iconvector.lib.IconVGMachine
import okio.buffer
import okio.source
import kotlin.math.roundToInt
import com.amarland.iconvector.lib.IconVGIntermediateRepresentation as IR

@ExperimentalUnsignedTypes
fun Resources.getIconVGDrawable(
    @RawRes id: Int,
    @DimenRes heightRes: Int,
    @ColorInt palette: IntArray = intArrayOf()
) = getIconVGDrawableWithPixelHeight(id, getDimensionPixelSize(heightRes), palette)

@ExperimentalUnsignedTypes
fun Resources.getIconVGDrawableWithPixelHeight(
    @RawRes id: Int,
    @Dimension height: Int,
    @ColorInt palette: IntArray = intArrayOf()
): Drawable =
    openRawResource(id)
        .source().buffer()
        .use { source ->
            PictureDrawable(
                IconVGMachine(source, palette.toUIntArray())
                    .intermediateRepresentation
                    .toPicture(height)
            )
        }

@ExperimentalUnsignedTypes
fun IconVGIntermediateRepresentation.toPicture(@Dimension height: Int): Picture {
    return Picture().apply {
        val width = (height / viewportHeight * viewportWidth).roundToInt()
        beginRecording(width, height).run {
            scale(width / viewportWidth, height / viewportHeight)
            if (translationX != 0F || translationY != 0F) {
                translate(translationX, translationY)
            }

            val pathToDraw = Path()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            for (path in paths) {
                pathToDraw.rewind()

                var currentX = 0F
                var currentY = 0F
                for (segment in path.segments) {
                    val args = segment.arguments
                    when (segment.command) {
                        IR.Path.Command.MOVE_TO -> {
                            pathToDraw.moveTo(args[0], args[1])
                            currentX = args[0]
                            currentY = args[1]
                        }
                        IR.Path.Command.LINE_TO -> {
                            pathToDraw.lineTo(args[0], args[1])
                            currentX = args[0]
                            currentY = args[1]
                        }
                        IR.Path.Command.CUBIC_TO -> {
                            pathToDraw.cubicTo(
                                args[0], args[1],
                                args[2], args[3],
                                args[4], args[5]
                            )
                            currentX = args[4]
                            currentY = args[5]
                        }
                        IR.Path.Command.QUAD_TO -> {
                            pathToDraw.quadTo(
                                args[0], args[1],
                                args[2], args[3]
                            )
                            currentX = args[2]
                            currentY = args[3]
                        }
                        /* IconVG.Path.Command.ARC_TO */
                        else -> {
                            // no method for drawing elliptical arcs
                            EllipticalArcDrawingUtil.drawArc(
                                pathToDraw,
                                currentX, currentY,
                                args[5], args[6],
                                args[0], args[1],
                                args[2],
                                args[3] == 1F,
                                args[4] == 1F
                            )
                            currentX = args[5]
                            currentY = args[6]
                        }
                    }
                }

                fun IR.TileMode.toAndroidTileMode(): Shader.TileMode =
                    when (this) {
                        IR.TileMode.REPEAT -> Shader.TileMode.REPEAT
                        IR.TileMode.MIRROR -> Shader.TileMode.MIRROR
                        else -> Shader.TileMode.CLAMP
                    }

                when (val fill = path.fill) {
                    is IR.Path.Fill.Color -> {
                        paint.color = fill.argb.toInt()
                        paint.shader = null
                    }
                    is IR.Path.Fill.LinearGradient ->
                        paint.shader = LinearGradient(
                            fill.startX, fill.startY,
                            fill.endX, fill.endY,
                            fill.colors,
                            fill.stops,
                            fill.tileMode.toAndroidTileMode()
                        )
                    is IR.Path.Fill.RadialGradient ->
                        paint.shader = RadialGradient(
                            0F, 0F,
                            1F,
                            fill.colors,
                            fill.stops,
                            fill.tileMode.toAndroidTileMode()
                        ).apply {
                            setLocalMatrix(
                                Matrix().apply {
                                    val values = fill.matrix.values
                                    // column-major order -> row-major order
                                    setValues(
                                        floatArrayOf(
                                            values[0], values[3], values[6],
                                            values[1], values[4], values[7],
                                            values[2], values[5], values[8]
                                        )
                                    )
                                }
                            )
                        }
                }
                drawPath(pathToDraw, paint)
            }
        }
        endRecording()
    }
}
