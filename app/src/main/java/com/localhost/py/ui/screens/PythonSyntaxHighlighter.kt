package com.localhost.py.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

class PythonSyntaxHighlighter : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val inputText = text.text
        val annotatedString = buildAnnotatedString {
            append(inputText)
            
            // JetBrains Darcula-like colors
            val keywordColor = Color(0xFFCC7832) // Orange
            val stringColor = Color(0xFF6A8759) // Green
            val commentColor = Color(0xFF808080) // Gray
            val numberColor = Color(0xFF6897BB) // Blue
            val functionColor = Color(0xFFFFC66D) // Yellow
            val builtinColor = Color(0xFF8888C6) // Purple-ish

            val pattern = "(#.*)|(\"(?:\\\\.|[^\"])*\"|'(?:\\\\.|[^'])*')|\\b(and|as|assert|async|await|break|class|continue|def|del|elif|else|except|False|finally|for|from|global|if|import|in|is|lambda|None|nonlocal|not|or|pass|raise|return|True|try|while|with|yield)\\b|\\b(print|len|range|str|int|float|list|dict|set|tuple|open|type|dir|help|input)\\b|\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*(?=\\()|\\b(\\d+(?:\\.\\d+)?)\\b".toRegex()

            pattern.findAll(inputText).forEach { matchResult ->
                val groups = matchResult.groups
                when {
                    groups[1] != null -> addStyle(SpanStyle(color = commentColor), groups[1]!!.range.first, groups[1]!!.range.last + 1)
                    groups[2] != null -> addStyle(SpanStyle(color = stringColor), groups[2]!!.range.first, groups[2]!!.range.last + 1)
                    groups[3] != null -> addStyle(SpanStyle(color = keywordColor), groups[3]!!.range.first, groups[3]!!.range.last + 1)
                    groups[4] != null -> addStyle(SpanStyle(color = builtinColor), groups[4]!!.range.first, groups[4]!!.range.last + 1)
                    groups[5] != null -> addStyle(SpanStyle(color = functionColor), groups[5]!!.range.first, groups[5]!!.range.last + 1)
                    groups[6] != null -> addStyle(SpanStyle(color = numberColor), groups[6]!!.range.first, groups[6]!!.range.last + 1)
                }
            }
        }
        return TransformedText(annotatedString, OffsetMapping.Identity)
    }
}
