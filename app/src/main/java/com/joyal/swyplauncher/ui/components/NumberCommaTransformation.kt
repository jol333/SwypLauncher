package com.joyal.swyplauncher.ui.components

import android.icu.number.NumberFormatter
import android.icu.text.DecimalFormatSymbols
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.util.Locale

internal class NumberCommaTransformation(locale: Locale) : VisualTransformation {
    private val symbols = DecimalFormatSymbols.getInstance(locale)
    private val decimalSeparator = symbols.decimalSeparator
    private val groupingSeparator = symbols.groupingSeparator
    private val formatter = NumberFormatter.withLocale(locale)
        .grouping(NumberFormatter.GroupingStrategy.ON_ALIGNED)

    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        if (original.isEmpty() || original == "-") return TransformedText(
            text,
            OffsetMapping.Identity
        )

        // Split by standard dot since our internal state always uses dot.
        val parts = original.split(".")
        val intPart = parts[0]
        val decPart = if (parts.size > 1) decimalSeparator.toString() + parts[1] else ""

        val isNegative = intPart.startsWith("-")
        val digitsOnly = if (isNegative) intPart.substring(1) else intPart

        val formattedInt = if (digitsOnly.isNotEmpty()) {
            val num = digitsOnly.toLongOrNull()
            if (num != null) {
                val formatted = formatter.format(num).toString()
                if (isNegative) "-$formatted" else formatted
            } else {
                intPart
            }
        } else {
            if (isNegative) "-" else ""
        }

        val formattedResult = formattedInt + decPart

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= (if (isNegative) 1 else 0)) return offset
                val intLen = intPart.length
                if (offset <= intLen) {
                    val originalSub = intPart.substring(0, offset)
                    val transformedSub = formattedInt.takeWhile { it != decimalSeparator }
                    var mappedOffset = 0
                    var digitCount = 0
                    val targetDigits = if (isNegative) offset - 1 else offset
                    
                    if (isNegative) mappedOffset++
                    
                    for (i in (if (isNegative) 1 else 0) until transformedSub.length) {
                        if (digitCount == targetDigits) break
                        mappedOffset++
                        if (transformedSub[i] != groupingSeparator) {
                            digitCount++
                        }
                    }
                    return mappedOffset
                } else {
                    return formattedInt.length + (offset - intLen)
                }
            }

            override fun transformedToOriginal(offset: Int): Int {
                var originalOffset = 0
                for (i in 0 until offset) {
                    if (i < formattedResult.length && formattedResult[i] != groupingSeparator) {
                        originalOffset++
                    }
                }
                return originalOffset
            }
        }

        return TransformedText(AnnotatedString(formattedResult), offsetMapping)
    }
}
