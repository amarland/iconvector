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

@file:Suppress(
    "LocalVariableName",
    "ObjectPropertyName",
    "PropertyName",
    "PrivatePropertyName"
)

package com.amarland.iconvector.lib

import okio.Buffer
import okio.BufferedSource
import org.jetbrains.annotations.TestOnly
import com.amarland.iconvector.lib.IconVGIntermediateRepresentation as IR

class FormatException(message: String) : RuntimeException(message)

/*
 * The class below is a rather direct translation of:
 * https://github.com/google/iconvg/blob/e5239c1a78325368d6d8eca4757bdee916c3e564/src/dart/lib/decoder.dart#L151
 * However, instead of drawing directly to a canvas, operations are "recorded" and stored
 * as an instance of the `ImageVector` class.
 */

@ExperimentalUnsignedTypes
// @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
class IconVGMachine {

    private val source: BufferedSource
    private var cursor = 0

    // neither set nor used by test instances
    lateinit var intermediateRepresentation: IR private set

    private var minX = -32F
    private var maxX = 32F
    private var minY = -32F
    private var maxY = 32F

    // @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal val customPalette = UIntArray(CREG_LENGTH) { 0x000000FFU }

    // @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal var foundPalette = false

    // @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal val CREG = UIntArray(CREG_LENGTH) { 0x000000FFU }
    private val NREG = FloatArray(NREG_LENGTH)

    private val viewportHeight: Float

    private var CSEL = 0
    private var NSEL = 0

    private var LOD0 = 0F
    private var LOD1 = Float.POSITIVE_INFINITY

    constructor(
        source: BufferedSource,
        palette: UIntArray? = null
    ) {
        require(palette == null || palette.size <= CREG_LENGTH) {
            "`palette` cannot store more than $CREG_LENGTH colors."
        }

        this.source = source

        checkSignature()

        palette?.takeUnless { it.isEmpty() }?.let(::applyCustomPalette)

        readMetadata()

        if (foundPalette) {
            customPalette.forEachIndexed { index, color -> CREG[index] = color }
        }

        val viewportWidth = maxX - minX
        viewportHeight = maxY - minY
        intermediateRepresentation = IR(
            viewportWidth,
            viewportHeight,
            translationX = if (minX < 0) -minX else 0F,
            translationY = if (minY < 0) -minY else 0F
        )

        execute()
    }

    // used by tests only (through specific "factory" function)
    private constructor(bytes: ByteArray, palette: UIntArray) {
        this.source = Buffer().write(bytes)
        viewportHeight = 0F
        applyCustomPalette(palette)
        customPalette.forEachIndexed { index, color ->
            CREG[index] = color
        }
    }

    private fun nextByte() =
        if (!source.exhausted()) {
            source.readByte().toUByte().also { cursor++ }
        } else throw FormatException("Unexpected end of file at offset $cursor.")

    private fun rgbaToGradient(color: UInt): IR.Path.Fill {
        val colorAsInt = color.toInt()
        var NSTOPS = colorAsInt shr 24 and 0x3F
        val CBASE = colorAsInt shr 16 and 0x3F
        val tileMode = colorAsInt and 0x00C00000
        val NBASE = colorAsInt shr 8 and 0x3F
        val isRadial = colorAsInt and 0x00004000 == 0x00004000
        // "color stops" in IconVG
        val rawColors = IntArray(NSTOPS) { index -> CREG[(CBASE + index) % CREG_LENGTH].toInt() }
        // "offset stops" in IconVG
        val stops = FloatArray(NSTOPS) { index -> NREG[(NBASE + index) % NREG_LENGTH] }

        // IconVG uses pre-multiplied alpha, but gradients use straight alpha.
        //
        // When the alpha channel is non-zero, rgbaToColor un-multiplies the color
        // correctly, but when alpha is zero, there's no way for it to know what the
        // color actually should be. So here we replace fully-transparent colors
        // with straight-alpha alternatives. rgbaToColor (used below to generate the
        // list of Colors) does not re-multiply colors that have been affected by
        // this code.
        //
        // As part of this we sometimes have to double-up any fully-transparent
        // stops since the colors on either side may be different.
        var index = 0
        while (index < NSTOPS) {
            if (rawColors[index].toUInt() == 0x00000000U) {
                if (index > 0) {
                    rawColors[index] = (rawColors[index - 1].toUInt() and 0xFFFFFF00U).toInt()
                }
                if (index < NSTOPS - 1) {
                    val nextColor = rawColors[index + 1].toUInt() and 0xFFFFFF00U
                    if (nextColor != rawColors[index].toUInt()) {
                        rawColors.insert(index + 1, nextColor.toInt())
                        stops.insert(index + 1, stops[index])
                        index++
                        NSTOPS++
                    } else {
                        rawColors[index] = nextColor.toInt()
                    }
                }
            }
            index++
        }

        val a = NREG[(NBASE - 6) % NREG_LENGTH]
        val b = NREG[(NBASE - 5) % NREG_LENGTH]
        val c = NREG[(NBASE - 4) % NREG_LENGTH]
        val d = NREG[(NBASE - 3) % NREG_LENGTH]
        val e = NREG[(NBASE - 2) % NREG_LENGTH]
        val f = NREG[(NBASE - 1) % NREG_LENGTH]

        if (isRadial) {
            val matrix = IR.Matrix().apply {
                this[IR.Matrix.INDEX_SCALE_X] = a
                this[IR.Matrix.INDEX_SKEW_Y] = d
                this[IR.Matrix.INDEX_SKEW_X] = b
                this[IR.Matrix.INDEX_SCALE_Y] = e
                this[IR.Matrix.INDEX_TRANSLATE_X] = c
                this[IR.Matrix.INDEX_TRANSLATE_Y] = f
                if (!tryInverse()) {
                    this[IR.Matrix.INDEX_SCALE_X] = 0F
                    this[IR.Matrix.INDEX_SKEW_Y] = 0F
                    this[IR.Matrix.INDEX_SKEW_X] = 0F
                    this[IR.Matrix.INDEX_SCALE_Y] = 0F
                }
            }
            return IR.Path.Fill.RadialGradient(
                colors = IntArray(NSTOPS) { i -> rgbaToArgb(rawColors[i].toUInt()).toInt() },
                stops,
                IR.TileMode(tileMode),
                matrix
            )
        }

        val x1: Float
        val y1: Float
        val dx: Float
        val dy: Float
        if (a != 0F || b != 0F) {
            x1 = if (a != 0F) -c / a else 0F
            y1 = if (b != 0F) -c / b else 0F
            dx = a / (a * a + b * b)
            dy = b / (b * b + a * a)
        } else {
            x1 = -1e9F
            y1 = -1e9F
            dx = 2e9F
            dy = 2e9F
        }
        return IR.Path.Fill.LinearGradient(
            colors = IntArray(NSTOPS) { i -> rgbaToArgb(rawColors[i].toUInt()).toInt() },
            stops,
            startX = x1,
            startY = y1,
            endX = x1 + dx,
            endY = y1 + dy,
            IR.TileMode(tileMode)
        )
    }

    // @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun decodeColor1(): UInt {
        val byte1 = nextByte().toUInt()
        if (byte1 >= 192U) {
            return CREG[(byte1 - 192U).toInt() % CREG_LENGTH]
        }
        if (byte1 >= 128U) {
            return customPalette[(byte1 - 128U).toInt() % CREG_LENGTH]
        }
        when (byte1) {
            127U -> return 0x00000000U
            126U -> return 0x80808080U
            125U -> return 0xC0C0C0C0U
        }
        val blue = byte1 % 5U
        val remainder = (byte1 - blue) / 5U
        val green = remainder % 5U
        val red = (remainder - green) / 5U % 5U
        return ((BYTE1_DECODER_RING[red.toInt()] shl 24)
                + (BYTE1_DECODER_RING[green.toInt()] shl 16)
                + (BYTE1_DECODER_RING[blue.toInt()] shl 8)
                + 0xFFU)
    }

    // @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun decodeColor2(): UInt {
        val byte1 = nextByte().toUInt()
        val byte2 = nextByte().toUInt()
        return ((byte1 and 0xF0U) * 0x1100000U
                + (byte1 and 0x0FU) * 0x110000U
                + (byte2 and 0xF0U) * 0x110U
                + (byte2 and 0x0FU) * 0x11U)
    }

    // @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun decodeDirectColor3() =
        ((nextByte().toUInt() shl 24)
                + (nextByte().toUInt() shl 16)
                + (nextByte().toUInt() shl 8)
                + 0xFFU)

    // @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun decodeIndirectColor3(): UInt {
        val byte1 = nextByte().toUInt()
        val C0 = decodeColor1()
        val C1 = decodeColor1()
        val red = (((255U - byte1) * ((C0 and 0xFF000000U) shr 24))
                + (byte1 * ((C1 and 0xFF000000U) shr 24)) + 128U) / 255U
        val green = (((255U - byte1) * ((C0 and 0x00FF0000U) shr 16))
                + (byte1 * ((C1 and 0x00FF0000U) shr 16)) + 128U) / 255U
        val blue = (((255U - byte1) * ((C0 and 0x0000FF00U) shr 8))
                + (byte1 * ((C1 and 0x0000FF00U) shr 8)) + 128U) / 255U
        val alpha = (((255U - byte1) * (C0 and 0x000000FFU))
                + (byte1 * (C1 and 0x000000FFU)) + 128U) / 255U
        return (red shl 24) + (green shl 16) + (blue shl 8) + alpha
    }

    // @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun decodeColor4() =
        ((nextByte().toUInt() shl 24)
                + (nextByte().toUInt() shl 16)
                + (nextByte().toUInt() shl 8)
                + nextByte().toUInt())

    // @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun decodeNaturalNumber(): Int {
        val byte1 = nextByte().toInt()
        if (byte1 and 0x01 == 0x00) {
            // 1-byte encoding
            return byte1 shr 1
        }
        val byte2 = nextByte().toInt()
        if (byte1 and 0x02 == 0x00) {
            // 2-byte encoding
            return (byte1 shr 2) + (byte2 shl 6)
        }
        // 4-byte encoding
        val byte3 = nextByte().toInt()
        val byte4 = nextByte().toInt()
        return (byte1 shr 2) + (byte2 shl 6) + (byte3 shl 14) + (byte4 shl 22)
    }

    // @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun decodeRealNumber(): Float {
        val byte1 = nextByte().toInt()
        if (byte1 and 0x01 == 0x00) {
            // 1-byte encoding (same as decodeNaturalNumber)
            return (byte1 shr 1).toFloat()
        }
        val byte2 = nextByte().toInt()
        if (byte1 and 0x02 == 0x00) {
            // 2-byte encoding (same as decodeNaturalNumber)
            return ((byte1 shr 2) + (byte2 shl 6)).toFloat()
        }
        // 4-byte encoding (decodeNaturalNumber << 2, cast to float)
        val byte3 = nextByte().toInt()
        val byte4 = nextByte().toInt()
        return Float.fromBits((byte1 and 0xFC) + (byte2 shl 8) + (byte3 shl 16) + (byte4 shl 24))
    }

    // @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun decodeCoordinateNumber(): Float {
        val byte1 = nextByte().toInt()
        if (byte1 and 0x01 == 0x00) {
            // 1-byte encoding (decodeRealNumber with a bias)
            return (byte1 shr 1) - 64F
        }
        val byte2 = nextByte().toInt()
        if (byte1 and 0x02 == 0x00) {
            // 2-byte encoding (decodeRealNumber with a scale and a bias)
            return ((byte1 shr 2) + (byte2 shl 6)) / 64F - 128F
        }
        // 4-byte encoding (same as decodeRealNumber)
        val byte3 = nextByte().toInt()
        val byte4 = nextByte().toInt()
        return Float.fromBits((byte1 and 0xFC) + (byte2 shl 8) + (byte3 shl 16) + (byte4 shl 24))
    }

    // @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun decodeZeroToOneNumber(): Float {
        val byte1 = nextByte().toInt()
        if (byte1 and 0x01 == 0x00) {
            // 1-byte encoding (decodeRealNumber with a bias)
            return (byte1 shr 1) / 120F
        }
        val byte2 = nextByte().toInt()
        if (byte1 and 0x02 == 0x00) {
            // 2-byte encoding (decodeRealNumber with a scale and a bias)
            return ((byte1 shr 2) + (byte2 shl 6)) / 15120F
        }
        // 4-byte encoding (same as decodeRealNumber)
        val byte3 = nextByte().toInt()
        val byte4 = nextByte().toInt()
        return Float.fromBits((byte1 and 0xFC) + (byte2 shl 8) + (byte3 shl 16) + (byte4 shl 24))
    }

    private fun checkSignature() {
        if (nextByte().toUInt() != 0x89U ||
            nextByte().toUInt() != 0x49U ||
            nextByte().toUInt() != 0x56U ||
            nextByte().toUInt() != 0x47U
        ) {
            throw FormatException("Signature did not match IconVG signature.")
        }
    }

    private fun applyCustomPalette(palette: UIntArray) {
        palette.forEachIndexed { index, color ->
            customPalette[index] = argbToRgba(color).takeUnless(::isNonColor) ?: 0x000000FFU
        }
        foundPalette = true
    }

    private fun readMetadata() {
        val count = decodeNaturalNumber()
        var lastMID = -1
        for (index in 0 until count) {
            val blockLength = decodeNaturalNumber()
            val blockEnd = cursor + blockLength
            val MID = decodeNaturalNumber()
            if (MID < lastMID) {
                throw FormatException("Metadata blocks out of order ($MID followed $lastMID).")
            }
            if (MID == lastMID) {
                throw FormatException("Duplicate metadata block with ID $MID.")
            }
            lastMID = MID
            when (MID) {
                // ViewBox
                0 -> {
                    minX = decodeCoordinateNumber().takeIf(Float::isFinite)
                        ?: throw FormatException("ViewBox minX must be finite, not $minX.")
                    minY = decodeCoordinateNumber().takeIf(Float::isFinite)
                        ?: throw FormatException("ViewBox minY must be finite, not $minY.")
                    maxX = decodeCoordinateNumber().takeIf(Float::isFinite)
                        ?: throw FormatException("ViewBox maxX must be finite, not $maxX.")
                    maxY = decodeCoordinateNumber().takeIf(Float::isFinite)
                        ?: throw FormatException("ViewBox maxY must be finite, not $maxY.")
                    if (minX > maxX) {
                        throw FormatException(
                            "ViewBox minX ($minX) must not be bigger than maxX ($maxX)."
                        )
                    }
                    if (minY > maxY) {
                        throw FormatException(
                            "ViewBox minY ($minY) must not be bigger than maxY ($maxY)."
                        )
                    }
                }
                // suggested palette
                1 -> {
                    val atLeastOneByte = nextByte().toInt()
                    val N = atLeastOneByte and 0x3F // low six bits
                    val newPalette = when (N and 0xC0) {
                        // 1-byte colors
                        0x00 -> UIntArray(N + 1) { decodeColor1() }
                        // 2-byte colors
                        0x40 -> UIntArray(N + 1) { decodeColor2() }
                        // 3-byte (direct) colors
                        0x80 -> UIntArray(N + 1) { decodeDirectColor3() }
                        // 4-byte colors
                        0xC0 -> UIntArray(N + 1) { decodeColor4() }
                        else -> throw IllegalStateException("unreachable")
                    }
                    if (!foundPalette) {
                        System.arraycopy(newPalette, 0, customPalette, 0, newPalette.size)
                        foundPalette = true
                    }
                }
            }
            source.skip((blockEnd - cursor).toLong())
            cursor = blockEnd
        }
    }

    private fun execute() {
        // x,y is the current point; cx,cy is the old control point
        var x = 0F
        var y = 0F
        var cx = 0F
        var cy = 0F
        val pathSegments = mutableListOf<IR.Path.Segment>()
        var fill: IR.Path.Fill? = null
        var mode = RenderingMode.STYLING
        var lastOpcode = DrawingCommand.OTHER
        while (!source.exhausted()) {
            val opcode = nextByte().toInt()
            when (mode) {
                RenderingMode.STYLING -> {
                    when {
                        opcode <= 0x3F -> CSEL = opcode and 0x3F
                        opcode <= 0x7F -> NSEL = opcode and 0x3F
                        opcode <= 0x86 -> {
                            CREG[(CSEL - (opcode and 0x07)) % CREG_LENGTH] = decodeColor1()
                        }
                        opcode <= 0x87 -> {
                            CREG[CSEL % CREG_LENGTH] = decodeColor1()
                            CSEL++
                        }
                        opcode <= 0x8E -> {
                            CREG[(CSEL - (opcode and 0x07)) % CREG_LENGTH] = decodeColor2()
                        }
                        opcode <= 0x8F -> {
                            CREG[CSEL % CREG_LENGTH] = decodeColor2()
                            CSEL++
                        }
                        opcode <= 0x96 -> {
                            CREG[(CSEL - (opcode and 0x07)) % CREG_LENGTH] = decodeDirectColor3()
                        }
                        opcode <= 0x97 -> {
                            CREG[CSEL % CREG_LENGTH] = decodeDirectColor3()
                            CSEL++
                        }
                        opcode <= 0x9E -> {
                            CREG[(CSEL - (opcode and 0x07)) % CREG_LENGTH] = decodeColor4()
                        }
                        opcode <= 0x9F -> {
                            CREG[CSEL % CREG_LENGTH] = decodeColor4()
                            CSEL++
                        }
                        opcode <= 0xA6 -> {
                            CREG[(CSEL - (opcode and 0x07)) % CREG_LENGTH] = decodeIndirectColor3()
                        }
                        opcode <= 0xA7 -> {
                            CREG[CSEL % CREG_LENGTH] = decodeIndirectColor3()
                            CSEL++
                        }
                        opcode <= 0xAE -> {
                            NREG[(NSEL - (opcode and 0x07)) % CREG_LENGTH] = decodeRealNumber()
                        }
                        opcode <= 0xAF -> {
                            NREG[NSEL % NREG_LENGTH] = decodeRealNumber()
                            NSEL++
                        }
                        opcode <= 0xB6 -> {
                            NREG[(NSEL - (opcode and 0x07)) % CREG_LENGTH] =
                                decodeCoordinateNumber()
                        }
                        opcode <= 0xB7 -> {
                            NREG[NSEL % NREG_LENGTH] = decodeCoordinateNumber()
                            NSEL++
                        }
                        opcode <= 0xBE -> {
                            NREG[(NSEL - (opcode and 0x07)) % CREG_LENGTH] = decodeZeroToOneNumber()
                        }
                        opcode <= 0xBF -> {
                            NREG[NSEL % NREG_LENGTH] = decodeZeroToOneNumber()
                            NSEL++
                        }
                        opcode <= 0xC6 -> {
                            mode = RenderingMode.DRAWING
                            x = decodeCoordinateNumber()
                            y = decodeCoordinateNumber()
                            pathSegments.run {
                                clear()
                                add(IR.Path.Segment(IR.Path.Command.MOVE_TO, x, y))
                            }
                            lastOpcode = DrawingCommand.OTHER
                            val color = CREG[(CSEL - (opcode and 0x07)) % CREG_LENGTH]
                            fill = if (isGradient(color)) {
                                rgbaToGradient(color)
                            } else {
                                IR.Path.Fill.Color(rgbaToArgb(color))
                            }
                        }
                        opcode <= 0xC7 -> {
                            LOD0 = decodeRealNumber()
                            LOD1 = decodeRealNumber()
                        }
                        else -> throw FormatException("Unexpected reserved opcode $opcode.")
                    }
                }
                RenderingMode.DRAWING -> {
                    val RC = (if (opcode <= 0x3F) opcode and 0x1F else opcode and 0x0F) + 1
                    when {
                        opcode <= 0x3F -> {
                            val absolute = opcode <= 0x1F
                            for (repeat in 0 until RC) {
                                if (absolute) {
                                    x = decodeCoordinateNumber()
                                    y = decodeCoordinateNumber()
                                } else {
                                    x += decodeCoordinateNumber()
                                    y += decodeCoordinateNumber()
                                }
                                pathSegments.add(IR.Path.Segment(IR.Path.Command.LINE_TO, x, y))
                            }
                            lastOpcode = DrawingCommand.OTHER
                        }
                        opcode <= 0x5F -> {
                            if (lastOpcode != DrawingCommand.QUADRATIC) {
                                cx = x
                                cy = y
                            }
                            val absolute = opcode <= 0x4F
                            for (repeat in 0 until RC) {
                                cx = 2 * x - cx
                                cy = 2 * y - cy
                                if (absolute) {
                                    x = decodeCoordinateNumber()
                                    y = decodeCoordinateNumber()
                                } else {
                                    x += decodeCoordinateNumber()
                                    y += decodeCoordinateNumber()
                                }
                                pathSegments.add(
                                    IR.Path.Segment(IR.Path.Command.QUAD_TO, cx, cy, x, y)
                                )
                            }
                            lastOpcode = DrawingCommand.QUADRATIC
                        }
                        opcode <= 0x7F -> {
                            val absolute = opcode <= 0x6F
                            for (repeat in 0 until RC) {
                                if (absolute) {
                                    cx = decodeCoordinateNumber()
                                    cy = decodeCoordinateNumber()
                                    x = decodeCoordinateNumber()
                                    y = decodeCoordinateNumber()
                                } else {
                                    cx = x + decodeCoordinateNumber()
                                    cy = y + decodeCoordinateNumber()
                                    x += decodeCoordinateNumber()
                                    y += decodeCoordinateNumber()
                                }
                                pathSegments.add(
                                    IR.Path.Segment(IR.Path.Command.QUAD_TO, cx, cy, x, y)
                                )
                            }
                            lastOpcode = DrawingCommand.QUADRATIC
                        }
                        opcode <= 0x9F -> {
                            if (lastOpcode != DrawingCommand.CUBIC) {
                                cx = x
                                cy = y
                            }
                            val absolute = opcode <= 0x8F
                            for (repeat in 0 until RC) {
                                val cx1 = 2 * x - cx
                                val cy1 = 2 * y - cy
                                if (absolute) {
                                    cx = decodeCoordinateNumber()
                                    cy = decodeCoordinateNumber()
                                    x = decodeCoordinateNumber()
                                    y = decodeCoordinateNumber()
                                } else {
                                    cx = x + decodeCoordinateNumber()
                                    cy = y + decodeCoordinateNumber()
                                    x += decodeCoordinateNumber()
                                    y += decodeCoordinateNumber()
                                }
                                pathSegments.add(
                                    IR.Path.Segment(
                                        IR.Path.Command.CUBIC_TO, cx1, cy1, cx, cy, x, y
                                    )
                                )
                            }
                            lastOpcode = DrawingCommand.CUBIC
                        }
                        opcode <= 0xBF -> {
                            val absolute = opcode <= 0xAF
                            for (repeat in 0 until RC) {
                                val cx1: Float
                                val cy1: Float
                                if (absolute) {
                                    cx1 = decodeCoordinateNumber()
                                    cy1 = decodeCoordinateNumber()
                                    cx = decodeCoordinateNumber()
                                    cy = decodeCoordinateNumber()
                                    x = decodeCoordinateNumber()
                                    y = decodeCoordinateNumber()
                                } else {
                                    cx1 = x + decodeCoordinateNumber()
                                    cy1 = y + decodeCoordinateNumber()
                                    cx = x + decodeCoordinateNumber()
                                    cy = y + decodeCoordinateNumber()
                                    x += decodeCoordinateNumber()
                                    y += decodeCoordinateNumber()
                                }
                                pathSegments.add(
                                    IR.Path.Segment(
                                        IR.Path.Command.CUBIC_TO, cx1, cy1, cx, cy, x, y
                                    )
                                )
                            }
                            lastOpcode = DrawingCommand.CUBIC
                        }
                        opcode <= 0xDF -> {
                            val absolute = opcode <= 0xCF
                            for (repeat in 0 until RC) {
                                val rx = decodeCoordinateNumber()
                                val ry = decodeCoordinateNumber()
                                val angle = decodeZeroToOneNumber() * 360
                                val flags = decodeNaturalNumber()
                                val largeArc = (flags and 0x01).toFloat()
                                val clockwise = (flags and 0x02).toFloat()
                                if (absolute) {
                                    x = decodeCoordinateNumber()
                                    y = decodeCoordinateNumber()
                                } else {
                                    x += decodeCoordinateNumber()
                                    y += decodeCoordinateNumber()
                                }
                                pathSegments.add(
                                    IR.Path.Segment(
                                        IR.Path.Command.ARC_TO,
                                        rx, ry,
                                        angle,
                                        largeArc,
                                        clockwise,
                                        x, y
                                    )
                                )
                            }
                            lastOpcode = DrawingCommand.OTHER
                        }
                        opcode <= 0xE0 ->
                            throw FormatException("Unexpected reserved opcode $opcode.")
                        opcode <= 0xE1 -> {
                            lastOpcode = DrawingCommand.OTHER
                            // TODO: is this check still relevant now that `H` is gone?
                            if (viewportHeight in LOD0..LOD1) {
                                // make an immutable copy of the mutable list
                                val segments = pathSegments.toList()
                                intermediateRepresentation._paths.add(IR.Path(segments, fill))
                            }
                            mode = RenderingMode.STYLING
                            fill = null
                        }
                        opcode <= 0xE3 -> {
                            val absolute = opcode <= 0xE2
                            if (absolute) {
                                x = decodeCoordinateNumber()
                                y = decodeCoordinateNumber()
                            } else {
                                x += decodeCoordinateNumber()
                                y += decodeCoordinateNumber()
                            }
                            pathSegments.add(IR.Path.Segment(IR.Path.Command.MOVE_TO, x, y))
                            lastOpcode = DrawingCommand.OTHER
                        }
                        opcode <= 0xE5 ->
                            throw FormatException("Unexpected reserved opcode $opcode.")
                        opcode <= 0xE7 -> {
                            val absolute = opcode <= 0xE6
                            if (absolute) {
                                x = decodeCoordinateNumber()
                            } else {
                                x += decodeCoordinateNumber()
                            }
                            pathSegments.add(IR.Path.Segment(IR.Path.Command.LINE_TO, x, y))
                            lastOpcode = DrawingCommand.OTHER
                        }
                        opcode <= 0xE9 -> {
                            val absolute = opcode <= 0xE8
                            if (absolute) {
                                y = decodeCoordinateNumber()
                            } else {
                                y += decodeCoordinateNumber()
                            }
                            pathSegments.add(IR.Path.Segment(IR.Path.Command.LINE_TO, x, y))
                            lastOpcode = DrawingCommand.OTHER
                        }
                        else -> throw FormatException("Unexpected reserved opcode $opcode.")
                    }
                }
            }
        }
    }

    companion object {

        @JvmStatic
        @TestOnly
        fun testInstance(bytes: ByteArray, palette: UIntArray) = IconVGMachine(bytes, palette)

        const val CREG_LENGTH = 64
        private const val NREG_LENGTH = 64

        private val BYTE1_DECODER_RING = uintArrayOf(0x00U, 0x40U, 0x80U, 0xC0U, 0xFFU)

        @JvmStatic
        private fun argbToRgba(argb: UInt): UInt {
            val alpha = argb shr 24
            val red = (((argb and 0x00FF0000U) shr 16) * alpha) / 255U
            val green = (((argb and 0x0000FF00U) shr 8) * alpha) / 255U
            val blue = ((argb and 0x000000FFU) * alpha) / 255U
            return (red shl 24) + (green shl 16) + (blue shl 8) + alpha
        }

        @JvmStatic
        private fun rgbaToArgb(rgba: UInt): UInt {
            val alpha = rgba and 0x000000FFU
            if (alpha == 0x00U) {
                // We sometimes (for gradients) add in real color to the zero-alpha colors.
                // These don't need to be un-multiplied further, they're already un-multiplied.
                return (rgba and 0xFFFFFF00U) shr 8
            }
            val red = ((rgba and 0xFF000000U) shr 24) * 255U / alpha
            val green = ((rgba and 0x00FF0000U) shr 16) * 255U / alpha
            val blue = ((rgba and 0x0000FF00U) shr 8) * 255U / alpha
            return (alpha shl 24) + (red shl 16) + (green shl 8) + blue
        }

        @JvmStatic
        private fun isNonColor(color: UInt) =
            (color and 0xFF000000U) shr 24 > (color and 0x000000FFU)
                    || (color and 0x00FF0000U) shr 16 > (color and 0x000000FFU)
                    || (color and 0x0000FF00U) shr 8 > (color and 0x000000FFU)

        @JvmStatic
        private fun isGradient(color: UInt) = color and 0x000080FFU == 0x00008000U
    }

    private enum class RenderingMode { STYLING, DRAWING }
    private enum class DrawingCommand { QUADRATIC, CUBIC, OTHER }
}
