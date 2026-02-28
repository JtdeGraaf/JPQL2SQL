package com.github.jtdegraaf.jpql2sql.converter.resolver

class PsiUtilsTest : BaseResolverTestCase() {

    override fun setUp() {
        super.setUp()
        addJpaStubs()
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class FieldEntity {
                @Id private Long id;
                private String name;
                public String getComputed() { return ""; }
                public boolean isActive() { return true; }
            }
        """)
        addClass("public class BaseClass { protected String baseField; }")
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class ChildEntity extends BaseClass { @Id private Long id; }
        """)
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class AnnotatedEntity {
                @Id private Long id;
                @Column(name = "tag_col") private String tagged;
            }
        """)
    }

    // ─────────────────── findField ──────────────────────

    fun testFindFieldByName() {
        val field = PsiUtils.findField(findClass("FieldEntity"), "name")
        assertNotNull(field)
        assertEquals("name", field!!.name)
    }

    fun testFindFieldInSuperclass() {
        assertNotNull("should find inherited field", PsiUtils.findField(findClass("ChildEntity"), "baseField"))
    }

    fun testFindFieldReturnsNullForMissing() {
        assertNull(PsiUtils.findField(findClass("FieldEntity"), "doesNotExist"))
    }

    // ─────────────────── findGetter ─────────────────────

    fun testFindGetterStandard() {
        val getter = PsiUtils.findGetter(findClass("FieldEntity"), "computed")
        assertNotNull(getter)
        assertEquals("getComputed", getter!!.name)
    }

    fun testFindGetterBooleanIs() {
        val getter = PsiUtils.findGetter(findClass("FieldEntity"), "active")
        assertNotNull("should find isActive()", getter)
        assertEquals("isActive", getter!!.name)
    }

    fun testFindGetterReturnsNull() {
        assertNull(PsiUtils.findGetter(findClass("FieldEntity"), "name"))
    }

    // ──────────── findAnnotatedMembers ───────────────────

    fun testFindAnnotatedMembersFieldOnly() {
        assertEquals(1, PsiUtils.findAnnotatedMembers(findClass("FieldEntity"), "name").size)
    }

    fun testFindAnnotatedMembersEmpty() {
        assertTrue(PsiUtils.findAnnotatedMembers(findClass("FieldEntity"), "ghost").isEmpty())
    }

    // ──────────────── hasAnyAnnotation ───────────────────

    fun testHasAnyAnnotationPositive() {
        val members = PsiUtils.findAnnotatedMembers(findClass("AnnotatedEntity"), "tagged")
        assertTrue(PsiUtils.hasAnyAnnotation(members, listOf("jakarta.persistence.Column")))
    }

    fun testHasAnyAnnotationNegative() {
        val members = PsiUtils.findAnnotatedMembers(findClass("AnnotatedEntity"), "tagged")
        assertFalse(PsiUtils.hasAnyAnnotation(members, listOf("jakarta.persistence.ManyToOne")))
    }

    // ──────────────── getAnnotation ──────────────────────

    fun testGetAnnotationOnField() {
        val field = PsiUtils.findField(findClass("AnnotatedEntity"), "tagged")!!
        assertNotNull(PsiUtils.getAnnotation(field, "jakarta.persistence.Column"))
    }

    fun testGetAnnotationOnClass() {
        assertNotNull(PsiUtils.getAnnotation(findClass("AnnotatedEntity"), "jakarta.persistence.Entity"))
    }

    fun testGetAnnotationReturnsNull() {
        val field = PsiUtils.findField(findClass("AnnotatedEntity"), "tagged")!!
        assertNull(PsiUtils.getAnnotation(field, "jakarta.persistence.Id"))
    }

    // ──────────── getAnnotationStringValue ────────────────

    fun testGetAnnotationStringValueExtractsName() {
        val field = PsiUtils.findField(findClass("AnnotatedEntity"), "tagged")!!
        val annotation = PsiUtils.getAnnotation(field, "jakarta.persistence.Column")!!
        assertEquals("tag_col", PsiUtils.getAnnotationStringValue(annotation, "name"))
    }

    fun testGetAnnotationStringValueReturnsBlankForDefault() {
        val annotation = PsiUtils.getAnnotation(findClass("AnnotatedEntity"), "jakarta.persistence.Entity")!!
        assertTrue(PsiUtils.getAnnotationStringValue(annotation, "name").isNullOrBlank())
    }

    // ──────────── findAnnotation (list) ──────────────────

    fun testFindAnnotationSearchesMultipleFqns() {
        val field = PsiUtils.findField(findClass("AnnotatedEntity"), "tagged")!!
        val annotation = PsiUtils.findAnnotation(
            listOf(field),
            listOf("javax.persistence.Column", "jakarta.persistence.Column")
        )
        assertNotNull(annotation)
    }
}
