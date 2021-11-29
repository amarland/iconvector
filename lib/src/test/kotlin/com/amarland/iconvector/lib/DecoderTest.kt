// Copyright 2021 The IconVG Authors.
// Copyright 2021 Anthony Marland
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.amarland.iconvector.lib

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.TileMode
import okio.Buffer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

// Source: https://github.com/google/iconvg/blob/e5239c1a78325368d6d8eca4757bdee916c3e564/src/dart/lib/decoder.dart#L66

class DecoderTest {

    @Test
    fun testDecoders() {
        // We create a test machine with a bogus program that just consists of data
        // in a carefully-crafted order so that we can then check the decoders below.
        val bytes = ubyteArrayOf(
            // decodeColor1
            0x00U, 0x18U, 0x30U, 0x56U, 0x7cU, 125U, 126U, 127U,
            128U, 129U, 130U, 191U, 192U, 193U, 194U, 255U,
            // decodeColor2
            0x38U, 0x0FU,
            // decodeDirectColor3
            0x30U, 0x66U, 0x07U,
            // decodeColor4
            0x30U, 0x66U, 0x07U, 0x80U,
            // decodeIndirectColor3
            0x40U, 0x7FU, 0x82U,
            // numbers
            0x28U,
            0x59U, 0x83U,
            0x07U, 0x00U, 0x80U, 0x3FU,
            0x28U,
            0x59U, 0x83U,
            0x07U, 0x00U, 0x80U, 0x3FU,
            0x8EU,
            0x81U, 0x87U,
            0x03U, 0x00U, 0xF0U, 0x40U,
            0x0AU,
            0x41U, 0x1AU,
            0x63U, 0x0BU, 0x36U, 0x3BU
        ).toByteArray()
        val palette = List(IconVGMachine.CREG_LENGTH) { index ->
            Color(0xFF000000 + (2 * index + 1))
        }

        val testMachine = IconVGMachine.testInstance(bytes, palette)

        with(testMachine) {
            assertTrue(foundPalette)
            assertEquals(CREG.size, customPalette.size)

            assertEquals(0x000000FFU, decodeColor1())
            assertEquals(0x00FFFFFFU, decodeColor1())
            assertEquals(0x40FFC0FFU, decodeColor1())
            assertEquals(0xC08040FFU, decodeColor1())
            assertEquals(0xFFFFFFFFU, decodeColor1())
            assertEquals(0xC0C0C0C0U, decodeColor1())
            assertEquals(0x80808080U, decodeColor1())
            assertEquals(0x00000000U, decodeColor1())
            assertEquals(0x000001FFU, decodeColor1())
            assertEquals(0x000003FFU, decodeColor1())
            assertEquals(0x000005FFU, decodeColor1())
            assertEquals(0x00007FFFU, decodeColor1())
            assertEquals(0x000001FFU, decodeColor1())
            assertEquals(0x000003FFU, decodeColor1())
            assertEquals(0x000005FFU, decodeColor1())
            assertEquals(0x00007FFFU, decodeColor1())
            assertEquals(0x338800FFU, decodeColor2())
            assertEquals(0x306607FFU, decodeDirectColor3())
            assertEquals(0x30660780U, decodeColor4())

            customPalette[2] = 0xFF9000FFU // "opaque orange"
            assertEquals(
                0x40240040U, // "25% opaque orange" (pre-multiplied)
                decodeIndirectColor3()
            )
            assertEquals(0x14, decodeNaturalNumber())
            assertEquals(0x20D6, decodeNaturalNumber())
            assertEquals(0xFE00001, decodeNaturalNumber())
            assertEquals(20F, decodeRealNumber())
            assertEquals(8406F, decodeRealNumber())
            assertEquals(1.000000476837158203125F, decodeRealNumber())
            assertEquals(7.0F, decodeCoordinateNumber())
            assertEquals(7.5F, decodeCoordinateNumber())
            assertEquals(7.5F, decodeCoordinateNumber())
            assertEquals(15F / 360F, decodeZeroToOneNumber())
            assertEquals(40F / 360F, decodeZeroToOneNumber())
            assertEquals(0.00277777761220932F /* approx 1.0/360.0 */, decodeZeroToOneNumber())
        }
    }

    @ParameterizedTest
    @MethodSource("provideInvalidByteSequences")
    fun testSimpleInvalidByteSequences(bytes: ByteArray) {
        assertThrowsExactly(FormatException::class.java) {
            IconVGMachine(
                source = Buffer().write(bytes),
                radialGradientCreator = object : RadialGradientCreator<Nothing> {

                    override fun create(
                        colors: List<Color>, stops: List<Float>?,
                        center: Offset, radius: Float,
                        tileMode: TileMode, matrix: Matrix
                    ) = throw UnsupportedOperationException()
                }
            )
        }
    }

    companion object {

        @JvmStatic
        @Suppress("unused")
        fun provideInvalidByteSequences(): Array<ByteArray> {
            val oneHundredAndThirtySeven: UByte = 0x89U // express as UByte as Bytes are signed
            return arrayOf(
                byteArrayOf(),
                byteArrayOf(0x00),
                byteArrayOf(oneHundredAndThirtySeven.toByte(), 0x49, 0x56),
                byteArrayOf(oneHundredAndThirtySeven.toByte(), 0x49, 0x56, 0x46, 0x00),
            )
        }
    }
}
