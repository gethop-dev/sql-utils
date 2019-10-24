# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/).

## [Unreleased]

## [0.4.4] - 2019-10-24
Changed
- Bumped minimum Leiningen version to 2.9.0
- Reorganize dev profile definition. The goal is to let profiles.clj file inside the project directory override some settings on demand (e.g., inject CIDER dependencies for different versions)

## [0.4.3] - 2019-10-21

Changed
- Successful queries now log their details using TRACE level, instead of INFO, as they are considered fine-grained informational events that are most useful to debug an application. See https://www.tutorialspoint.com/log4j/log4j_logging_levels.htm for additional details.

## [0.4.2] - 2019-07-30

Changed
- Relaxed spec & preconditions on pg-enum->keyword to also accept strings, in addition to PGobjects. Some versions of Postgresl or Postgresql client drivers return enums as plain strings, instead of wrapped in a PGObject value.

## [0.4.1] - 2019-07-29

### Added
- pg-enum->keyword, which is the complementary function of pg-enum->keyword.

## [0.4.0] - 2019-07-19

### Changed
- Moved Clojars artifact to magnet/sql-utils, to use the same naming pattern as other Manget artifacts.

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

[UNRELEASED]: https://github.com/magnetcoop/sql-utils/compare/v0.4.4...HEAD
[0.4.4]: https://github.com/magnetcoop/sql-utils/releases/tag/v0.4.4
[0.4.3]: https://github.com/magnetcoop/sql-utils/releases/tag/v0.4.3
[0.4.2]: https://github.com/magnetcoop/sql-utils/releases/tag/v0.4.2
[0.4.1]: https://github.com/magnetcoop/sql-utils/releases/tag/v0.4.1
[0.4.0]: https://github.com/magnetcoop/sql-utils/releases/tag/v0.4.0
[0.3.0]: https://github.com/magnetcoop/sql-utils/releases/tag/v0.3.0
[0.2.0]: https://github.com/magnetcoop/sql-utils/releases/tag/v0.2.0
[0.1.0]: https://github.com/magnetcoop/sql-utils/releases/tag/v0.1.0

