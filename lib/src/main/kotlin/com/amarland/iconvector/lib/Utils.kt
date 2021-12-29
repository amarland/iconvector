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

import kotlin.math.roundToInt
import com.amarland.iconvector.lib.IconVGIntermediateRepresentation as IR

internal fun IntArray.insert(index: Int, value: Int): IntArray {
    require(index in 0..size)

    return IntArray(size + 1).apply {
        val source = this@insert
        val destination = this
        destination[index] = value
        if (index == 0) {
            System.arraycopy(source, 0, destination, 1, source.size)
        } else {
            System.arraycopy(source, 0, destination, 0, index)
            if (index < source.size - 1) {
                System.arraycopy(source, index, destination, index + 1, source.size - index)
            }
        }
    }
}

internal fun FloatArray.insert(index: Int, value: Float): FloatArray {
    require(index in 0..size)

    return FloatArray(size + 1).apply {
        val source = this@insert
        val destination = this
        destination[index] = value
        if (index == 0) {
            System.arraycopy(source, 0, destination, 1, source.size)
        } else {
            System.arraycopy(source, 0, destination, 0, index)
            if (index < source.size - 1) {
                System.arraycopy(source, index, destination, index + 1, source.size - index)
            }
        }
    }
}

fun argbColorToHexString(argb: UInt) =
    '#' + ((((argb shr 24) / 255U) * (argb and 0x00FFFFFFU)).toString(16)
        .padStart(length = 6, padChar = '0'))

fun Iterable<IR.Path.Segment>.toSvgPathDataString(decimalPlaces: Int = Int.MAX_VALUE) =
    buildString {
        joinTo(this, separator = " ") { segment ->
            var index = 0
            segment.arguments.joinToString(
                separator = " ",
                prefix = "${segment.command.value} "
            ) { value ->
                if (segment.command == IR.Path.Command.ARC_TO && (index == 3 || index == 4)) {
                    value.roundToInt().toString()
                } else if (decimalPlaces in 0..5) {
                    "%.${decimalPlaces}f".format(value)
                } else {
                    value.toString()
                }.also { index++ }
            }
        }
    }
