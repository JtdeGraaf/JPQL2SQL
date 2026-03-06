package com.github.jtdegraaf.jpql2sql.converter.resolver

/**
 * Common JPA annotation fully-qualified names for both `jakarta.persistence` and `javax.persistence`.
 */
object JpaAnnotations {

    val COLUMN = listOf(
        "jakarta.persistence.Column",
        "javax.persistence.Column"
    )

    val JOIN_COLUMN = listOf(
        "jakarta.persistence.JoinColumn",
        "javax.persistence.JoinColumn"
    )

    val JOIN_COLUMNS = listOf(
        "jakarta.persistence.JoinColumns",
        "javax.persistence.JoinColumns"
    )

    val JOIN_TABLE = listOf(
        "jakarta.persistence.JoinTable",
        "javax.persistence.JoinTable"
    )

    val EMBEDDED = listOf(
        "jakarta.persistence.Embedded",
        "javax.persistence.Embedded",
        "jakarta.persistence.EmbeddedId",
        "javax.persistence.EmbeddedId"
    )

    val ATTRIBUTE_OVERRIDE = listOf(
        "jakarta.persistence.AttributeOverride",
        "javax.persistence.AttributeOverride"
    )

    val ATTRIBUTE_OVERRIDES = listOf(
        "jakarta.persistence.AttributeOverrides",
        "javax.persistence.AttributeOverrides"
    )

    val SINGLE_VALUED_ASSOCIATION = listOf(
        "jakarta.persistence.ManyToOne",
        "javax.persistence.ManyToOne",
        "jakarta.persistence.OneToOne",
        "javax.persistence.OneToOne"
    )

    val ONE_TO_MANY = listOf(
        "jakarta.persistence.OneToMany",
        "javax.persistence.OneToMany"
    )

    val MANY_TO_MANY = listOf(
        "jakarta.persistence.ManyToMany",
        "javax.persistence.ManyToMany"
    )

    val TABLE = listOf(
        "jakarta.persistence.Table",
        "javax.persistence.Table"
    )

    val ENTITY = listOf(
        "jakarta.persistence.Entity",
        "javax.persistence.Entity"
    )

    val ID = listOf(
        "jakarta.persistence.Id",
        "javax.persistence.Id",
        "jakarta.persistence.EmbeddedId",
        "javax.persistence.EmbeddedId"
    )

    val ID_CLASS = listOf(
        "jakarta.persistence.IdClass",
        "javax.persistence.IdClass"
    )

    val SUBSELECT = listOf(
        "org.hibernate.annotations.Subselect"
    )
}

