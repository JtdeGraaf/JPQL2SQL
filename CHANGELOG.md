<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# JPQL2SQL Changelog

## [Unreleased]

## [1.3.0]

### Added

- `@IdClass` annotation support for composite primary keys
- Hibernate `@Subselect` annotation support for mapping entities to SQL subqueries
- `UNION`, `UNION ALL`, `INTERSECT`, `INTERSECT ALL`, `EXCEPT`, `EXCEPT ALL` set operations
- `@Inheritance` and `@DiscriminatorColumn` support for SINGLE_TABLE inheritance strategy
- `TYPE(alias)` expression support for polymorphic queries (e.g., `WHERE TYPE(e) = Manager`)
- `@AttributeConverter` support with automatic literal value conversion (e.g., Boolean to 'Y'/'N' or 1/0)
- Subclass entities using SINGLE_TABLE inheritance automatically resolve to the parent's table
- Collection-valued parameters support (`IN :collectionParam` without parentheses)

## [1.2.0]

### Added

- Support for Spring Data JPA derived query methods (`findByName`, `findByAgeGreaterThan`, `countByStatus`, etc.)
- Implicit JOIN generation for relationship traversals in `@Query` JPQL (e.g., `u.department.name` automatically generates `LEFT JOIN departments`)
- FK optimization: accessing `.id` on `@ManyToOne`/`@OneToOne` relationships uses the FK column directly without generating a JOIN
- Support for String Concatenation || Operator
- Support for EXTRACT(field FROM date) Function
- Support for Enhanced TRIM Syntax
- Support for FULL OUTER JOIN and CROSS JOIN

## [1.1.0]

### Added

- SQL output is now automatically formatted using IntelliJ's SQL formatter
- Support for `CAST(expression AS type)` expressions with automatic JPQL to SQL type mapping (String→VARCHAR, Integer→INTEGER, Long→BIGINT, etc.)
- Support for parameterless native function calls like `SYSDATE()` (Hibernate-compatible syntax)
- Oracle dialect: `SYSDATE`, `SYSTIMESTAMP`, `USER` etc. rendered without parentheses
- Support for `FETCH FIRST n ROWS ONLY` and `OFFSET m ROWS FETCH FIRST n ROWS ONLY` syntax

### Changed

- Resilient parsing: unparsed/unsupported syntax is now captured as SQL comments (`/* UNPARSED: ... */`) instead of throwing exceptions

## [1.0.3]

### Changed

- Removed useless parenthesis that got added around conditions

## [1.0.2]

### Added

- Support for JPQL `FUNCTION('native_name', args...)` syntax to call native database functions
- Support for `EXISTS` and `NOT EXISTS` expressions

### Fixed

- Fixed subquery parsing not terminating correctly at closing parenthesis

## [1.0.1]

### Fixed

- Resolved issue with `@OneToMany` relationships not generating correct JOINs in certain edge cases

### Changed

- Refactored internal tests to make it easier to add new testcases for complex JPQL queries

## [1.0.0]

### Added

- Convert JPQL `@Query` annotations to native SQL with one click (_Copy as Native Query_)
- JPA annotation-aware column resolution: `@Table`, `@Column`, `@JoinColumn`, `@Embedded`, `@EmbeddedId`, `@AttributeOverride`, `@JoinTable`
- Multi-dialect support: PostgreSQL, MySQL, Oracle, SQL Server, H2
- Full JPQL clause coverage: SELECT, JOIN, WHERE, GROUP BY, HAVING, ORDER BY, subqueries, CASE, aggregates, BETWEEN, IN, LIKE, IS NULL
- Constructor expression support (`SELECT NEW ...`)
- Named (`:param`) and positional (`?1`) parameter preservation
- Configurable SQL dialect under Settings > Tools > JPQL to SQL

[Unreleased]: https://github.com/JtdeGraaf/JPQL2SQL/compare/1.3.0...HEAD
[1.3.0]: https://github.com/JtdeGraaf/JPQL2SQL/compare/1.2.0...1.3.0
[1.2.0]: https://github.com/JtdeGraaf/JPQL2SQL/compare/1.1.0...1.2.0
[1.1.0]: https://github.com/JtdeGraaf/JPQL2SQL/compare/1.0.3...1.1.0
[1.0.3]: https://github.com/JtdeGraaf/JPQL2SQL/compare/1.0.2...1.0.3
[1.0.2]: https://github.com/JtdeGraaf/JPQL2SQL/compare/1.0.1...1.0.2
[1.0.1]: https://github.com/JtdeGraaf/JPQL2SQL/compare/1.0.0...1.0.1
[1.0.0]: https://github.com/JtdeGraaf/JPQL2SQL/commits/1.0.0
