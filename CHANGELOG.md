<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# JPQL2SQL Changelog

## [Unreleased]

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

[Unreleased]: https://github.com/JtdeGraaf/JPQL2SQL/compare/1.0.1...HEAD
[1.0.1]: https://github.com/JtdeGraaf/JPQL2SQL/compare/1.0.0...1.0.1
[1.0.0]: https://github.com/JtdeGraaf/JPQL2SQL/commits/1.0.0
