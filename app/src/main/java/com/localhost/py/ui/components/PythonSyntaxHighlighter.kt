package com.localhost.py.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

class PythonSyntaxHighlighter : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(
            highlightPythonCode(text.text),
            OffsetMapping.Identity
        )
    }

    private fun highlightPythonCode(code: String): AnnotatedString {
        val keywords = setOf(
            "def", "class", "import", "from", "return", "if", "elif", "else",
            "while", "for", "in", "try", "except", "finally", "with", "as",
            "lambda", "yield", "pass", "break", "continue", "raise", "assert",
            "async", "await", "global", "nonlocal", "del"
        )
        val builtins = setOf(
            "print", "len", "range", "str", "int", "float", "list", "dict", "set",
            "tuple", "bool", "type", "open", "super", "sum", "min", "max", "enumerate",
            "zip", "map", "filter", "isinstance", "issubclass", "help", "dir", "id"
        )
        val specialConstants = setOf("True", "False", "None", "self", "cls")

        val keywordColor = Color(0xFF569CD6) // VS Code Blue
        val builtinColor = Color(0xFF4EC9B0) // Teal
        val constantColor = Color(0xFF4FC1FF) // Light Blue
        val stringColor = Color(0xFFCE9178) // Orange/Brown
        val commentColor = Color(0xFF6A9955) // Green
        val numberColor = Color(0xFFB5CEA8) // Light Green
        val decoratorColor = Color(0xFFDCDCAA) // Yellowish

        return buildAnnotatedString {
            append(code)

            // 1. Comments (# ...)
            val commentRegex = "(#.*)".toRegex()
            for (match in commentRegex.findAll(code)) {
                addStyle(SpanStyle(color = commentColor), match.range.first, match.range.last + 1)
            }

            // 2. Strings ("..." or '...' or """...""" or '''...''')
            val stringRegex = "(\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''|\"[^\"\\n]*\"|'[^'\\n]*')".toRegex()
            for (match in stringRegex.findAll(code)) {
                addStyle(SpanStyle(color = stringColor), match.range.first, match.range.last + 1)
            }

            // 3. Decorators (@...)
            val decoratorRegex = "(@[a-zA-Z0-9_.]+)".toRegex()
            for (match in decoratorRegex.findAll(code)) {
                addStyle(SpanStyle(color = decoratorColor, fontWeight = FontWeight.SemiBold), match.range.first, match.range.last + 1)
            }

            // 4. Numbers
            val numberRegex = "\\b(\\d+\\.?\\d*|0x[0-9a-fA-F]+)\\b".toRegex()
            for (match in numberRegex.findAll(code)) {
                addStyle(SpanStyle(color = numberColor), match.range.first, match.range.last + 1)
            }

            // 5. Identifiers / Keywords / Builtins
            val wordRegex = "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b".toRegex()
            for (match in wordRegex.findAll(code)) {
                val word = match.value
                val start = match.range.first
                val end = match.range.last + 1
                when {
                    word in keywords -> addStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold), start, end)
                    word in specialConstants -> addStyle(SpanStyle(color = constantColor, fontWeight = FontWeight.Medium), start, end)
                    word in builtins -> addStyle(SpanStyle(color = builtinColor), start, end)
                }
            }
        }
    }
}
