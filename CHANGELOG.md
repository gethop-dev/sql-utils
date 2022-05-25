# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/).

## [Unreleased]

## [0.4.12] - 2022-03-17
### Fixed
- Consider the case when `(.getSQLstate e)` returns `nil` in `explain-sql-error`. That can happen when using a closed database connection. This was only reproducible during development but when it happens, `sql-utils` would throw an exception.

## [0.4.11] - 2020-08-13
### Fixed
- sql-update-or-insert! was wrongly returning `:processed-values` key when inserting the row (instead of updating it). It was returning `:inserted-values` instead. Now it always returns `:processed-values` or `:error-details`, as the spec documents.

## [0.4.10] - 2020-03-26

### Added
- Add `sql-update-or-insert!` to update an existing row or inserting a new one if there wasn't one with the specified conditions on the where clause. If the update modifies more than one row, it rolls back all the changes and returns failure. Implementation inspired by http://clojure-doc.org/articles/ecosystem/java_jdbc/using_sql.html#updating-or-inserting-rows-conditionally

## [0.4.9] - 2020-03-21

### Changed
- Return additional error details when `:success?` is false. The main methods of the library return a `:success?` key specifying if the operation succeeded or not. But they don't give any details about why the operation was unsuccessful. That information is only sent to the logs. This additional information is especially useful when a query violates an integrity constratint (duplicate primary key, duplicate unique column, NULL value for a non-NULL column, etc). Returning this information to the caller allows it to handle the situation withou easier, without needing additional queries to find out the issue.

## [0.4.8] - 2020-02-28

### Added
- Add method to convert PostgreSQL JSON objects to Clojure collections.

## [0.4.7] - 2020-01-27

### Fixed
- Some functions (sql-update!,sql-delete! and sql-execute!) had their return value keys different from the spec definition. As spec doesn't validate return values we didn't notice it before.

## [0.4.6] - 2019-11-27

### Added
- Added a function to convert clojure collections into JDBC array objects.

## [0.4.5] - 2019-10-29
### Fixed
- sql-delete! wasn't working with tables that had underscores in their names, if we were referring to them using keywords having hyphens.

## [0.4.4] - 2019-10-24
### Changed
- Bumped minimum Leiningen version to 2.9.0
- Reorganize dev profile definition. The goal is to let profiles.clj file inside the project directory override some settings on demand (e.g., inject CIDER dependencies for different versions)

## [0.4.3] - 2019-10-21

### Changed
- Successful queries now log their details using TRACE level, instead of INFO, as they are considered fine-grained informational events that are most useful to debug an application. See https://www.tutorialspoint.com/log4j/log4j_logging_levels.htm for additional details.

## [0.4.2] - 2019-07-30

### Changed
- Relaxed spec & preconditions on pg-enum->keyword to also accept strings, in addition to PGobjects. Some versions of Postgresl or Postgresql client drivers return enums as plain strings, instead of wrapped in a PGObject value.

## [0.4.1] - 2019-07-29

### Added
- pg-enum->keyword, which is the complementary function of pg-enum->keyword.

## [0.4.0] - 2019-07-19

### Changed
- Moved Clojars artifact to dev.gethop/sql-utils, to use the same naming pattern as other HOP artifacts.

## [0.3.1] - 2019-07-19

### Added
- Added a more general version of map->pg-jsonb, called coll->pg-jsonb. It accepts not only maps, but any Clojure collection.

### Fixed
- A couple of typos in README.md (thanks to @lucassousaf and @bgalartza for spotting them!)

## [0.3.0] - 2019-07-18

### Fixed
- Several operations where creating their log entries with the wrong keywords, which would mislead anyone reading the logs. It has now been fixed.

## [0.2.0] - 2019-07-18

### Changed
- Relaxed the spec on map->pg-jsonb. Previously we only accepted maps, now we also accept `nil` (which is converted into the `null` JSON value).
- Added unit tests to check for that new supported case.

## [0.1.0] - 2019-07-16
- Initial commit

[UNRELEASED]: https://github.com/gethop-dev/sql-utils/compare/v0.4.12...HEAD
[0.4.12]: https://github.com/gethop-dev/sql-utils/releases/tag/v0.4.12
[0.4.11]: https://github.com/gethop-dev/sql-utils/releases/tag/v0.4.11
[0.4.10]: https://github.com/gethop-dev/sql-utils/releases/tag/v0.4.10
[0.4.9]: https://github.com/gethop-dev/sql-utils/releases/tag/v0.4.9
[0.4.8]: https://github.com/gethop-dev/sql-utils/releases/tag/v0.4.8
[0.4.7]: https://github.com/gethop-dev/sql-utils/releases/tag/v0.4.7
[0.4.6]: https://github.com/gethop-dev/sql-utils/releases/tag/v0.4.6
[0.4.5]: https://github.com/gethop-dev/sql-utils/releases/tag/v0.4.5
[0.4.4]: https://github.com/gethop-dev/sql-utils/releases/tag/v0.4.4
[0.4.3]: https://github.com/gethop-dev/sql-utils/releases/tag/v0.4.3
[0.4.2]: https://github.com/gethop-dev/sql-utils/releases/tag/v0.4.2
[0.4.1]: https://github.com/gethop-dev/sql-utils/releases/tag/v0.4.1
[0.4.0]: https://github.com/gethop-dev/sql-utils/releases/tag/v0.4.0
[0.3.1]: https://github.com/gethop-dev/sql-utils/releases/tag/v0.3.1
[0.3.0]: https://github.com/gethop-dev/sql-utils/releases/tag/v0.3.0
[0.2.0]: https://github.com/gethop-dev/sql-utils/releases/tag/v0.2.0
[0.1.0]: https://github.com/gethop-dev/sql-utils/releases/tag/v0.1.0
