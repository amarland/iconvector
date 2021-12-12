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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import okio.Buffer
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
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.w3c.dom.Element
import org.w3c.dom.svg.SVGDocument
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt

class IconVGTest {

    @ParameterizedTest
    @MethodSource("provideInvalidByteSequences")
    fun creationFromSimpleInvalidByteSequencesThrows(bytes: ByteArray) {
        Assertions.assertThrowsExactly(FormatException::class.java) {
            IconVGMachine(
                source = Buffer().write(bytes),
                radialGradientDelegateCreator = RADIAL_GRADIENT_DELEGATE_CREATOR
            )
        }
    }

    @Test
    fun blankFileCreationDoesNotThrow() {
        Assertions.assertDoesNotThrow {
            IconVGMachine(
                source = Buffer().write(
                    byteArrayOf(0x89U.toByte(), 0x49, 0x56, 0x47, 0x00)
                ),
                radialGradientDelegateCreator = RADIAL_GRADIENT_DELEGATE_CREATOR
            )
        }
    }

    @ParameterizedTest
    @MethodSource("provideGoldenTestingArguments")
    fun compareWithGoldenFiles(
        sourceFile: File,
        goldenSvgDocument: SVGDocument,
        palette: List<Color>
    ) {
        IconVGMachine(
            sourceFile.source().buffer(),
            palette = palette,
            radialGradientDelegateCreator = RADIAL_GRADIENT_DELEGATE_CREATOR
        ).imageVector.assertMatchesGoldenSvgDocument(goldenSvgDocument)
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
                        emptyList(),
                        listOf(Color(0xFFFF9800)),
                        listOf(Color(0xFF009688))
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
                                            color.toHexString()
                                        )
                                }
                            }

                        Arguments.of(sourceFiles[fileName], svgDocument, palette)
                    }
                }.toTypedArray(),

                "cowbell".let { fileName ->
                    Arguments.of(
                        sourceFiles[fileName],
                        goldenFiles[fileName]?.asSvgDocument(),
                        emptyList<Color>()
                    )
                }

                // TODO
            )
        }

        @Suppress("ClassName")
        @JvmStatic
        private val RADIAL_GRADIENT_DELEGATE_CREATOR =
            object : RadialGradientDelegateCreator<Nothing> {

                override fun create(
                    colors: List<Color>, stops: List<Float>?,
                    center: Offset, radius: Float,
                    tileMode: TileMode, matrix: Matrix
                ): AbstractRadialGradientDelegate<Nothing> {

                    class _Delegate(
                        colors: List<Color>, stops: List<Float>?,
                        center: Offset, radius: Float,
                        tileMode: TileMode, matrix: Matrix
                    ) : AbstractRadialGradientDelegate<Nothing>(
                        colors, stops, center, radius, tileMode, matrix
                    ) {

                        override fun createShaderInternal(
                            colors: List<Color>, stops: List<Float>?,
                            center: Offset, radius: Float,
                            tileMode: TileMode, matrix: Matrix
                        ) = throw UnsupportedOperationException()

                        override fun asBrush() = _Brush()

                        inner class _Brush : ShaderBrush(), RadialGradientDelegateOwner<Nothing> {

                            override val delegate: AbstractRadialGradientDelegate<Nothing>
                                get() = this@_Delegate

                            override fun createShader(size: Size) =
                                throw UnsupportedOperationException()
                        }
                    }

                    return _Delegate(colors, stops, center, radius, tileMode, matrix)
                }
            }
    }

    private fun ImageVector.assertMatchesGoldenSvgDocument(goldenSvgDocument: SVGDocument) {
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

        // File(okio.FileSystem.SYSTEM_TEMPORARY_DIRECTORY.toFile(), "actual.png")
        //     .writeBytes(actualImageBytes)

        val expectedImage = ImageIO.read(ByteArrayInputStream(goldenSvgDocument.toPngBytes()))
        val actualImage = ImageIO.read(ByteArrayInputStream(actualImageBytes))

        for (x in 0 until width) {
            for (y in 0 until height) {
                if (expectedImage.getRGB(x, y) != actualImage.getRGB(x, y))
                    fail("The two images are different.")
            }
        }
    }

    private fun createSvgDocument(
        imageVector: ImageVector,
        width: Int,
        height: Int
    ): SVGDocument {
        val nsUri = SVGDOMImplementation.SVG_NAMESPACE_URI

        return (SVGDOMImplementation.getDOMImplementation()
            .createDocument(nsUri, "svg", null) as SVGDocument)
            .apply {
                rootElement.apply {
                    setAttribute(SVGConstants.SVG_WIDTH_ATTRIBUTE, width.toString())
                    setAttribute(SVGConstants.SVG_HEIGHT_ATTRIBUTE, height.toString())
                    setAttribute(
                        SVGConstants.SVG_VIEW_BOX_ATTRIBUTE,
                        "0 0 ${imageVector.viewportWidth} ${imageVector.viewportHeight}"
                    )

                    val defsElement = createElementNS(nsUri, SVGConstants.SVG_DEFS_TAG)
                        .also(this::appendChild)

                    fun createGradientElement(gradient: ShaderBrush): Element {
                        val radialGradientDelegate =
                            (gradient as? RadialGradientDelegateOwner<*>)?.delegate
                        val isRadial = radialGradientDelegate != null
                        return createElementNS(
                            nsUri,
                            if (isRadial) SVGConstants.SVG_RADIAL_GRADIENT_TAG
                            else SVGConstants.SVG_LINEAR_GRADIENT_TAG
                        ).apply {
                            setAttribute(
                                SVGConstants.SVG_ID_ATTRIBUTE,
                                "gradient${defsElement.childNodes.length + 1}"
                            )

                            @Suppress("UNCHECKED_CAST")
                            fun <T> getGradientPropertyValue(name: String) =
                                (if (isRadial) AbstractRadialGradientDelegate::class.java
                                else LinearGradient::class.java)
                                    .getDeclaredField(name).also { it.trySetAccessible() }
                                    .get(if (isRadial) radialGradientDelegate else gradient) as T

                            if (isRadial) {
                                getGradientPropertyValue<FloatArray>("matrix")
                                    .takeUnless { values -> Matrix(values).isIdentity() }
                                    ?.let { matrix ->
                                        setAttribute(
                                            SVGConstants.SVG_GRADIENT_TRANSFORM_ATTRIBUTE,
                                            SVGConstants.TRANSFORM_MATRIX +
                                                    "(${matrix[Matrix.ScaleX]}" +
                                                    " ${matrix[Matrix.SkewY]}" +
                                                    " ${matrix[Matrix.SkewX]}" +
                                                    " ${matrix[Matrix.ScaleY]}" +
                                                    " ${matrix[Matrix.TranslateX]}" +
                                                    " ${matrix[Matrix.TranslateY]})"
                                        )
                                    }
                            }

                            @Suppress("UNCHECKED_CAST")
                            val stops = getGradientPropertyValue<List<Float>>("stops")

                            @Suppress("UNCHECKED_CAST")
                            val colors = getGradientPropertyValue<List<Color>>("colors")
                            for ((stop, color) in stops.zip(colors)) {
                                createElementNS(nsUri, SVGConstants.SVG_STOP_TAG).apply {
                                    setAttribute(
                                        SVGConstants.SVG_OFFSET_ATTRIBUTE,
                                        "${(stop * 100).roundToInt()}%"
                                    )
                                    setAttribute(
                                        SVGConstants.SVG_STOP_COLOR_ATTRIBUTE,
                                        color.toHexString()
                                    )
                                }.also(this::appendChild)
                            }

                            // `Offset` is an inline class, and the functions called to
                            // pack and unpack values are inline as well, so rather than
                            // copying their body, the internal constructor is called instead
                            @Suppress("TestFunctionName")
                            fun Offset(packedValue: Long) =
                                Offset::class.java
                                    .getDeclaredConstructor(Long::class.java)
                                    .also { it.trySetAccessible() }
                                    .newInstance(packedValue)

                            if (isRadial) {
                                val center = Offset(getGradientPropertyValue("center"))
                                setAttribute(SVGConstants.SVG_CX_ATTRIBUTE, "${center.x}")
                                setAttribute(SVGConstants.SVG_CY_ATTRIBUTE, "${center.y}")
                                setAttribute(
                                    SVGConstants.SVG_R_ATTRIBUTE,
                                    getGradientPropertyValue<Float>("radius").toString()
                                )
                            } else {
                                val start = Offset(getGradientPropertyValue("start"))
                                val end = Offset(getGradientPropertyValue("end"))
                                setAttribute(SVGConstants.SVG_X1_ATTRIBUTE, "${start.x}")
                                setAttribute(SVGConstants.SVG_Y1_ATTRIBUTE, "${start.y}")
                                setAttribute(SVGConstants.SVG_X2_ATTRIBUTE, "${end.x}")
                                setAttribute(SVGConstants.SVG_Y2_ATTRIBUTE, "${end.y}")
                            }

                            val tileMode = getGradientPropertyValue<Int>("tileMode")
                            setAttribute(
                                SVGConstants.SVG_SPREAD_METHOD_ATTRIBUTE,
                                when (tileMode) {
                                    1 -> SVGConstants.SVG_REPEAT_VALUE
                                    2 -> SVGConstants.SVG_REFLECT_VALUE
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
                    val transformAttr = (imageVector.root.first() as? VectorGroup)
                        ?.takeIf { it.translationX > 0 || it.translationY > 0 }
                        ?.let {
                            createAttribute(SVGConstants.SVG_TRANSFORM_ATTRIBUTE).apply {
                                value = SVGConstants.TRANSFORM_TRANSLATE +
                                        "(${it.translationX}, ${it.translationY})"
                            }
                        }

                    val paths = imageVector.root.fold(ArrayList<VectorPath>()) { list, node ->
                        if (node is VectorGroup) node.filterIsInstanceTo(list)
                        else list.apply { add(node as VectorPath) }
                    }
                    for (path in paths) {
                        createElementNS(nsUri, SVGConstants.SVG_PATH_TAG).apply {
                            transformAttr?.let(this::setAttributeNode)

                            setAttribute(
                                SVGConstants.SVG_D_ATTRIBUTE,
                                path.pathData.toSvgPathDataString()
                            )

                            path.fill?.let { fill ->
                                val attributeValue = when (fill) {
                                    is SolidColor -> fill.value.toHexString()
                                    is ShaderBrush -> {
                                        val gradient = createGradientElement(fill)
                                            .also(defsElement::appendChild)
                                        val id = gradient
                                            .getAttribute(SVGConstants.SVG_ID_ATTRIBUTE)
                                        "url(#$id)"
                                    }
                                }
                                setAttribute(SVGConstants.SVG_FILL_ATTRIBUTE, attributeValue)
                            }
                        }.also(this::appendChild)
                    }
                }
            }
    }
}
