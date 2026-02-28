package com.github.jtdegraaf.jpql2sql.converter.resolver

class JoinInfoResolverTest : BaseResolverTestCase() {

    private lateinit var resolver: JoinInfoResolver

    override fun setUp() {
        super.setUp()
        addJpaStubs()
        addClass("""
            import jakarta.persistence.*;
            @Entity @Table(name = "departments")
            public class Department { @Id private Long id; private String name; }
        """)
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class Team { @Id private Long id; }
        """)
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class Project { @Id private Long id; }
        """)
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class Employee {
                @Id private Long id;
                @ManyToOne @JoinColumn(name = "dept_id") private Department department;
                @ManyToOne @JoinColumn(name = "mgr_id", referencedColumnName = "mgr_code") private Employee manager;
                @ManyToOne private Team team;
                @ManyToMany @JoinTable(name = "employee_projects", joinColumns = @JoinColumn(name = "emp_id"), inverseJoinColumns = @JoinColumn(name = "proj_id")) private java.util.Set<Project> projects;
            }
        """)
        val finder = EntityFinder(project)
        val tableResolver = TableResolver()
        resolver = JoinInfoResolver(project, finder, tableResolver)
    }

    // ─────────────── @JoinColumn resolution ─────────────

    fun testResolvesJoinColumnName() {
        val info = resolver.resolve("Employee", "department")
        assertNotNull(info)
        assertEquals("dept_id", info!!.columnName)
    }

    fun testResolvesReferencedColumnName() {
        val info = resolver.resolve("Employee", "department")
        assertNotNull(info)
        assertEquals("id", info!!.referencedColumnName)
    }

    fun testResolvesCustomReferencedColumnName() {
        val info = resolver.resolve("Employee", "manager")
        assertNotNull(info)
        assertEquals("mgr_code", info!!.referencedColumnName)
    }

    fun testResolvesTargetTableFromTargetEntity() {
        val info = resolver.resolve("Employee", "department")
        assertNotNull(info)
        assertEquals("departments", info!!.targetTable)
    }

    fun testNoJoinTableForManyToOne() {
        val info = resolver.resolve("Employee", "department")
        assertNotNull(info)
        assertNull(info!!.joinTable)
        assertNull(info.inverseColumnName)
    }

    // ──────────── Default when no @JoinColumn ───────────

    fun testDefaultsToFieldNameIdWhenNoJoinColumn() {
        val info = resolver.resolve("Employee", "team")
        assertNotNull(info)
        assertEquals("team_id", info!!.columnName)
        assertEquals("id", info.referencedColumnName)
    }

    // ──────────────── @JoinTable resolution ──────────────

    fun testResolvesJoinTableName() {
        val info = resolver.resolve("Employee", "projects")
        assertNotNull(info)
        assertEquals("employee_projects", info!!.joinTable)
    }

    fun testResolvesJoinTableJoinColumnName() {
        val info = resolver.resolve("Employee", "projects")
        assertNotNull(info)
        assertEquals("emp_id", info!!.columnName)
    }

    fun testResolvesJoinTableInverseColumnName() {
        val info = resolver.resolve("Employee", "projects")
        assertNotNull(info)
        assertEquals("proj_id", info!!.inverseColumnName)
    }

    fun testResolvesJoinTableTargetTable() {
        val info = resolver.resolve("Employee", "projects")
        assertNotNull(info)
        assertEquals("project", info!!.targetTable)
    }

    // ──────────────── Edge cases ─────────────────────────

    fun testReturnsNullForUnknownEntity() {
        assertNull(resolver.resolve("NonExistent", "field"))
    }

    fun testReturnsNullForUnknownField() {
        assertNull(resolver.resolve("Employee", "nonExistentField"))
    }
}
