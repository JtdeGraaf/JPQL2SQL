# JPQL2SQL

![Build](https://github.com/JtdeGraaf/JPQL2SQL/workflows/Build/badge.svg)
[![codecov](https://codecov.io/gh/JtdeGraaf/JPQL2SQL/branch/main/graph/badge.svg)](https://codecov.io/gh/JtdeGraaf/JPQL2SQL)
[![Version](https://img.shields.io/jetbrains/plugin/v/30434.svg)](https://plugins.jetbrains.com/plugin/30434)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/30434.svg)](https://plugins.jetbrains.com/plugin/30434)

<!-- Plugin description -->
Converts JPQL queries from `@Query` annotations into native SQL.

Right-click any `@Query` annotation → **Copy as Native Query**.

The plugin reads your JPA entity mappings (`@Table`, `@Column`, `@JoinColumn`, etc.) to generate SQL with correct table and column names.

Supports PostgreSQL, MySQL, Oracle, SQL Server, and H2. Configure your dialect under <kbd>Settings</kbd> > <kbd>Tools</kbd> > <kbd>JPQL to SQL</kbd>.
<!-- Plugin description end -->

## Supported Features

| Category | Feature | Status |
|----------|---------|--------|
| **JPQL Clauses** | SELECT, FROM, WHERE | Yes |
| | JOIN (INNER, LEFT, RIGHT) | Yes |
| | FULL OUTER JOIN, CROSS JOIN | Yes |
| | GROUP BY, HAVING | Yes |
| | ORDER BY (ASC, DESC, NULLS FIRST/LAST) | Yes |
| | Subqueries | Yes |
| | UNION, INTERSECT, EXCEPT (with ALL) | Yes |
| **Expressions** | Comparison operators (=, <>, <, >, <=, >=) | Yes |
| | Logical operators (AND, OR, NOT) | Yes |
| | Arithmetic operators (+, -, *, /) | Yes |
| | String concatenation (\|\|) | Yes |
| | BETWEEN, IN, LIKE, IS NULL | Yes |
| | CASE WHEN expressions | Yes |
| | EXISTS, NOT EXISTS | Yes |
| | Constructor expressions (SELECT NEW) | Yes |
| **Functions** | Aggregate (COUNT, SUM, AVG, MIN, MAX) | Yes |
| | String (CONCAT, SUBSTRING, UPPER, LOWER, TRIM, LENGTH, LOCATE) | Yes |
| | Math (ABS, SQRT, MOD) | Yes |
| | Date/Time (CURRENT_DATE, CURRENT_TIME, CURRENT_TIMESTAMP) | Yes |
| | EXTRACT(field FROM date) | Yes |
| | Enhanced TRIM (LEADING/TRAILING/BOTH char FROM) | Yes |
| | CAST(expr AS type) | Yes |
| | COALESCE, NULLIF | Yes |
| | FUNCTION('native_name', args) | Yes |
| | TYPE(alias) for polymorphic queries | Yes |
| **JPA Annotations** | @Entity, @Table | Yes |
| | @Column | Yes |
| | @Id, @GeneratedValue | Yes |
| | @ManyToOne, @OneToOne, @OneToMany, @ManyToMany | Yes |
| | @JoinColumn, @JoinTable | Yes |
| | @Embedded, @EmbeddedId, @Embeddable | Yes |
| | @AttributeOverride, @AttributeOverrides | Yes |
| | @IdClass (composite keys) | Yes |
| | @Inheritance (SINGLE_TABLE strategy) | Yes |
| | @DiscriminatorColumn, @DiscriminatorValue | Yes |
| | @Convert, @Converter (AttributeConverter) | Yes |
| | @Subselect (Hibernate) | Yes |
| **Parameters** | Named parameters (:param) | Yes |
| | Positional parameters (?1) | Yes |
| | Collection-valued parameters (IN :collection) | Yes |
| **Spring Data JPA** | Derived query methods (findBy, countBy, etc.) | Yes |
| | Implicit JOIN generation for nested properties | Yes |
| | FK optimization (no JOIN for .id access) | Yes |
| **Dialects** | PostgreSQL | Yes |
| | MySQL | Yes |
| | Oracle | Yes |
| | SQL Server | Yes |
| | H2 | Yes |

## Not Yet Supported

| Feature | Description |
|---------|-------------|
| @Inheritance (JOINED) | JOINED inheritance strategy requires multiple table joins |
| @Inheritance (TABLE_PER_CLASS) | TABLE_PER_CLASS inheritance strategy |
| @SecondaryTable | Mapping entity to multiple tables |
| @Formula (Hibernate) | Calculated columns using SQL expressions |
| TREAT operator | Downcasting in polymorphic queries |
| Lateral joins | LATERAL JOIN syntax |
| Window functions | OVER (PARTITION BY ... ORDER BY ...) |
| CTE (Common Table Expressions) | WITH clause |
| FETCH JOIN with batch size | @BatchSize optimization hints |

## Installation

- Using the IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "JPQL2SQL"</kbd> >
  <kbd>Install</kbd>

- Manually:

  Download the [latest release](https://github.com/JtdeGraaf/JPQL2SQL/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

## License

This project is licensed under the [MIT License](LICENSE).

---
Plugin based on the [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template).
