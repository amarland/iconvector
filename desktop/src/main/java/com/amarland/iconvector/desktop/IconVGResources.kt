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

package com.amarland.iconvector.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import com.amarland.iconvector.lib.FormatException
import com.amarland.iconvector.lib.IconVGMachine
import com.amarland.iconvector.lib.toImageVector
import okio.buffer
import okio.source

@Composable
@ExperimentalUnsignedTypes
fun ImageVector.Companion.iconVGResource(
    resourcePath: String,
    palette: List<Color> = emptyList()
) = remember(resourcePath) { loadIconVGResource(resourcePath, palette) }

@ExperimentalUnsignedTypes
@Throws(FormatException::class)
fun loadIconVGResource(
    resourcePath: String,
    palette: List<Color> = emptyList()
): ImageVector =
    requireNotNull(
        Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
            ?.source()?.buffer()
            ?.use { source ->
                IconVGMachine(
                    source,
                    UIntArray(palette.size) { index -> palette[index].toArgb().toUInt() }
                ).intermediateRepresentation
                    .toImageVector { intermediateRepresentation ->
                        RadialGradient(
                            intermediateRepresentation.colors,
                            intermediateRepresentation.stops,
                            center = Offset.Zero,
                            radius = 1F,
                            intermediateRepresentation.tileMode,
                            intermediateRepresentation.matrix
                        )
                    }
            }
    ) { "Resource $resourcePath not found!" }
