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

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class UtilsTest {

    @ParameterizedTest
    @MethodSource("provideArgumentsForIntArray")
    fun `Int is inserted at the correct position and subsequent elements have been shifted`(
        originalArray: IntArray,
        element: Int,
        index: Int,
        expectedNewArray: IntArray
    ) {
        val actualNewArray = originalArray.insert(index, element)
        assertTrue(expectedNewArray.contentEquals(actualNewArray))
    }

    @ParameterizedTest
    @MethodSource("provideArgumentsForFloatArray")
    fun `Float is inserted at the correct position and subsequent elements have been shifted`(
        originalArray: FloatArray,
        element: Float,
        index: Int,
        expectedNewArray: FloatArray
    ) {
        val actualNewArray = originalArray.insert(index, element)
        assertTrue(expectedNewArray.contentEquals(actualNewArray))
    }

    companion object {

        @JvmStatic
        @Suppress("unused")
        fun provideArgumentsForIntArray() =
            arrayOf(
                Arguments.of(intArrayOf(1, 2, 3), 0, 0, intArrayOf(0, 1, 2, 3)),
                Arguments.of(intArrayOf(0, 2, 3), 1, 1, intArrayOf(0, 1, 2, 3)),
                Arguments.of(intArrayOf(0, 1, 2), 3, 3, intArrayOf(0, 1, 2, 3))
            )

        @JvmStatic
        @Suppress("unused")
        fun provideArgumentsForFloatArray() =
            arrayOf(
                Arguments.of(floatArrayOf(1F, 2F, 3F), 0F, 0, floatArrayOf(0F, 1F, 2F, 3F)),
                Arguments.of(floatArrayOf(0F, 2F, 3F), 1F, 1, floatArrayOf(0F, 1F, 2F, 3F)),
                Arguments.of(floatArrayOf(0F, 1F, 2F), 3F, 3, floatArrayOf(0F, 1F, 2F, 3F))
            )
    }
}
