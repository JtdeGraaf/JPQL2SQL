# JPQL2SQL

![Build](https://github.com/JtdeGraaf/JPQL2SQL/workflows/Build/badge.svg)

<!-- Plugin description -->
**JPQL2SQL** converts JPQL queries from `@Query` annotations into native SQL — directly in your IDE.

Right-click any `@Query` annotation and instantly copy the equivalent native SQL to your clipboard. The plugin reads your JPA entity mappings (`@Table`, `@Column`, `@JoinColumn`, `@Embedded`, `@JoinTable`, and more) so the generated SQL uses the correct table and column names.

## Features

- **One-click conversion** — right-click a `@Query` annotation → _Copy as Native Query_
- **JPA-aware** — resolves `@Table`, `@Column`, `@JoinColumn`, `@Embedded`, `@EmbeddedId`, `@AttributeOverride`, and `@JoinTable` annotations from your codebase
- **Multi-dialect support** — PostgreSQL, MySQL, Oracle, SQL Server, and H2
- **Full JPQL coverage** — SELECT, JOIN (INNER / LEFT / RIGHT), WHERE, GROUP BY, HAVING, ORDER BY, subqueries, CASE expressions, aggregate functions, BETWEEN, IN, LIKE, IS NULL, and more
- **Constructor expressions** — `SELECT NEW com.example.Dto(...)` translates to a clean column list
- **Named and positional parameters** — `:paramName` and `?1` are preserved in the output

## Getting Started

1. Open a repository or Spring Data interface containing a `@Query` annotation
2. Place your cursor on the annotation
3. Right-click → **Copy as Native Query**
4. Paste the native SQL wherever you need it

Configure your preferred SQL dialect under <kbd>Settings</kbd> > <kbd>Tools</kbd> > <kbd>JPQL to SQL</kbd>.
<!-- Plugin description end -->

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
