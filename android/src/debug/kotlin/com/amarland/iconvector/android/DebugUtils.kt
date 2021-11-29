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

package com.amarland.iconvector.android

import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible

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
        indent(mutableLevel++)
        appendLine("path:")
        indent(mutableLevel++)
        append("fill:")
        val brush = fill
        if (brush == null) {
            append(" none")
        } else {
            if (brush is SolidColor) {
                append(' ').appendLine(brush.value.toHexString())
            } else {
                appendLine()
                if (brush is LinearGradient) {
                    writeLinearGradientTo(this, brush, mutableLevel++)
                } else if (brush is RadialGradient) {
                    writeRadialGradientTo(this, brush, mutableLevel++)
                }
            }
        }
        indent(mutableLevel--)
        append("pathData:")
        pathData.joinTo(this, separator = " ", prefix = " ") { node ->
            val (letter, values) = when (node) {
                is PathNode.MoveTo ->
                    'M' to PathNode.MoveTo::class.declaredMemberPropertyValues(node)
                is PathNode.LineTo ->
                    'L' to PathNode.LineTo::class.declaredMemberPropertyValues(node)
                is PathNode.QuadTo ->
                    'Q' to PathNode.QuadTo::class.declaredMemberPropertyValues(node)
                is PathNode.CurveTo ->
                    'C' to PathNode.CurveTo::class.declaredMemberPropertyValues(node)
                is PathNode.ArcTo ->
                    'A' to PathNode.ArcTo::class.declaredMemberPropertyValues(node)
                else -> return@joinTo ""
            }
            values.joinTo(this, separator = " ", prefix = "$letter ") { value ->
                if (value is Boolean) {
                    if (value) "1" else "0"
                } else value.toString()
            }
        }
    }
}

private fun StringBuilder.indent(level: Int) = append(CharArray(level * 2) { ' ' })

private fun Color.toHexString() = toArgb().toUInt().toString(16)

private fun writeLinearGradientTo(sb: StringBuilder, linearGradient: LinearGradient, level: Int) {
    writeGradientTo(sb, linearGradient, level)
}

private fun writeRadialGradientTo(sb: StringBuilder, radialGradient: RadialGradient, level: Int) {
    writeGradientTo(sb, radialGradient, level)
}

private inline fun <reified B : Any> writeGradientTo(sb: StringBuilder, brush: B, level: Int) {
    for (property in B::class.declaredMemberProperties) {
        with(sb) {
            indent(level)
            append("${property.name} = ")
            val value = property.apply { isAccessible = true }.get(brush)
            if (value is Iterable<*> && value.first() as? Color != null) {
                value.joinTo(this) { element -> (element as Color).toHexString() }
                appendLine()
            } else appendLine(value)
        }
    }
}

private fun <T : Any> KClass<T>.declaredMemberPropertyValues(receiver: T) =
    declaredMemberProperties.map { property -> property.get(receiver) }
