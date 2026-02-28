package com.github.jtdegraaf.jpql2sql.converter.resolver

class AttributeOverrideHelperTest : BaseResolverTestCase() {

    override fun setUp() {
        super.setUp()
        addJpaStubs()
        addClass("""
            import jakarta.persistence.*;
            @Embeddable
            public class EmbAddress { private String street; private String city; }
        """)
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class OverrideEntity {
                @Id private Long id;

                @Embedded
                @AttributeOverride(name = "street", column = @Column(name = "shipping_street"))
                private EmbAddress shippingAddress;

                @Embedded
                @AttributeOverrides({
                    @AttributeOverride(name = "street", column = @Column(name = "billing_street")),
                    @AttributeOverride(name = "city", column = @Column(name = "billing_city"))
                })
                private EmbAddress billingAddress;

                @Embedded
                private EmbAddress plainAddress;
            }
        """)
    }

    fun testFindsOverrideColumnForMatchingField() {
        val overrides = getOverridesFor("shippingAddress")
        val result = AttributeOverrideHelper.findOverrideColumn("street", overrides)
        assertEquals("shipping_street", result)
    }

    fun testReturnsNullForNonMatchingField() {
        val overrides = getOverridesFor("shippingAddress")
        val result = AttributeOverrideHelper.findOverrideColumn("city", overrides)
        assertNull(result)
    }

    fun testFindsOverrideColumnFromAttributeOverrides() {
        val overrides = getOverridesFor("billingAddress")
        assertEquals("billing_street", AttributeOverrideHelper.findOverrideColumn("street", overrides))
        assertEquals("billing_city", AttributeOverrideHelper.findOverrideColumn("city", overrides))
    }

    fun testReturnsNullWhenNoOverrides() {
        assertNull(AttributeOverrideHelper.findOverrideColumn("street", emptyList()))
    }

    fun testReturnsNullForEmbeddedWithoutOverrides() {
        val overrides = getOverridesFor("plainAddress")
        assertNull(AttributeOverrideHelper.findOverrideColumn("street", overrides))
    }

    // ═══════════════════ Helpers ═════════════════════════

    private fun getOverridesFor(fieldName: String): List<com.intellij.psi.PsiAnnotation> {
        val psiClass = findClass("OverrideEntity")
        val members = PsiUtils.findAnnotatedMembers(psiClass, fieldName)

        val result = mutableListOf<com.intellij.psi.PsiAnnotation>()
        for (member in members) {
            for (fqn in JpaAnnotations.ATTRIBUTE_OVERRIDE) {
                PsiUtils.getAnnotation(member, fqn)?.let { result.add(it) }
            }
            for (fqn in JpaAnnotations.ATTRIBUTE_OVERRIDES) {
                val container = PsiUtils.getAnnotation(member, fqn) ?: continue
                val value = container.findAttributeValue("value")
                if (value is com.intellij.psi.PsiArrayInitializerMemberValue) {
                    for (init in value.initializers) {
                        if (init is com.intellij.psi.PsiAnnotation) result.add(init)
                    }
                } else if (value is com.intellij.psi.PsiAnnotation) {
                    result.add(value)
                }
            }
        }
        return result
    }
}

