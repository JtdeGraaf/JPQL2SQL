package com.github.jtdegraaf.jpql2sql.converter.resolver

import org.junit.Assert.*
import org.junit.Test

class JpaAnnotationsTest {
    @Test
    fun testColumnContainsBothNamespaces() {
        assertTrue(JpaAnnotations.COLUMN.contains("jakarta.persistence.Column"))
        assertTrue(JpaAnnotations.COLUMN.contains("javax.persistence.Column"))
    }

    @Test
    fun testJoinColumnContainsBothNamespaces() {
        assertTrue(JpaAnnotations.JOIN_COLUMN.contains("jakarta.persistence.JoinColumn"))
        assertTrue(JpaAnnotations.JOIN_COLUMN.contains("javax.persistence.JoinColumn"))
    }

    @Test
    fun testEmbeddedContainsAllVariants() {
        assertTrue(JpaAnnotations.EMBEDDED.contains("jakarta.persistence.Embedded"))
        assertTrue(JpaAnnotations.EMBEDDED.contains("javax.persistence.Embedded"))
        assertTrue(JpaAnnotations.EMBEDDED.contains("jakarta.persistence.EmbeddedId"))
        assertTrue(JpaAnnotations.EMBEDDED.contains("javax.persistence.EmbeddedId"))
    }

    @Test
    fun testSingleValuedAssociationContainsAllVariants() {
        assertTrue(JpaAnnotations.SINGLE_VALUED_ASSOCIATION.contains("jakarta.persistence.ManyToOne"))
        assertTrue(JpaAnnotations.SINGLE_VALUED_ASSOCIATION.contains("javax.persistence.ManyToOne"))
        assertTrue(JpaAnnotations.SINGLE_VALUED_ASSOCIATION.contains("jakarta.persistence.OneToOne"))
        assertTrue(JpaAnnotations.SINGLE_VALUED_ASSOCIATION.contains("javax.persistence.OneToOne"))
    }

    @Test
    fun testEntityContainsBothNamespaces() {
        assertTrue(JpaAnnotations.ENTITY.contains("jakarta.persistence.Entity"))
        assertTrue(JpaAnnotations.ENTITY.contains("javax.persistence.Entity"))
    }

    @Test
    fun testTableContainsBothNamespaces() {
        assertTrue(JpaAnnotations.TABLE.contains("jakarta.persistence.Table"))
        assertTrue(JpaAnnotations.TABLE.contains("javax.persistence.Table"))
    }

    @Test
    fun testJoinTableContainsBothNamespaces() {
        assertTrue(JpaAnnotations.JOIN_TABLE.contains("jakarta.persistence.JoinTable"))
        assertTrue(JpaAnnotations.JOIN_TABLE.contains("javax.persistence.JoinTable"))
    }

    @Test
    fun testAttributeOverrideContainsBothNamespaces() {
        assertTrue(JpaAnnotations.ATTRIBUTE_OVERRIDE.contains("jakarta.persistence.AttributeOverride"))
        assertTrue(JpaAnnotations.ATTRIBUTE_OVERRIDE.contains("javax.persistence.AttributeOverride"))
        assertTrue(JpaAnnotations.ATTRIBUTE_OVERRIDES.contains("jakarta.persistence.AttributeOverrides"))
        assertTrue(JpaAnnotations.ATTRIBUTE_OVERRIDES.contains("javax.persistence.AttributeOverrides"))
    }

    @Test
    fun testJoinColumnsContainsBothNamespaces() {
        assertTrue(JpaAnnotations.JOIN_COLUMNS.contains("jakarta.persistence.JoinColumns"))
        assertTrue(JpaAnnotations.JOIN_COLUMNS.contains("javax.persistence.JoinColumns"))
    }
}
