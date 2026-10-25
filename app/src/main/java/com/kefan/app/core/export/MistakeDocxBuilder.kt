package com.kefan.app.core.export

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.kefan.app.core.math.MathRunStyle
import com.kefan.app.core.math.MathSyntax
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

data class DocxQuestion(
    val stemText: String,
    val correctAnswer: String = "",
    val analysis: String = "",
    val imageUri: String? = null
)

/**
 * 生成极简 OOXML .docx：标题 + 题目 + 手写留白 + 可选的图片与答案页。
 * 不依赖 Apache POI，直接拼 WordprocessingML 并打成 zip。
 */
@Singleton
class MistakeDocxBuilder @Inject constructor() {

    suspend fun build(
        context: Context,
        studentName: String,
        questions: List<DocxQuestion>,
        includeAnswers: Boolean,
        oneQuestionPerPage: Boolean,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()

        val images = mutableListOf<DocxImage>()
        val formulaDir = File(context.cacheDir, "docx_formula").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        val body = StringBuilder()
        body.append(
            paragraph(
                context,
                images,
                formulaDir,
                "错题重做 · ${studentName.ifBlank { "学生" }}",
                bold = true,
                sizeHalfPoints = 32,
                align = "center"
            )
        )
        body.append(
            paragraph(
                context,
                images,
                formulaDir,
                "姓名：____________    日期：____________",
                sizeHalfPoints = 21,
                align = "center",
                spacingAfter = 240
            )
        )

        questions.forEachIndexed { index, question ->
            val number = index + 1
            body.append(
                paragraph(
                    context,
                    images,
                    formulaDir,
                    "$number. ${question.stemText.ifBlank { "（题干待补全）" }}",
                    bold = false,
                    sizeHalfPoints = 21,
                    spacingBefore = 160,
                    spacingAfter = 160
                )
            )

            if (!question.imageUri.isNullOrBlank()) {
                val image = readImage(context, question.imageUri, images.size + 1)
                if (image != null) {
                    images += image
                    body.append(imageParagraph(image))
                }
            }

            val blanks = if (oneQuestionPerPage) 9 else 3
            repeat(blanks) {
                body.append(blankParagraph(heightTwips = if (oneQuestionPerPage) 420 else 300))
            }
            if (oneQuestionPerPage && number < questions.size) {
                body.append(pageBreak())
            }
        }

        if (includeAnswers) {
            body.append(pageBreak())
            body.append(
                paragraph(
                    context,
                    images,
                    formulaDir,
                    "参考答案与解析",
                    bold = true,
                    sizeHalfPoints = 30,
                    align = "center",
                    spacingAfter = 240
                )
            )
            questions.forEachIndexed { index, question ->
                val number = index + 1
                val answer = question.correctAnswer.ifBlank { "（本题答案缺失）" }
                body.append(
                    paragraph(
                        context,
                        images,
                        formulaDir,
                        "$number. $answer",
                        bold = true,
                        sizeHalfPoints = 21,
                        spacingBefore = 160,
                        spacingAfter = 80
                    )
                )
                if (question.analysis.isNotBlank()) {
                    body.append(
                        paragraph(
                            context,
                            images,
                            formulaDir,
                            question.analysis,
                            sizeHalfPoints = 21,
                            spacingAfter = 160
                        )
                    )
                }
            }
        }

        val document = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
 xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
 xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
 xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
 xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
<w:body>
${body}
<w:sectPr>
<w:pgSz w:w="11906" w:h="16838"/>
<w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="720" w:footer="720" w:gutter="0"/>
</w:sectPr>
</w:body>
</w:document>"""

        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Default Extension="jpg" ContentType="image/jpeg"/>
<Default Extension="jpeg" ContentType="image/jpeg"/>
<Default Extension="png" ContentType="image/png"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
<Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
</Types>"""

        val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
</Relationships>"""

        val docRels = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
            append("""<Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
            images.forEach { image ->
                append("""<Relationship Id="rId${image.index}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/${image.fileName}"/>""")
            }
            append("</Relationships>")
        }

        val styles = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:docDefaults>
<w:rPrDefault>
<w:rPr>
<w:rFonts w:ascii="宋体" w:eastAsia="宋体" w:hAnsi="宋体"/>
<w:sz w:val="22"/>
<w:szCs w:val="22"/>
</w:rPr>
</w:rPrDefault>
<w:pPrDefault/>
</w:docDefaults>
<w:style w:type="paragraph" w:default="1" w:styleId="Normal">
<w:name w:val="Normal"/>
<w:qFormat/>
</w:style>
</w:styles>"""

        val core = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
 xmlns:dc="http://purl.org/dc/elements/1.1/">
<dc:title>错题重做</dc:title>
<dc:creator>课返</dc:creator>
</cp:coreProperties>"""

        val app = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties">
<Application>课返</Application>
</Properties>"""

        ZipOutputStream(BufferedOutputStream(FileOutputStream(outputFile))).use { zip ->
            fun writeEntry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            writeEntry("[Content_Types].xml", contentTypes)
            writeEntry("_rels/.rels", rootRels)
            writeEntry("word/document.xml", document)
            writeEntry("word/styles.xml", styles)
            writeEntry("word/_rels/document.xml.rels", docRels)
            writeEntry("docProps/core.xml", core)
            writeEntry("docProps/app.xml", app)

            images.forEach { image ->
                zip.putNextEntry(ZipEntry("word/media/${image.fileName}"))
                zip.write(image.bytes)
                zip.closeEntry()
            }
        }

        formulaDir.deleteRecursively()
        outputFile
    }

    private fun paragraph(
        context: Context,
        images: MutableList<DocxImage>,
        formulaDir: File,
        text: String,
        bold: Boolean = false,
        sizeHalfPoints: Int = 22,
        align: String? = null,
        spacingBefore: Int = 0,
        spacingAfter: Int = 120
    ): String {
        val alignment = if (align != null) """<w:jc w:val="$align"/>""" else ""
        val boldTag = if (bold) "<w:b/>" else ""
        val spacing = """<w:spacing w:before="$spacingBefore" w:after="$spacingAfter" w:line="360" w:lineRule="auto"/>"""
        return """<w:p><w:pPr>$spacing$alignment</w:pPr>${runsWithMath(context, images, formulaDir, text, boldTag, sizeHalfPoints)}</w:p>"""
    }

    private fun runsWithMath(
        context: Context,
        images: MutableList<DocxImage>,
        formulaDir: File,
        text: String,
        boldTag: String,
        sizeHalfPoints: Int
    ): String {
        val builder = StringBuilder()
        MathSyntax.splitSegments(text).forEach { segment ->
            if (segment.isFormula) {
                val normalized = MathSyntax.normalizeMath(segment.text)
                if (normalized.supported) {
                    val file = File(formulaDir, "formula${images.size + 1}.png")
                    val rendered = FormulaImageRenderer.render(
                        runs = normalized.runs,
                        textSizePx = sizeHalfPoints * 1.8f,
                        targetFile = file
                    )
                    if (rendered) {
                        val image = readFormulaImage(file, images.size + 1, sizeHalfPoints)
                        if (image != null) {
                            images += image
                            builder.append(imageRun(image))
                            return@forEach
                        }
                    }
                }
                builder.append(runs(segment.text, boldTag, sizeHalfPoints))
            } else {
                builder.append(plainRuns(segment.text, boldTag, sizeHalfPoints))
            }
        }
        return builder.toString()
    }

    /** 普通文字里的上下标用 Word 原生上下标，下划线与花括号按原样保留。 */
    private fun plainRuns(text: String, boldTag: String, sizeHalfPoints: Int): String =
        text.split("\n").joinToString("<w:r><w:br/></w:r>") { line ->
            MathSyntax.normalizePlain(line).runs.joinToString("") { run ->
                val align = when (run.style) {
                    MathRunStyle.NORMAL -> ""
                    MathRunStyle.SUP -> """<w:vertAlign w:val="superscript"/>"""
                    MathRunStyle.SUB -> """<w:vertAlign w:val="subscript"/>"""
                }
                """<w:r><w:rPr><w:rFonts w:ascii="宋体" w:eastAsia="宋体" w:hAnsi="宋体"/>$boldTag<w:sz w:val="$sizeHalfPoints"/><w:szCs w:val="$sizeHalfPoints"/>$align</w:rPr><w:t xml:space="preserve">${escape(run.text)}</w:t></w:r>"""
            }
        }

    private fun runs(text: String, boldTag: String, sizeHalfPoints: Int): String =
        text.split("\n").joinToString("<w:r><w:br/></w:r>") { line ->
            """<w:r><w:rPr><w:rFonts w:ascii="宋体" w:eastAsia="宋体" w:hAnsi="宋体"/>$boldTag<w:sz w:val="$sizeHalfPoints"/><w:szCs w:val="$sizeHalfPoints"/></w:rPr><w:t xml:space="preserve">${escape(line)}</w:t></w:r>"""
        }

    private fun blankParagraph(heightTwips: Int): String =
        """<w:p><w:pPr><w:spacing w:after="$heightTwips" w:line="240" w:lineRule="auto"/></w:pPr></w:p>"""

    private fun pageBreak(): String =
        """<w:p><w:r><w:br w:type="page"/></w:r></w:p>"""

    private fun imageRun(image: DocxImage): String = """<w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0">
<wp:extent cx="${image.widthEmu}" cy="${image.heightEmu}"/>
<wp:docPr id="${image.index}" name="Picture ${image.index}"/>
<a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
<pic:pic>
<pic:nvPicPr><pic:cNvPr id="${image.index}" name="${image.fileName}"/><pic:cNvPicPr/></pic:nvPicPr>
<pic:blipFill><a:blip r:embed="rId${image.index}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>
<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="${image.widthEmu}" cy="${image.heightEmu}"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>
</pic:pic>
</a:graphicData></a:graphic>
</wp:inline></w:drawing></w:r>"""

    private fun imageParagraph(image: DocxImage): String =
        """<w:p><w:pPr><w:jc w:val="center"/><w:spacing w:before="80" w:after="120"/></w:pPr>${imageRun(image)}</w:p>"""

    /** 读取公式 PNG 尺寸，按正文磅值折算 Word 内联尺寸（与五号字 10.5pt 匹配）。 */
    private fun readFormulaImage(file: File, index: Int, sizeHalfPoints: Int): DocxImage? {
        return runCatching {
            if (!file.exists() || file.length() == 0L) return null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            val width = options.outWidth.takeIf { it > 0 } ?: return null
            val height = options.outHeight.takeIf { it > 0 } ?: return null

            val targetHeightEmu = sizeHalfPoints * 12_000.0
            val maxWidthEmu = 5_486_400.0
            var scale = targetHeightEmu / height
            if (width * scale > maxWidthEmu) scale = maxWidthEmu / width
            DocxImage(
                index = index,
                fileName = "image$index.png",
                bytes = file.readBytes(),
                widthEmu = (width * scale).toLong().coerceAtLeast(1L),
                heightEmu = (height * scale).toLong().coerceAtLeast(1L)
            )
        }.getOrNull()
    }

    private fun readImage(context: Context, uriText: String, index: Int): DocxImage? {
        return runCatching {
            val uri = Uri.parse(uriText)
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return null
            if (bytes.isEmpty()) return null
            val mime = context.contentResolver.getType(uri).orEmpty()
            val isPng = mime.contains("png") || uriText.endsWith(".png", ignoreCase = true)
            val ext = if (isPng) "png" else "jpg"

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            val width = options.outWidth.takeIf { it > 0 } ?: 800
            val height = options.outHeight.takeIf { it > 0 } ?: 600

            val maxWidthEmu = 5_486_400.0
            val maxHeightEmu = 3_657_600.0
            val scale = min(maxWidthEmu / width, maxHeightEmu / height)
            DocxImage(
                index = index,
                fileName = "image$index.$ext",
                bytes = bytes,
                widthEmu = (width * scale).toLong(),
                heightEmu = (height * scale).toLong()
            )
        }.getOrNull()
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private data class DocxImage(
        val index: Int,
        val fileName: String,
        val bytes: ByteArray,
        val widthEmu: Long,
        val heightEmu: Long
    )
}
