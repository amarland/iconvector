package com.amarland.iconvector.lib

class IconVGIntermediateRepresentation(
    val viewportWidth: Float,
    val viewportHeight: Float,
    val translationX: Float,
    val translationY: Float
) {

    @Suppress("PropertyName")
    internal var _paths = mutableListOf<Path>()
    val paths: List<Path> = _paths

    class Path(val segments: List<Segment>, fill: Fill?) {

        val fill: Fill = fill ?: Fill.Color(0x00000000U)

        class Segment(val command: Command, vararg val arguments: Float)

        sealed interface Fill {

            @JvmInline
            value class Color(val argb: UInt) : Fill {

                override fun toString(): String = argbColorToHexString(argb)
            }

            class LinearGradient(
                val colors: IntArray,
                val stops: FloatArray,
                val startX: Float,
                val startY: Float,
                val endX: Float,
                val endY: Float,
                val tileMode: TileMode
            ) : Fill

            class RadialGradient(
                val colors: IntArray,
                val stops: FloatArray,
                val tileMode: TileMode,
                val matrix: Matrix
            ) : Fill
        }

        @JvmInline
        value class Command(val value: Char) {

            companion object {

                @JvmStatic
                val MOVE_TO = Command('M')

                @JvmStatic
                val LINE_TO = Command('L')

                @JvmStatic
                val CUBIC_TO = Command('C')

                @JvmStatic
                val QUAD_TO = Command('Q')

                @JvmStatic
                val ARC_TO = Command('A')
            }
        }
    }

    @JvmInline
    value class TileMode(val value: Int) {

        override fun toString() =
            when (this) {
                CLAMP -> "clamp"
                MIRROR -> "mirror"
                REPEAT -> "repeat"
                else -> "unknown/invalid"
            }

        companion object {

            @JvmStatic
            val CLAMP = TileMode(0x00400000)

            @JvmStatic
            val MIRROR = TileMode(0x00800000)

            @JvmStatic
            val REPEAT = TileMode(0x00C00000)
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    @JvmInline
    value class Matrix(
        val values: FloatArray =
            floatArrayOf(
                1F, 0F, 0F,
                0F, 1F, 0F,
                0F, 0F, 1F
            )
    ) {

        inline operator fun get(index: Int) = values[index]

        inline operator fun set(index: Int, value: Float) {
            values[index] = value
        }

        fun tryInverse(): Boolean {
            val determinant = values[0] * ((values[4] * values[8]) - (values[5] * values[7])) -
                    values[1] * ((values[3] * values[8]) - (values[5] * values[6])) +
                    values[2] * ((values[3] * values[7]) - (values[4] * values[6]))
            if (determinant == 0F) return false
            val inverse = 1F / determinant
            val ix = inverse * (values[4] * values[8] - values[5] * values[7])
            val iy = inverse * (values[2] * values[7] - values[1] * values[8])
            val iz = inverse * (values[1] * values[5] - values[2] * values[4])
            val jx = inverse * (values[5] * values[6] - values[3] * values[8])
            val jy = inverse * (values[0] * values[8] - values[2] * values[6])
            val jz = inverse * (values[2] * values[3] - values[0] * values[5])
            val kx = inverse * (values[3] * values[7] - values[4] * values[6])
            val ky = inverse * (values[1] * values[6] - values[0] * values[7])
            val kz = inverse * (values[0] * values[4] - values[1] * values[3])
            values[0] = ix
            values[1] = iy
            values[2] = iz
            values[3] = jx
            values[4] = jy
            values[5] = jz
            values[6] = kx
            values[7] = ky
            values[8] = kz
            return true
        }

        override fun toString() =
            buildString {
                for (row in 0..2) {
                    append('(')
                    for (column in 0..2) {
                        append("%.3f".format(values[(row * 3) + column]))
                        if (column < 2)
                            append(' ')
                    }
                    appendLine(')')
                }
            }

        companion object {

            // column-major order
            const val INDEX_SCALE_X = 0
            const val INDEX_SKEW_Y = 1
            const val INDEX_SKEW_X = 3
            const val INDEX_SCALE_Y = 4
            const val INDEX_TRANSLATE_X = 6
            const val INDEX_TRANSLATE_Y = 7
        }
    }
}
