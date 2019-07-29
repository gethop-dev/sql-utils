# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/).

## [Unreleased]

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

[UNRELEASED]: https://github.com/magnetcoop/buddy-auth.jwt-oidc/compare/v0.4.1...HEAD
[0.4.1]: https://github.com/magnetcoop/buddy-auth.jwt-oidc/releases/tag/v0.4.0
[0.4.0]: https://github.com/magnetcoop/buddy-auth.jwt-oidc/releases/tag/v0.4.0
[0.3.0]: https://github.com/magnetcoop/buddy-auth.jwt-oidc/releases/tag/v0.3.0
[0.2.0]: https://github.com/magnetcoop/buddy-auth.jwt-oidc/releases/tag/v0.2.0
[0.1.0]: https://github.com/magnetcoop/buddy-auth.jwt-oidc/releases/tag/v0.1.0

