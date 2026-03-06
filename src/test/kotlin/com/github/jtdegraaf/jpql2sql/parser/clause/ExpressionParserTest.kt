package com.github.jtdegraaf.jpql2sql.parser.clause

import com.github.jtdegraaf.jpql2sql.parser.*
import org.junit.Assert.*
import org.junit.Test

class ExpressionParserTest {

    private fun parseExpr(fragment: String): Expression {
        val ctx = ParseContext(fragment)
        val expr = ExpressionParser(ctx) { JpqlParser(fragment).parse() }
        return expr.parseExpression()
    }

    private fun parsePath(fragment: String): PathExpression {
        val ctx = ParseContext(fragment)
        val expr = ExpressionParser(ctx) { throw UnsupportedOperationException() }
        return expr.parsePathExpression()
    }

    // ──────────── Path expressions ──────────────────────

    @Test fun testSimplePath() = assertEquals(listOf("u"), parsePath("u").parts)
    @Test fun testDottedPath() = assertEquals(listOf("u", "name"), parsePath("u.name").parts)
    @Test fun testDeepPath() = assertEquals(listOf("u", "address", "city", "name"), parsePath("u.address.city.name").parts)
    @Test fun testPathWithKeywordPart() = assertEquals(listOf("u", "order"), parsePath("u.order").parts)
    @Test fun testPathStartingWithKeyword() = assertEquals(listOf("Order"), parsePath("Order").parts)

    // ──────────── Literals ──────────────────────────────

    @Test fun testStringLiteral() { val lit = parseExpr("'hello'") as LiteralExpression; assertEquals("hello", lit.value); assertEquals(LiteralType.STRING, lit.type) }
    @Test fun testEscapedStringLiteral() { val lit = parseExpr("'it''s'") as LiteralExpression; assertEquals("it's", lit.value) }
    @Test fun testIntegerLiteral() { val lit = parseExpr("42") as LiteralExpression; assertEquals(42L, lit.value); assertEquals(LiteralType.NUMBER, lit.type) }
    @Test fun testZeroLiteral() { val lit = parseExpr("0") as LiteralExpression; assertEquals(0L, lit.value) }
    @Test fun testDecimalLiteral() { val lit = parseExpr("3.14") as LiteralExpression; assertEquals(3.14, lit.value); assertEquals(LiteralType.NUMBER, lit.type) }
    @Test fun testBooleanTrue() { val lit = parseExpr("true") as LiteralExpression; assertEquals(true, lit.value); assertEquals(LiteralType.BOOLEAN, lit.type) }
    @Test fun testBooleanFalse() { val lit = parseExpr("false") as LiteralExpression; assertEquals(false, lit.value) }
    @Test fun testNullLiteral() { val lit = parseExpr("NULL") as LiteralExpression; assertNull(lit.value); assertEquals(LiteralType.NULL, lit.type) }

    // ──────────── Parameters ────────────────────────────

    @Test fun testNamedParameter() { val p = parseExpr(":userId") as ParameterExpression; assertEquals("userId", p.name); assertNull(p.position) }
    @Test fun testPositionalParameter() { val p = parseExpr("?1") as ParameterExpression; assertNull(p.name); assertEquals(1, p.position) }

    // ──────────── Comparison operators ──────────────────

    @Test fun testEquals() = assertEquals(BinaryOperator.EQ, (parseExpr("u.x = 1") as BinaryExpression).operator)
    @Test fun testNotEquals() = assertEquals(BinaryOperator.NE, (parseExpr("u.x <> 1") as BinaryExpression).operator)
    @Test fun testLessThan() = assertEquals(BinaryOperator.LT, (parseExpr("u.x < 1") as BinaryExpression).operator)
    @Test fun testLessOrEqual() = assertEquals(BinaryOperator.LE, (parseExpr("u.x <= 1") as BinaryExpression).operator)
    @Test fun testGreaterThan() = assertEquals(BinaryOperator.GT, (parseExpr("u.x > 1") as BinaryExpression).operator)
    @Test fun testGreaterOrEqual() = assertEquals(BinaryOperator.GE, (parseExpr("u.x >= 1") as BinaryExpression).operator)

    // ──────────── Logical operators ─────────────────────

    @Test fun testAnd() = assertEquals(BinaryOperator.AND, (parseExpr("u.a = 1 AND u.b = 2") as BinaryExpression).operator)
    @Test fun testOr() = assertEquals(BinaryOperator.OR, (parseExpr("u.a = 1 OR u.b = 2") as BinaryExpression).operator)
    @Test fun testNot() = assertEquals(UnaryOperator.NOT, (parseExpr("NOT u.active = true") as UnaryExpression).operator)

    @Test
    fun testAndOrPrecedence() {
        val expr = parseExpr("u.a = 1 OR u.b = 2 AND u.c = 3") as BinaryExpression
        assertEquals(BinaryOperator.OR, expr.operator)
        assertEquals(BinaryOperator.AND, (expr.right as BinaryExpression).operator)
    }

    // ──────────── IS NULL / IS NOT NULL ─────────────────

    @Test fun testIsNull() = assertEquals(BinaryOperator.IS_NULL, (parseExpr("u.deletedAt IS NULL") as BinaryExpression).operator)
    @Test fun testIsNotNull() = assertEquals(BinaryOperator.IS_NOT_NULL, (parseExpr("u.deletedAt IS NOT NULL") as BinaryExpression).operator)

    // ──────────── IN / NOT IN ───────────────────────────

    @Test
    fun testIn() {
        val expr = parseExpr("u.status IN ('A', 'B')") as BinaryExpression
        assertEquals(BinaryOperator.IN, expr.operator)
        assertEquals(2, (expr.right as InListExpression).elements.size)
    }

    @Test fun testNotIn() = assertEquals(BinaryOperator.NOT_IN, (parseExpr("u.status NOT IN ('X')") as BinaryExpression).operator)

    @Test
    fun testInCollectionNamedParam() {
        val expr = parseExpr("u.status IN :statuses") as BinaryExpression
        assertEquals(BinaryOperator.IN, expr.operator)
        val param = expr.right as ParameterExpression
        assertEquals("statuses", param.name)
    }

    @Test
    fun testInCollectionPositionalParam() {
        val expr = parseExpr("u.id IN ?1") as BinaryExpression
        assertEquals(BinaryOperator.IN, expr.operator)
        val param = expr.right as ParameterExpression
        assertEquals(1, param.position)
    }

    @Test
    fun testNotInCollectionParam() {
        val expr = parseExpr("u.status NOT IN :excluded") as BinaryExpression
        assertEquals(BinaryOperator.NOT_IN, expr.operator)
        val param = expr.right as ParameterExpression
        assertEquals("excluded", param.name)
    }

    // ──────────── BETWEEN / NOT BETWEEN ─────────────────

    @Test
    fun testBetween() {
        val expr = parseExpr("u.age BETWEEN 18 AND 65") as BinaryExpression
        assertEquals(BinaryOperator.BETWEEN, expr.operator)
        val between = expr.right as BetweenExpression
        assertEquals(18L, (between.lower as LiteralExpression).value)
        assertEquals(65L, (between.upper as LiteralExpression).value)
    }

    @Test fun testNotBetween() = assertEquals(BinaryOperator.NOT_BETWEEN, (parseExpr("u.age NOT BETWEEN 0 AND 17") as BinaryExpression).operator)

    // ──────────── LIKE / NOT LIKE ───────────────────────

    @Test fun testLike() = assertEquals(BinaryOperator.LIKE, (parseExpr("u.name LIKE '%john%'") as BinaryExpression).operator)
    @Test fun testNotLike() = assertEquals(BinaryOperator.NOT_LIKE, (parseExpr("u.name NOT LIKE '%spam%'") as BinaryExpression).operator)

    // ──────────── MEMBER OF ─────────────────────────────

    @Test fun testMemberOf() = assertEquals(BinaryOperator.MEMBER_OF, (parseExpr("u MEMBER OF g.members") as BinaryExpression).operator)
    @Test fun testNotMemberOf() = assertEquals(BinaryOperator.NOT_MEMBER_OF, (parseExpr("u NOT MEMBER OF g.members") as BinaryExpression).operator)

    // ──────────── Unary minus ───────────────────────────

    @Test fun testUnaryMinus() = assertEquals(UnaryOperator.MINUS, (parseExpr("-u.balance") as UnaryExpression).operator)

    // ──────────── Parenthesized expressions ─────────────

    @Test fun testParenthesized() = assertEquals(BinaryOperator.EQ, (parseExpr("(u.a = 1)") as BinaryExpression).operator)

    // ──────────── Function calls ────────────────────────

    @Test fun testUpper() { val f = parseExpr("UPPER(u.name)") as FunctionCallExpression; assertEquals("UPPER", f.name); assertEquals(1, f.arguments.size) }
    @Test fun testLower() { val f = parseExpr("LOWER(u.name)") as FunctionCallExpression; assertEquals("LOWER", f.name); assertEquals(1, f.arguments.size) }
    @Test fun testConcat() { val f = parseExpr("CONCAT(u.first, ' ', u.last)") as FunctionCallExpression; assertEquals("CONCAT", f.name); assertEquals(3, f.arguments.size) }
    @Test fun testSubstring() { val f = parseExpr("SUBSTRING(u.name, 1, 3)") as FunctionCallExpression; assertEquals("SUBSTRING", f.name); assertEquals(3, f.arguments.size) }
    @Test fun testLength() { val f = parseExpr("LENGTH(u.name)") as FunctionCallExpression; assertEquals("LENGTH", f.name); assertEquals(1, f.arguments.size) }
    @Test fun testTrim() { val t = parseExpr("TRIM(u.name)") as TrimExpression; assertEquals(TrimMode.BOTH, t.mode); assertNull(t.trimCharacter); assertTrue(t.source is PathExpression) }
    @Test fun testAbs() { val f = parseExpr("ABS(u.balance)") as FunctionCallExpression; assertEquals("ABS", f.name); assertEquals(1, f.arguments.size) }
    @Test fun testSqrt() { val f = parseExpr("SQRT(u.value)") as FunctionCallExpression; assertEquals("SQRT", f.name); assertEquals(1, f.arguments.size) }
    @Test fun testMod() { val f = parseExpr("MOD(u.id, 2)") as FunctionCallExpression; assertEquals("MOD", f.name); assertEquals(2, f.arguments.size) }
    @Test fun testCoalesce() { val f = parseExpr("COALESCE(u.nickname, u.name)") as FunctionCallExpression; assertEquals("COALESCE", f.name); assertEquals(2, f.arguments.size) }
    @Test fun testNullif() { val f = parseExpr("NULLIF(u.name, '')") as FunctionCallExpression; assertEquals("NULLIF", f.name); assertEquals(2, f.arguments.size) }

    // ──────────── Aggregate expressions ─────────────────

    @Test fun testCount() { val a = parseExpr("COUNT(u)") as AggregateExpression; assertEquals(AggregateFunction.COUNT, a.function); assertFalse(a.distinct) }
    @Test fun testCountStar() { val a = parseExpr("COUNT(*)") as AggregateExpression; assertEquals(AggregateFunction.COUNT, a.function) }
    @Test fun testCountDistinct() { val a = parseExpr("COUNT(DISTINCT u.dept)") as AggregateExpression; assertTrue(a.distinct) }
    @Test fun testSum() = assertEquals(AggregateFunction.SUM, (parseExpr("SUM(o.amount)") as AggregateExpression).function)
    @Test fun testAvg() = assertEquals(AggregateFunction.AVG, (parseExpr("AVG(o.rating)") as AggregateExpression).function)
    @Test fun testMin() = assertEquals(AggregateFunction.MIN, (parseExpr("MIN(o.price)") as AggregateExpression).function)
    @Test fun testMax() = assertEquals(AggregateFunction.MAX, (parseExpr("MAX(o.price)") as AggregateExpression).function)

    // ──────────── CASE expression ───────────────────────

    @Test
    fun testSimpleCaseExpression() {
        val expr = parseExpr("CASE WHEN u.active = true THEN 'yes' ELSE 'no' END") as CaseExpression
        assertNull(expr.operand)
        assertEquals(1, expr.whenClauses.size)
        assertNotNull(expr.elseExpression)
    }

    @Test
    fun testCaseExpressionMultipleWhen() {
        val expr = parseExpr("CASE WHEN u.role = 'ADMIN' THEN 1 WHEN u.role = 'USER' THEN 2 ELSE 3 END") as CaseExpression
        assertEquals(2, expr.whenClauses.size)
    }

    @Test
    fun testCaseExpressionNoElse() {
        val expr = parseExpr("CASE WHEN u.active = true THEN 'yes' END") as CaseExpression
        assertNull(expr.elseExpression)
    }

    // ──────────── Qualified name ────────────────────────

    @Test
    fun testQualifiedName() {
        val ctx = ParseContext("com.example.UserDto")
        val expr = ExpressionParser(ctx) { throw UnsupportedOperationException() }
        assertEquals("com.example.UserDto", expr.parseQualifiedName())
    }

    @Test
    fun testQualifiedNameSinglePart() {
        val ctx = ParseContext("User")
        val expr = ExpressionParser(ctx) { throw UnsupportedOperationException() }
        assertEquals("User", expr.parseQualifiedName())
    }

    // ──────────── CAST expression ────────────────────────
    // JPQL uses abstract types: String, Integer, Long, Float, Double, BigDecimal, BigInteger, Date, Time, Timestamp

    @Test
    fun testCastToString() {
        val expr = parseExpr("CAST(u.id AS String)") as CastExpression
        assertEquals("String", expr.targetType)
        assertTrue(expr.expression is PathExpression)
    }

    @Test
    fun testCastToInteger() {
        val expr = parseExpr("CAST(u.name AS Integer)") as CastExpression
        assertEquals("Integer", expr.targetType)
        assertTrue(expr.expression is PathExpression)
    }

    @Test
    fun testCastToLong() {
        val expr = parseExpr("CAST(u.id AS Long)") as CastExpression
        assertEquals("Long", expr.targetType)
    }

    @Test
    fun testCastToDouble() {
        val expr = parseExpr("CAST(u.id AS Double)") as CastExpression
        assertEquals("Double", expr.targetType)
    }

    @Test
    fun testCastToBigDecimal() {
        val expr = parseExpr("CAST(u.id AS BigDecimal)") as CastExpression
        assertEquals("BigDecimal", expr.targetType)
    }

    @Test
    fun testCastWithLiteral() {
        val expr = parseExpr("CAST('123' AS Integer)") as CastExpression
        assertEquals("Integer", expr.targetType)
        assertTrue(expr.expression is LiteralExpression)
    }

    @Test
    fun testCastWithParameter() {
        val expr = parseExpr("CAST(:value AS Double)") as CastExpression
        assertEquals("Double", expr.targetType)
        assertTrue(expr.expression is ParameterExpression)
    }

    @Test
    fun testNestedCast() {
        val expr = parseExpr("CAST(CAST(u.name AS Integer) AS String)") as CastExpression
        assertEquals("String", expr.targetType)
        assertTrue(expr.expression is CastExpression)
        assertEquals("Integer", (expr.expression as CastExpression).targetType)
    }

    @Test
    fun testCastInComparison() {
        val expr = parseExpr("CAST(u.id AS String) = '123'") as BinaryExpression
        assertEquals(BinaryOperator.EQ, expr.operator)
        assertTrue(expr.left is CastExpression)
    }
}
