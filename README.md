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
