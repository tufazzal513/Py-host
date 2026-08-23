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

            val keywords = "\\b(and|as|assert|async|await|break|class|continue|def|del|elif|else|except|False|finally|for|from|global|if|import|in|is|lambda|None|nonlocal|not|or|pass|raise|return|True|try|while|with|yield)\\b".toRegex()
            val builtins = "\\b(print|len|range|str|int|float|list|dict|set|tuple|open|type|dir|help|input)\\b".toRegex()
            val strings = "\".*?\"|'.*?'".toRegex()
            val comments = "#.*".toRegex()
            val numbers = "\\b\\d+(\\.\\d+)?\\b".toRegex()
            val functions = "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*(?=\\()".toRegex()

            // Apply functions first
            functions.findAll(inputText).forEach {
                if (!keywords.matches(it.groupValues[1])) {
                    addStyle(SpanStyle(color = functionColor), it.range.first, it.range.last + 1)
                }
            }
            
            // Apply built-ins
            builtins.findAll(inputText).forEach {
                addStyle(SpanStyle(color = builtinColor), it.range.first, it.range.last + 1)
            }
            
            // Apply numbers
            numbers.findAll(inputText).forEach {
                addStyle(SpanStyle(color = numberColor), it.range.first, it.range.last + 1)
            }

            // Apply keywords
            keywords.findAll(inputText).forEach {
                addStyle(SpanStyle(color = keywordColor), it.range.first, it.range.last + 1)
            }

            // Apply strings
            strings.findAll(inputText).forEach {
                addStyle(SpanStyle(color = stringColor), it.range.first, it.range.last + 1)
            }

            // Apply comments (last so they override content inside)
            comments.findAll(inputText).forEach {
                addStyle(SpanStyle(color = commentColor), it.range.first, it.range.last + 1)
            }
        }
        return TransformedText(annotatedString, OffsetMapping.Identity)
    }
}
