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

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.*
import com.amarland.iconvector.lib.argbColorToHexString

internal fun ImageVector.asString() =
    buildString {
        appendLine('\u200B')
        for (property in arrayOf(
            ImageVector::defaultWidth,
            ImageVector::defaultHeight,
            ImageVector::viewportWidth,
            ImageVector::viewportHeight
        )) {
            appendLine("${property.name} = ${property.get(this@asString)}")
        }
        this@asString.root.writeTo(this, level = 1)
    }

private fun VectorGroup.writeTo(sb: StringBuilder, level: Int) {
    var mutableLevel = level

    with(sb) {
        indent(mutableLevel++)
        appendLine("group:")
        val translationX = VectorGroup::translationX.get(this@writeTo)
        val translationY = VectorGroup::translationY.get(this@writeTo)
        if (translationX != DefaultTranslationX || translationY != DefaultTranslationY) {
            indent(mutableLevel++)
            append("translation: ")
            appendLine("$translationX, $translationY")
        }
    }
    for (node in this) {
        when (node) {
            is VectorGroup -> node.writeTo(sb, mutableLevel)
            is VectorPath -> node.writeTo(sb, mutableLevel)
        }
    }
}

private fun VectorPath.writeTo(sb: StringBuilder, level: Int) {
    var mutableLevel = level

    with(sb) {
        indent(mutableLevel)
        appendLine("path:")
        indent(++mutableLevel)
        append("fill:")
        val brush = fill
        if (brush == null) {
            append(" none")
        } else {
            if (brush is SolidColor) {
                append(' ').appendLine(argbColorToHexString(brush.value.toArgb().toUInt()))
            } else {
                appendLine()
                appendLine(
                    brush.toString().prependIndent(
                        String(CharArray((mutableLevel + 1) * 2) { ' ' })
                    )
                )
            }
        }
        indent(mutableLevel)
        appendLine("pathData: ${pathData.toSvgPathDataString(decimalPlaces = 2)}")
    }
}

private fun StringBuilder.indent(level: Int) = append(CharArray(level * 2) { ' ' })

private fun Iterable<PathNode>.toSvgPathDataString(decimalPlaces: Int = Int.MAX_VALUE) =
    buildString {
        joinTo(this, separator = " ") { node ->
            val (letter, values) = when (node) {
                is PathNode.MoveTo ->
                    'M' to PathNode.MoveTo::class.java.declaredFieldValues(node)
                is PathNode.LineTo ->
                    'L' to PathNode.LineTo::class.java.declaredFieldValues(node)
                is PathNode.QuadTo ->
                    'Q' to PathNode.QuadTo::class.java.declaredFieldValues(node)
                is PathNode.CurveTo ->
                    'C' to PathNode.CurveTo::class.java.declaredFieldValues(node)
                is PathNode.ArcTo ->
                    'A' to PathNode.ArcTo::class.java.declaredFieldValues(node)
                else -> return@joinTo ""
            }
            values.joinToString(separator = " ", prefix = "$letter ") { value ->
                if (value is Boolean) {
                    if (value) "1" else "0"
                } else if (decimalPlaces in 0..5) {
                    "%.${decimalPlaces}f".format(value as Float)
                } else {
                    (value as Float).toString()
                }
            }
        }
    }

private fun <T> Class<T>.declaredFieldValues(receiver: T) =
    declaredFields.map { it.also { it.isAccessible = true }.get(receiver) }
