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

package com.amarland.iconvector.androidcompose

import android.content.res.Resources
import android.util.Log
import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.amarland.iconvector.lib.FormatException
import com.amarland.iconvector.lib.IconVGMachine
import com.amarland.iconvector.lib.toImageVector
import okio.buffer
import okio.source

@Composable
@ExperimentalUnsignedTypes
fun ImageVector.Companion.iconVGResource(
    @RawRes id: Int,
    palette: List<Color> = emptyList()
) = with(LocalContext.current) {
    remember(id) {
        loadIconVGResource(resources, id, palette)
    }
}

@ExperimentalUnsignedTypes
@Throws(FormatException::class)
fun loadIconVGResource(
    resources: Resources,
    id: Int,
    palette: List<Color> = emptyList()
): ImageVector =
    resources.openRawResource(id)
        .source().buffer()
        .use { source ->
            IconVGMachine(
                source,
                UIntArray(palette.size) { index -> palette[index].toArgb().toUInt() }
            ).intermediateRepresentation
                .toImageVector { expectation ->
                    RadialGradient(
                        expectation.colors,
                        expectation.stops,
                        center = Offset.Zero,
                        radius = 1F,
                        expectation.tileMode,
                        expectation.matrix
                    )
                }.also { imageVector: ImageVector ->
                    if (BuildConfig.DEBUG) {
                        Log.d("loadIconVGResource", imageVector.asString())
                    }
                }
        }
