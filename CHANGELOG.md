<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# JPQL2SQL Changelog

## [Unreleased]

## 1.0.0
### Added
- Convert JPQL `@Query` annotations to native SQL with one click (_Copy as Native Query_)
- JPA annotation-aware column resolution: `@Table`, `@Column`, `@JoinColumn`, `@Embedded`, `@EmbeddedId`, `@AttributeOverride`, `@JoinTable`
- Multi-dialect support: PostgreSQL, MySQL, Oracle, SQL Server, H2
- Full JPQL clause coverage: SELECT, JOIN, WHERE, GROUP BY, HAVING, ORDER BY, subqueries, CASE, aggregates, BETWEEN, IN, LIKE, IS NULL
- Constructor expression support (`SELECT NEW ...`)
- Named (`:param`) and positional (`?1`) parameter preservation
- Configurable SQL dialect under Settings > Tools > JPQL to SQL
