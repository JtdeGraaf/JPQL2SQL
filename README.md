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

| Category | Feature |
|----------|---------|
| **JPQL Clauses** | SELECT, FROM, WHERE |
| | JOIN (INNER, LEFT, RIGHT) |
| | FULL OUTER JOIN, CROSS JOIN |
| | GROUP BY, HAVING |
| | ORDER BY (ASC, DESC, NULLS FIRST/LAST) |
| | Subqueries |
| | UNION, INTERSECT, EXCEPT (with ALL) |
| **Expressions** | Comparison operators (=, <>, <, >, <=, >=) |
| | Logical operators (AND, OR, NOT) |
| | Arithmetic operators (+, -, *, /) |
| | String concatenation (\|\|) |
| | BETWEEN, IN, LIKE, IS NULL |
| | CASE WHEN expressions |
| | EXISTS, NOT EXISTS |
| | Constructor expressions (SELECT NEW) |
| **Functions** | Aggregate (COUNT, SUM, AVG, MIN, MAX) |
| | String (CONCAT, SUBSTRING, UPPER, LOWER, TRIM, LENGTH, LOCATE) |
| | Math (ABS, SQRT, MOD) |
| | Date/Time (CURRENT_DATE, CURRENT_TIME, CURRENT_TIMESTAMP) |
| | EXTRACT(field FROM date) |
| | Enhanced TRIM (LEADING/TRAILING/BOTH char FROM) |
| | CAST(expr AS type) |
| | COALESCE, NULLIF |
| | FUNCTION('native_name', args) |
| | TYPE(alias) for polymorphic queries |
| **JPA Annotations** | @Entity, @Table |
| | @Column |
| | @Id, @GeneratedValue |
| | @ManyToOne, @OneToOne, @OneToMany, @ManyToMany |
| | @JoinColumn, @JoinTable |
| | @Embedded, @EmbeddedId, @Embeddable |
| | @AttributeOverride, @AttributeOverrides |
| | @IdClass (composite keys) |
| | @Inheritance (SINGLE_TABLE strategy) |
| | @DiscriminatorColumn, @DiscriminatorValue |
| | @Convert, @Converter (AttributeConverter) |
| | @Subselect (Hibernate) |
| **Parameters** | Named parameters (:param) |
| | Positional parameters (?1) |
| | Collection-valued parameters (IN :collection) |
| | SpEL expressions (:#{...}, ?#{...}, #{...}) |
| **Spring Data JPA** | Derived query methods (findBy, countBy, etc.) |
| | Implicit JOIN generation for nested properties |
| | FK optimization (no JOIN for .id access) |
| **Dialects** | PostgreSQL |
| | MySQL |
| | Oracle |
| | SQL Server |
| | H2 |

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
