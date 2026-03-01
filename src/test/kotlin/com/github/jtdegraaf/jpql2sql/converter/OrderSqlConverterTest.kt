package com.github.jtdegraaf.jpql2sql.converter

import com.github.jtdegraaf.jpql2sql.converter.entities.UserEntities

/**
 * SQL conversion tests for Order entity.
 */
class OrderSqlConverterTest : BaseJpaTestCase() {

    override fun setUpEntities() {
        UserEntities.addOrder(myFixture)
    }

    fun testCountAll() {
        val sql = convertWithPostgres("SELECT COUNT(*) FROM Order o")
        assertEquals("SELECT COUNT(*) FROM orders o", sql)
    }

    fun testCountDistinct() {
        val sql = convertWithPostgres("SELECT COUNT(DISTINCT o.amount) FROM Order o")
        assertEquals("SELECT COUNT(DISTINCT o.amount) FROM orders o", sql)
    }

    fun testSum() {
        val sql = convertWithPostgres("SELECT SUM(o.amount) FROM Order o")
        assertEquals("SELECT SUM(o.amount) FROM orders o", sql)
    }
}
