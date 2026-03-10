package com.github.jtdegraaf.jpql2sql.converter.resolver

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMember

/**
 * Resolves and invokes JPA AttributeConverters for field value conversions.
 *
 * Handles @Convert annotations on entity fields and invokes the actual converter
 * to transform JPQL literal values to their database representations.
 *
 * Supports:
 * - Runtime invocation via reflection when converter class is loadable
 * - PSI-based source code analysis as fallback
 * - Boolean, String, Number, and Enum type conversions
 */
class AttributeConverterResolver(private val project: Project?) {

    /**
     * Gets the AttributeConverter PsiClass for a field, if it has @Convert annotation.
     *
     * @param members the field/getter members to check
     * @return the converter PsiClass, or null if no converter
     */
    fun getConverterClass(members: List<PsiMember>): PsiClass? {
        if (project == null || members.isEmpty()) return null

        val annotation = PsiUtils.findAnnotation(members, JpaAnnotations.CONVERT) ?: return null
        return PsiUtils.getAnnotationClassReference(annotation, "converter", project)
    }

    /**
     * Result wrapper for converter invocation.
     * Distinguishes between successful invocation (with nullable result) and invocation failure.
     */
    private sealed class InvocationResult {
        data class Success(val value: Any?) : InvocationResult()
        data object Failure : InvocationResult()
    }

    /**
     * Converts a value using the AttributeConverter.
     * First tries runtime invocation via reflection, then falls back to PSI analysis.
     *
     * @param converterClass the converter PsiClass
     * @param value the value to convert
     * @return the converted value as a SQL literal, or null if conversion fails
     */
    fun convertValue(converterClass: PsiClass, value: Any): String? {
        // Try runtime invocation first
        return when (val result = invokeConverterAtRuntime(converterClass, value)) {
            is InvocationResult.Success -> formatAsSqlLiteral(result.value)
            is InvocationResult.Failure -> extractConversionFromPsi(converterClass, value)
        }
    }

    /**
     * Invokes the converter's convertToDatabaseColumn method at runtime using reflection.
     * Returns Success with the result (which may be null) if invocation succeeds,
     * or Failure if invocation fails.
     */
    private fun invokeConverterAtRuntime(converterPsiClass: PsiClass, value: Any): InvocationResult {
        try {
            val qualifiedName = converterPsiClass.qualifiedName ?: return InvocationResult.Failure

            // Get the current thread's classloader
            val classLoader = Thread.currentThread().contextClassLoader ?: return InvocationResult.Failure

            // Load the converter class
            val converterJavaClass = classLoader.loadClass(qualifiedName)

            // Create an instance of the converter
            val converterInstance = converterJavaClass.getDeclaredConstructor().newInstance()

            // Find and invoke the convertToDatabaseColumn method
            val method = converterJavaClass.methods.find { it.name == "convertToDatabaseColumn" }
                ?: return InvocationResult.Failure

            val result = method.invoke(converterInstance, value)

            return InvocationResult.Success(result)
        } catch (e: Exception) {
            // Reflection failed, will fall back to PSI analysis
            return InvocationResult.Failure
        }
    }

    /**
     * Extracts conversion values from converter's source code using PSI analysis.
     */
    private fun extractConversionFromPsi(converterClass: PsiClass, value: Any): String? {
        val method = converterClass.findMethodsByName("convertToDatabaseColumn", false).firstOrNull()
            ?: return null

        val body = method.body?.text ?: return null

        return when (value) {
            is Boolean -> extractBooleanConversion(body, value)
            is String -> extractStringConversion(body, value)
            is Number -> extractNumberConversion(body, value)
            is Enum<*> -> extractEnumConversion(body, value)
            else -> null
        }
    }

    /**
     * Formats a value as a SQL literal.
     */
    private fun formatAsSqlLiteral(result: Any?): String {
        return when (result) {
            null -> "NULL"
            is String -> "'${result.replace("'", "''")}'"
            is Number -> result.toString()
            is Boolean -> if (result) "TRUE" else "FALSE"
            is Enum<*> -> "'${result.name}'"
            else -> "'$result'"
        }
    }

    /**
     * Extracts boolean conversion from source code.
     * Handles patterns like:
     * - `attribute ? "Y" : "N"` (string result)
     * - `attribute ? 1 : 0` (numeric result)
     * - `if (attribute) return "Y"; return "N";`
     * - `attribute != null && attribute ? "Y" : "N"`
     */
    private fun extractBooleanConversion(body: String, value: Boolean): String? {
        // Pattern: attribute ? "trueVal" : "falseVal" (string literals)
        val ternaryStringPattern = Regex(
            """(?:attribute|attr|value|input|[a-z])\s*\?\s*["']([^"']+)["']\s*:\s*["']([^"']+)["']"""
        )
        ternaryStringPattern.find(body)?.let { match ->
            val trueVal = match.groupValues[1]
            val falseVal = match.groupValues[2]
            return if (value) "'$trueVal'" else "'$falseVal'"
        }

        // Pattern: attribute ? 1 : 0 (numeric literals)
        val ternaryNumericPattern = Regex(
            """(?:attribute|attr|value|input|[a-z])\s*\?\s*(\d+)\s*:\s*(\d+)"""
        )
        ternaryNumericPattern.find(body)?.let { match ->
            val trueVal = match.groupValues[1]
            val falseVal = match.groupValues[2]
            return if (value) trueVal else falseVal
        }

        // Pattern: attribute != null && attribute ? "trueVal" : "falseVal" (string)
        val nullSafeStringPattern = Regex(
            """!=\s*null\s*&&\s*(?:attribute|attr|value|input|[a-z])\s*\?\s*["']([^"']+)["']\s*:\s*["']([^"']+)["']"""
        )
        nullSafeStringPattern.find(body)?.let { match ->
            val trueVal = match.groupValues[1]
            val falseVal = match.groupValues[2]
            return if (value) "'$trueVal'" else "'$falseVal'"
        }

        // Pattern: attribute != null && attribute ? 1 : 0 (numeric)
        val nullSafeNumericPattern = Regex(
            """!=\s*null\s*&&\s*(?:attribute|attr|value|input|[a-z])\s*\?\s*(\d+)\s*:\s*(\d+)"""
        )
        nullSafeNumericPattern.find(body)?.let { match ->
            val trueVal = match.groupValues[1]
            val falseVal = match.groupValues[2]
            return if (value) trueVal else falseVal
        }

        // Pattern: if (...) { return "trueVal"; } return "falseVal" (string)
        // Handles: if (attribute), if (attribute != null && attribute), etc.
        val ifStringPattern = Regex("""if\s*\([^)]*\)\s*\{?\s*return\s*["']([^"']+)["']""")
        val ifStringMatch = ifStringPattern.find(body)
        if (ifStringMatch != null) {
            val trueVal = ifStringMatch.groupValues[1]
            // Find return statement after the if block (handles }return and standalone return)
            val elseStringPattern = Regex("""(?:}\s*return|;\s*return|else\s*\{?\s*return)\s*["']([^"']+)["']""")
            val elseMatch = elseStringPattern.find(body.substring(ifStringMatch.range.last))
            if (elseMatch != null) {
                val falseVal = elseMatch.groupValues[1]
                return if (value) "'$trueVal'" else "'$falseVal'"
            }
            // Also check for return at start of next line (after closing brace)
            val returnAfterBlockPattern = Regex("""return\s*["']([^"']+)["']""")
            val returnAfterMatch = returnAfterBlockPattern.find(body.substring(ifStringMatch.range.last))
            if (returnAfterMatch != null) {
                val falseVal = returnAfterMatch.groupValues[1]
                return if (value) "'$trueVal'" else "'$falseVal'"
            }
        }

        // Pattern: if (...) { return 1; } return 0; (numeric)
        val ifNumericPattern = Regex("""if\s*\([^)]*\)\s*\{?\s*return\s*(\d+)""")
        val ifNumericMatch = ifNumericPattern.find(body)
        if (ifNumericMatch != null) {
            val trueVal = ifNumericMatch.groupValues[1]
            val elseNumericPattern = Regex("""(?:}\s*return|;\s*return|else\s*\{?\s*return)\s*(\d+)""")
            val elseMatch = elseNumericPattern.find(body.substring(ifNumericMatch.range.last))
            if (elseMatch != null) {
                val falseVal = elseMatch.groupValues[1]
                return if (value) trueVal else falseVal
            }
            // Also check for return at start of next line
            val returnAfterBlockPattern = Regex("""return\s*(\d+)""")
            val returnAfterMatch = returnAfterBlockPattern.find(body.substring(ifNumericMatch.range.last))
            if (returnAfterMatch != null) {
                val falseVal = returnAfterMatch.groupValues[1]
                return if (value) trueVal else falseVal
            }
        }

        return null
    }

    /**
     * Extracts string conversion from source code.
     * Handles switch/case patterns and simple pass-through.
     */
    private fun extractStringConversion(body: String, value: String): String? {
        // Pattern: case "VALUE": return "RESULT"
        val casePattern = Regex(
            """case\s*["']${Regex.escape(value)}["']\s*[:-]?\s*(?:return\s*)?["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        casePattern.find(body)?.let { match ->
            return "'${match.groupValues[1]}'"
        }

        // Simple pass-through converter
        if (body.contains("return attribute") || body.contains("return value")) {
            return "'${value.replace("'", "''")}'"
        }

        return null
    }

    /**
     * Extracts number conversion from source code.
     */
    private fun extractNumberConversion(body: String, value: Number): String? {
        // Simple pass-through
        if (body.contains("return attribute") || body.contains("return value")) {
            return value.toString()
        }

        return null
    }

    /**
     * Extracts enum conversion from source code.
     * Handles switch/case patterns and common enum methods.
     */
    private fun extractEnumConversion(body: String, value: Enum<*>): String? {
        val enumName = value.name

        // Pattern: case ENUM_VALUE: return "RESULT"
        val casePattern = Regex(
            """case\s*(?:\w+\.)?$enumName\s*[:-]?\s*(?:return\s*)?["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        casePattern.find(body)?.let { match ->
            return "'${match.groupValues[1]}'"
        }

        // Pattern: attribute.name()
        if (body.contains(".name()")) {
            return "'$enumName'"
        }

        // Pattern: attribute.ordinal()
        if (body.contains(".ordinal()")) {
            return value.ordinal.toString()
        }

        return null
    }
}
