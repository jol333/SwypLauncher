package com.joyal.swyplauncher.util

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.*

object CalculatorUtil {
    
    /**
     * Evaluates a mathematical expression and returns the result as a string.
     * Returns null if the expression is invalid or not a mathematical expression.
     */
    fun evaluate(expression: String): String? {
        if (expression.isBlank()) return null
        
        // Clean the expression for calculation
        val cleaned = cleanExpression(expression)
        val isMath = isMathExpression(cleaned)
        
        return try {
            if (!isMath) return null
            val result = evaluateExpression(cleaned)
            formatResult(result)
        } catch (e: Exception) {
            // If it looked like a math expression but failed (e.g. division by zero), return Error
            // Otherwise if it wasn't really a math expression, return null
            if (isMath) "Error" else null
        }
    }
    
    /**
     * Normalizes the expression for display with proper symbols (× and ÷)
     */
    fun normalizeForDisplay(expression: String): String {
        return expression.trim()
            .replace("*", "×")
            .replace("/", "÷")
            .replace("x", "×")
            .replace("X", "×")
    }
    
    /**
     * Checks if the string looks like a mathematical expression
     */
    private fun isMathExpression(str: String): Boolean {
        // Must contain at least one operator and one digit
        val hasOperator = str.any { it in "+-*/×÷^%()." } || 
            listOf("sqrt", "sin", "cos", "tan", "log", "ln", "abs").any { str.contains(it) }
        val hasDigit = str.any { it.isDigit() }
        
        // Should not contain letters (except for scientific notation 'e' and math constants)
        val hasInvalidChars = str.any { 
            it.isLetter() && it.lowercaseChar() !in listOf('e', 'x', 'π', 'p', 'i', 's', 'q', 'r', 't', 'c', 'o', 'n', 'a', 'l', 'g', 'b')
        }
        
        return hasOperator && hasDigit && !hasInvalidChars
    }
    
    /**
     * Cleans the expression by removing spaces and normalizing operators
     */
    private fun cleanExpression(expr: String): String {
        // Pre-process natural language terms
        var processed = expr.trim().lowercase()
        
        processed = processed
            .replace("square root of", "sqrt")
            .replace("√", "sqrt")
            .replace("percentage of", "%*")
            .replace("percent of", "%*")
            .replace("out of", "/")
            .replace("divided by", "/")
            .replace("multiplied by", "*")
            .replace("times", "*")
            .replace("into", "*")
            .replace("plus", "+")
            .replace("minus", "-")
            .replace("percentage", "%")
            .replace("percent", "%")
            .replace("over", "/")
            
        // specific check for "by" as "divided by" (e.g. "5 by 3")
        // Use regex to ensure we don't break words
        processed = processed.replace(Regex("\\bby\\b"), "/")

        return processed
            .replace(" ", "")
            .replace("×", "*")
            .replace("÷", "/")
            .replace("x", "*")
            .replace("X", "*")
            .replace("π", PI.toString())
            .replace("pi", PI.toString())
    }
    
    /**
     * Evaluates the mathematical expression using a simple recursive descent parser
     */
    private fun evaluateExpression(expr: String): Double {
        return Parser(expr).parse()
    }
    
    /**
     * Formats the result to a readable string with 2 decimal places
     */
    private fun formatResult(value: Double): String {
        return when {
            value.isInfinite() -> "∞"
            value.isNaN() -> "Error"
            value == value.toLong().toDouble() -> value.toLong().toString()
            else -> {
                // Round to 2 decimal places
                val bd = BigDecimal(value).setScale(2, RoundingMode.HALF_UP)
                bd.stripTrailingZeros().toPlainString()
            }
        }
    }
    
    /**
     * Simple recursive descent parser for mathematical expressions
     * Supports: +, -, *, /, ^, %, parentheses, and basic functions
     */
    private class Parser(private val expr: String) {
        private var pos = 0
        
        fun parse(): Double = parseExpression()
        
        private fun parseExpression(): Double {
            var result = parseTerm()
            
            while (pos < expr.length) {
                when (expr[pos]) {
                    '+' -> {
                        pos++
                        result += parseTerm()
                    }
                    '-' -> {
                        pos++
                        result -= parseTerm()
                    }
                    else -> break
                }
            }
            
            return result
        }
        
        private fun parseTerm(): Double {
            var result = parseFactor()
            
            while (pos < expr.length) {
                when (expr[pos]) {
                    '*' -> {
                        pos++
                        result *= parseFactor()
                    }
                    '/' -> {
                        pos++
                        val divisor = parseFactor()
                        // Double division by zero returns Infinity which is handled
                        result /= divisor
                    }
                    else -> break
                }
            }
            
            return result
        }
        
        private fun parseFactor(): Double {
            var result = parsePower()
            
            // Handle percentage after the value
            if (pos < expr.length && expr[pos] == '%') {
                pos++
                result /= 100.0
            }
            
            return result
        }
        
        private fun parsePower(): Double {
            var result = parseUnary()
            
            while (pos < expr.length && expr[pos] == '^') {
                pos++
                result = result.pow(parsePower()) // Right associative
            }
            
            return result
        }
        
        private fun parseUnary(): Double {
            if (pos < expr.length && expr[pos] == '-') {
                pos++
                return -parseUnary()
            }
            if (pos < expr.length && expr[pos] == '+') {
                pos++
                return parseUnary()
            }
            return parsePrimary()
        }
        
        private fun parsePrimary(): Double {
            // Handle parentheses
            if (pos < expr.length && expr[pos] == '(') {
                pos++
                val result = parseExpression()
                if (pos < expr.length && expr[pos] == ')') {
                    pos++
                }
                return result
            }
            
            // Handle functions (sqrt, sin, cos, tan, log, ln, abs)
            if (pos < expr.length && expr[pos].isLetter()) {
                return parseFunction()
            }
            
            // Handle numbers
            return parseNumber()
        }
        
        private fun parseFunction(): Double {
            val start = pos
            while (pos < expr.length && expr[pos].isLetter()) {
                pos++
            }
            val funcName = expr.substring(start, pos).lowercase()
            
            // Skip opening parenthesis
            if (pos < expr.length && expr[pos] == '(') {
                pos++
            }
            
            val arg = parseExpression()
            
            // Skip closing parenthesis
            if (pos < expr.length && expr[pos] == ')') {
                pos++
            }
            
            return when (funcName) {
                "sqrt" -> sqrt(arg)
                "sin" -> sin(Math.toRadians(arg))
                "cos" -> cos(Math.toRadians(arg))
                "tan" -> {
                    // Check for 90, 270, etc.
                    val degrees = arg % 180
                    if (abs(degrees - 90) < 1e-9 || abs(degrees + 90) < 1e-9) {
                        Double.POSITIVE_INFINITY
                    } else {
                        tan(Math.toRadians(arg))
                    }
                }
                "log" -> log10(arg)
                "ln" -> ln(arg)
                "abs" -> abs(arg)
                else -> throw IllegalArgumentException("Unknown function: $funcName")
            }
        }
        
        private fun parseNumber(): Double {
            val start = pos
            
            // Handle negative numbers
            if (pos < expr.length && expr[pos] == '-') {
                pos++
            }
            
            // Parse integer part
            while (pos < expr.length && expr[pos].isDigit()) {
                pos++
            }
            
            // Parse decimal part
            if (pos < expr.length && expr[pos] == '.') {
                pos++
                while (pos < expr.length && expr[pos].isDigit()) {
                    pos++
                }
            }
            
            // Parse scientific notation
            if (pos < expr.length && (expr[pos] == 'e' || expr[pos] == 'E')) {
                pos++
                if (pos < expr.length && (expr[pos] == '+' || expr[pos] == '-')) {
                    pos++
                }
                while (pos < expr.length && expr[pos].isDigit()) {
                    pos++
                }
            }
            
            val numStr = expr.substring(start, pos)
            return numStr.toDoubleOrNull() ?: throw NumberFormatException("Invalid number: $numStr")
        }
    }
}
