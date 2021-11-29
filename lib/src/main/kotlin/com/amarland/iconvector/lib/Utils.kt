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

import androidx.compose.ui.graphics.Matrix

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

internal val Matrix.determinant: Float
    get() {
        val a00 = this[0, 0]
        val a01 = this[0, 1]
        val a02 = this[0, 2]
        val a03 = this[0, 3]
        val a10 = this[1, 0]
        val a11 = this[1, 1]
        val a12 = this[1, 2]
        val a13 = this[1, 3]
        val a20 = this[2, 0]
        val a21 = this[2, 1]
        val a22 = this[2, 2]
        val a23 = this[2, 3]
        val a30 = this[3, 0]
        val a31 = this[3, 1]
        val a32 = this[3, 2]
        val a33 = this[3, 3]
        val b00 = a00 * a11 - a01 * a10
        val b01 = a00 * a12 - a02 * a10
        val b02 = a00 * a13 - a03 * a10
        val b03 = a01 * a12 - a02 * a11
        val b04 = a01 * a13 - a03 * a11
        val b05 = a02 * a13 - a03 * a12
        val b06 = a20 * a31 - a21 * a30
        val b07 = a20 * a32 - a22 * a30
        val b08 = a20 * a33 - a23 * a30
        val b09 = a21 * a32 - a22 * a31
        val b10 = a21 * a33 - a23 * a31
        val b11 = a22 * a33 - a23 * a32
        return b00 * b11 - b01 * b10 + b02 * b09 + b03 * b08 - b04 * b07 + b05 * b06
    }
