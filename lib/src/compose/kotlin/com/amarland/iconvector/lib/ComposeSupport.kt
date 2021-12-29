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

package com.amarland.iconvector.lib

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.TileMode as ComposeTileMode
import com.amarland.iconvector.lib.IconVGIntermediateRepresentation as IR

@ExperimentalUnsignedTypes
fun IR.toImageVector(
    createActualRadialGradient: (IR.Path.Fill.RadialGradient) -> ShaderBrush
) = ImageVector.Builder(
    defaultWidth = viewportWidth.dp,
    defaultHeight = viewportHeight.dp,
    viewportWidth = viewportWidth,
    viewportHeight = viewportHeight
).apply {
    if (translationX != 0F || translationY != 0F) {
        addGroup(translationX = translationX, translationY = translationY)
    }
    for (path in paths) {
        addPath(
            path.segments.map { segment ->
                val args = segment.arguments
                when (segment.command) {
                    IR.Path.Command.MOVE_TO -> PathNode.MoveTo(args[0], args[1])
                    IR.Path.Command.LINE_TO -> PathNode.LineTo(args[0], args[1])
                    IR.Path.Command.CUBIC_TO ->
                        PathNode.CurveTo(
                            args[0], args[1],
                            args[2], args[3],
                            args[4], args[5]
                        )
                    IR.Path.Command.QUAD_TO ->
                        PathNode.QuadTo(
                            args[0], args[1],
                            args[2], args[3]
                        )
                    /* IconVG.Path.Command.ARC_TO */
                    else -> PathNode.ArcTo(
                        args[0], args[1],
                        args[2],
                        args[3] == 1F,
                        args[4] == 1F,
                        args[5], args[6]
                    )
                }
            },
            fill = with(path.fill) {
                when (this) {
                    is IR.Path.Fill.Color -> SolidColor(Color(argb.toLong()))
                    is IR.Path.Fill.LinearGradient ->
                        Brush.linearGradient(
                            colorStops = Array(colors.size) { index ->
                                stops[index] to Color(colors[index])
                            },
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            tileMode = when (tileMode) {
                                IR.TileMode.REPEAT -> ComposeTileMode.Repeated
                                IR.TileMode.MIRROR -> ComposeTileMode.Mirror
                                else -> ComposeTileMode.Clamp
                            }
                        )
                    is IR.Path.Fill.RadialGradient -> createActualRadialGradient(this)
                }
            }
        )
    }
}.build()
