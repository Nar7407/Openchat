package com.example.util

sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock
    data class BulletItem(val text: String) : MarkdownBlock
    data class NumberedItem(val number: String, val text: String) : MarkdownBlock
    data class Blockquote(val text: String) : MarkdownBlock
}

object MarkdownParser {

    fun parse(markdown: String): List<MarkdownBlock> {
        if (markdown.isBlank()) return emptyList()
        val lines = markdown.replace("\r\n", "\n").split("\n")
        val blocks = mutableListOf<MarkdownBlock>()
        var inCodeFence = false
        var codeLang = ""
        val codeBuffer = StringBuilder()
        val paragraphBuffer = StringBuilder()

        fun flushParagraph() {
            val content = paragraphBuffer.toString().trim()
            if (content.isNotEmpty()) {
                blocks += MarkdownBlock.Paragraph(content)
            }
            paragraphBuffer.clear()
        }

        for (line in lines) {
            val trimmedStart = line.trimStart()
            if (trimmedStart.startsWith("```")) {
                if (!inCodeFence) {
                    flushParagraph()
                    inCodeFence = true
                    codeLang = trimmedStart.removePrefix("```").trim()
                    codeBuffer.clear()
                } else {
                    blocks += MarkdownBlock.CodeBlock(
                        language = codeLang,
                        code = codeBuffer.toString().trimEnd('\n')
                    )
                    inCodeFence = false
                    codeLang = ""
                    codeBuffer.clear()
                }
                continue
            }

            if (inCodeFence) {
                if (codeBuffer.isNotEmpty()) codeBuffer.append('\n')
                codeBuffer.append(line)
                continue
            }

            if (line.isBlank()) {
                flushParagraph()
                continue
            }

            val headingMatch = Regex("""^(#{1,4})\s+(.+)$""").find(trimmedStart)
            if (headingMatch != null) {
                flushParagraph()
                blocks += MarkdownBlock.Heading(
                    level = headingMatch.groupValues[1].length,
                    text = headingMatch.groupValues[2].trim()
                )
                continue
            }

            if (trimmedStart.startsWith("> ")) {
                flushParagraph()
                blocks += MarkdownBlock.Blockquote(trimmedStart.removePrefix("> ").trim())
                continue
            }

            if (trimmedStart.startsWith("- ") || trimmedStart.startsWith("* ")) {
                flushParagraph()
                blocks += MarkdownBlock.BulletItem(trimmedStart.drop(2).trim())
                continue
            }

            val numberedMatch = Regex("""^(\d+\.)\s+(.+)$""").find(trimmedStart)
            if (numberedMatch != null) {
                flushParagraph()
                blocks += MarkdownBlock.NumberedItem(
                    number = numberedMatch.groupValues[1],
                    text = numberedMatch.groupValues[2].trim()
                )
                continue
            }

            if (paragraphBuffer.isNotEmpty()) paragraphBuffer.append(' ')
            paragraphBuffer.append(line.trim())
        }

        if (inCodeFence && codeBuffer.isNotEmpty()) {
            // Render partially streamed code blocks cleanly without flicker
            blocks += MarkdownBlock.CodeBlock(
                language = codeLang,
                code = codeBuffer.toString().trimEnd('\n')
            )
        }
        flushParagraph()
        return blocks
    }
}
