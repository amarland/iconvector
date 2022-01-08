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

import okio.Buffer
import okio.FileSystem
import okio.buffer
import okio.source
import org.apache.batik.anim.dom.SAXSVGDocumentFactory
import org.apache.batik.anim.dom.SVGDOMImplementation
import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.batik.transcoder.image.PNGTranscoder
import org.apache.batik.util.SVGConstants
import org.apache.batik.util.XMLResourceDescriptor
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.w3c.dom.Element
import org.w3c.dom.svg.SVGDocument
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.management.ManagementFactory
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import com.amarland.iconvector.lib.IconVGIntermediateRepresentation as IR

class IconVGTest {

    @ParameterizedTest
    @MethodSource("provideInvalidByteSequences")
    fun creationFromSimpleInvalidByteSequencesThrows(bytes: ByteArray) {
        Assertions.assertThrowsExactly(FormatException::class.java) {
            IconVGMachine(Buffer().write(bytes))
        }
    }

    @Test
    fun blankFileCreationDoesNotThrow() {
        Assertions.assertDoesNotThrow {
            IconVGMachine(
                Buffer().write(
                    byteArrayOf(0x89U.toByte(), 0x49, 0x56, 0x47, 0x00)
                )
            )
        }
    }

    @ParameterizedTest
    @MethodSource("provideGoldenTestingArguments")
    fun compareWithGoldenFiles(
        sourceFile: File,
        goldenSvgDocument: SVGDocument,
        palette: IntArray // JUnit is Java, it doesn't play nice with unsigned types
    ) {
        IconVGMachine(
            sourceFile.source().buffer(),
            palette = palette.toUIntArray()
        ).intermediateRepresentation.assertMatchesGoldenSvgDocument(goldenSvgDocument)
    }

    companion object {

        @JvmStatic
        @Suppress("unused")
        fun provideInvalidByteSequences(): Array<ByteArray> {
            return arrayOf(
                byteArrayOf(),
                byteArrayOf(0x00),
                byteArrayOf(0x89U.toByte(), 0x49, 0x56),
                byteArrayOf(0x89U.toByte(), 0x49, 0x56, 0x46, 0x00),
            )
        }

        @JvmStatic
        @Suppress("unused")
        fun provideGoldenTestingArguments(): Array<Arguments> {

            fun File.listFilesAsFileNameToFileMap() =
                listFiles()!!.associateBy(File::nameWithoutExtension)

            val resourceDirectories = IconVGTest::class.java.classLoader
                .getResources("").toList()
                .mapNotNull {
                    it.takeIf { url ->
                        url.protocol == "file" && url.path.contains("resources")
                    }?.toURI()?.let(::File)
                }.single()
                .listFiles()!!
            val sourceFiles = resourceDirectories.single { it.path.endsWith("data") }
                .listFilesAsFileNameToFileMap()
            val goldenFiles = resourceDirectories.single { it.path.endsWith("goldens") }
                .listFilesAsFileNameToFileMap()

            fun File.asSvgDocument() = SAXSVGDocumentFactory(
                XMLResourceDescriptor.getXMLParserClassName()
            ).createSVGDocument(null, inputStream())

            return arrayOf(
                // 2 files, each with 3 choices of palette, 6 combinations
                *arrayOf(
                    "action-info.hi-res",
                    "action-info.lo-res"
                ).flatMap { fileName ->
                    arrayOf(
                        uintArrayOf(),
                        uintArrayOf(0xFFFF9800U),
                        uintArrayOf(0xFF009688U)
                    ).map { palette ->
                        val svgDocument = goldenFiles[fileName]
                            ?.asSvgDocument()?.also { document ->
                                palette.singleOrNull()?.let { color ->
                                    // either `getElementsByTagName` doesn't work as expected,
                                    // or I am too stupid to call it properly
                                    (document.rootElement.childNodes
                                        .let { nodes -> Array(nodes.length, nodes::item) }
                                        .single { node ->
                                            (node as? Element)?.tagName == SVGConstants.SVG_PATH_TAG
                                        } as Element)
                                        .setAttributeNS(
                                            null,
                                            SVGConstants.SVG_FILL_ATTRIBUTE,
                                            argbColorToHexString(color)
                                        )
                                }
                            }

                        Arguments.of(sourceFiles[fileName], svgDocument, palette.toIntArray())
                    }
                }.toTypedArray(),

                "cowbell".let { fileName ->
                    Arguments.of(
                        sourceFiles[fileName],
                        goldenFiles[fileName]?.asSvgDocument(),
                        intArrayOf()
                    )
                }

                // TODO
            )
        }
    }

    private fun IR.assertMatchesGoldenSvgDocument(goldenSvgDocument: SVGDocument) {
        val svgElementAttributes = goldenSvgDocument.rootElement.attributes
        val width = svgElementAttributes.getNamedItemNS(
            null, SVGConstants.SVG_WIDTH_ATTRIBUTE
        ).nodeValue.toInt()
        val height = svgElementAttributes.getNamedItemNS(
            null, SVGConstants.SVG_HEIGHT_ATTRIBUTE
        ).nodeValue.toInt()

        val generatedSvgDocument = createSvgDocument(this, width, height)

        /* "Transform" `svgDocument` into a `String`:
        java.io.StringWriter().also {
            javax.xml.transform.TransformerFactory.newDefaultInstance()
                .newTransformer()
                .transform(
                    javax.xml.transform.dom.DOMSource(generatedSvgDocument),
                    javax.xml.transform.stream.StreamResult(it)
                )
        }.toString()
        */

        fun SVGDocument.toPngBytes() = ByteArrayOutputStream().also { stream ->
            PNGTranscoder().transcode(TranscoderInput(this), TranscoderOutput(stream))
        }.toByteArray()

        val actualImageBytes = generatedSvgDocument.toPngBytes()

        val isDebuggerAttached = ManagementFactory.getRuntimeMXBean().inputArguments
            .any { "jdwp=" in it && "suspend=n" !in it }
        if (isDebuggerAttached) {
            File(FileSystem.SYSTEM_TEMPORARY_DIRECTORY.toFile(), "actual.png").run {
                writeBytes(actualImageBytes)
                // add a breakpoint below to preview the temp file
                delete()
            }
        }

        val expectedImage = ImageIO.read(ByteArrayInputStream(goldenSvgDocument.toPngBytes()))
        val actualImage = ImageIO.read(ByteArrayInputStream(actualImageBytes))

        for (x in 0 until width) {
            for (y in 0 until height) {
                Assertions.assertTrue(
                    expectedImage.getRGB(x, y) == actualImage.getRGB(x, y),
                    "The two images are different."
                )
            }
        }
    }

    private fun createSvgDocument(image: IR, width: Int, height: Int): SVGDocument {
        val nsUri = SVGDOMImplementation.SVG_NAMESPACE_URI

        return (SVGDOMImplementation.getDOMImplementation()
            .createDocument(nsUri, "svg", null) as SVGDocument)
            .apply {
                rootElement.apply {
                    setAttribute(SVGConstants.SVG_WIDTH_ATTRIBUTE, width.toString())
                    setAttribute(SVGConstants.SVG_HEIGHT_ATTRIBUTE, height.toString())
                    setAttribute(
                        SVGConstants.SVG_VIEW_BOX_ATTRIBUTE,
                        "0 0 ${image.viewportWidth} ${image.viewportHeight}"
                    )

                    val defsElement = createElementNS(nsUri, SVGConstants.SVG_DEFS_TAG)
                        .also(this::appendChild)

                    fun createGradientElement(gradient: IR.Path.Fill.Gradient): Element {
                        val isRadial = gradient is IR.Path.Fill.RadialGradient
                        return createElementNS(
                            nsUri,
                            if (isRadial) SVGConstants.SVG_RADIAL_GRADIENT_TAG
                            else SVGConstants.SVG_LINEAR_GRADIENT_TAG
                        ).apply {
                            setAttribute(
                                SVGConstants.SVG_ID_ATTRIBUTE,
                                "gradient${defsElement.childNodes.length + 1}"
                            )

                            if (isRadial) {
                                (gradient as IR.Path.Fill.RadialGradient).matrix
                                    .takeUnless {
                                        // it.isIdentity()
                                        for (index in 0..9)
                                            if (it[index] != if (index % 4 == 0) 1F else 0F)
                                                return@takeUnless false
                                        return@takeUnless true
                                    }?.let { matrix ->
                                        setAttribute(
                                            SVGConstants.SVG_GRADIENT_TRANSFORM_ATTRIBUTE,
                                            SVGConstants.TRANSFORM_MATRIX +
                                                    "(${matrix[IR.Matrix.INDEX_SCALE_X]}" +
                                                    " ${matrix[IR.Matrix.INDEX_SKEW_Y]}" +
                                                    " ${matrix[IR.Matrix.INDEX_SKEW_X]}" +
                                                    " ${matrix[IR.Matrix.INDEX_SCALE_Y]}" +
                                                    " ${matrix[IR.Matrix.INDEX_TRANSLATE_X]}" +
                                                    " ${matrix[IR.Matrix.INDEX_TRANSLATE_Y]})"
                                        )
                                    }
                            }

                            val colorStops = gradient.stops.zip(gradient.colors.toUIntArray())
                            for ((stop, color) in colorStops) {
                                createElementNS(nsUri, SVGConstants.SVG_STOP_TAG).apply {
                                    setAttribute(
                                        SVGConstants.SVG_OFFSET_ATTRIBUTE,
                                        "${(stop * 100).roundToInt()}%"
                                    )
                                    setAttribute(
                                        SVGConstants.SVG_STOP_COLOR_ATTRIBUTE,
                                        argbColorToHexString(color)
                                    )
                                }.also(this::appendChild)
                            }

                            if (isRadial) {
                                setAttribute(SVGConstants.SVG_CX_ATTRIBUTE, "0")
                                setAttribute(SVGConstants.SVG_CY_ATTRIBUTE, "0")
                                setAttribute(SVGConstants.SVG_R_ATTRIBUTE, "1")
                            } else {
                                gradient as IR.Path.Fill.LinearGradient
                                setAttribute(SVGConstants.SVG_X1_ATTRIBUTE, "${gradient.startX}")
                                setAttribute(SVGConstants.SVG_Y1_ATTRIBUTE, "${gradient.startY}")
                                setAttribute(SVGConstants.SVG_X2_ATTRIBUTE, "${gradient.endX}")
                                setAttribute(SVGConstants.SVG_Y2_ATTRIBUTE, "${gradient.endY}")
                            }

                            setAttribute(
                                SVGConstants.SVG_SPREAD_METHOD_ATTRIBUTE,
                                when (gradient.tileMode) {
                                    IR.TileMode.REPEAT -> SVGConstants.SVG_REPEAT_VALUE
                                    IR.TileMode.MIRROR -> SVGConstants.SVG_REFLECT_VALUE
                                    else -> SVGConstants.SVG_PAD_VALUE
                                }
                            )
                        }
                    }

                    // if applied to a group (`<g>`) specifically created for this task,
                    // rasterization results in an all-transparent image, although the resulting
                    // SVG document is properly rendered when opened in a browser, for example;
                    // to avoid this, the attribute is created once and will be applied to
                    // all individual paths
                    val transformAttr =
                        if (image.translationX != 0F || image.translationY != 0F)
                            createAttribute(SVGConstants.SVG_TRANSFORM_ATTRIBUTE).apply {
                                value = SVGConstants.TRANSFORM_TRANSLATE +
                                        "(${image.translationX}, ${image.translationY})"
                            }
                        else null

                    for (path in image.paths) {
                        createElementNS(nsUri, SVGConstants.SVG_PATH_TAG).apply {
                            transformAttr?.let(this::setAttributeNode)

                            setAttribute(
                                SVGConstants.SVG_D_ATTRIBUTE,
                                path.segments.toSvgPathDataString()
                            )

                            val attributeValue = when (val fill = path.fill) {
                                is IR.Path.Fill.Color -> fill.toString()
                                else -> {
                                    val gradientElement = createGradientElement(
                                        fill as IR.Path.Fill.Gradient
                                    ).also(defsElement::appendChild)
                                    val id = gradientElement
                                        .getAttribute(SVGConstants.SVG_ID_ATTRIBUTE)
                                    "url(#$id)"
                                }
                            }
                            setAttribute(SVGConstants.SVG_FILL_ATTRIBUTE, attributeValue)
                        }.also(this::appendChild)
                    }
                }
            }
    }
}
