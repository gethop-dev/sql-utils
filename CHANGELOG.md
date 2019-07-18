# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/).

## [Unreleased]

### Fixed
- Several operations where creating their log entries with the wrong keywords, which would mislead anyone reading the logs. It has now been fixed.

## [0.2.0] - 2019-07-18

### Changed
- Relaxed the spec on map->pg-jsonb. Previously we only accepted maps, now we also accept `nil` (which is converted into the `null` JSON value).
- Added unit tests to check for that new supported case.

## [0.1.0] - 2019-07-16
- Initial commit

[UNRELEASED]: https://github.com/magnetcoop/buddy-auth.jwt-oidc/compare/v0.3.0...HEAD
[0.3.0]: https://github.com/magnetcoop/buddy-auth.jwt-oidc/releases/tag/v0.3.0
[0.2.0]: https://github.com/magnetcoop/buddy-auth.jwt-oidc/releases/tag/v0.2.0
[0.1.0]: https://github.com/magnetcoop/buddy-auth.jwt-oidc/releases/tag/v0.1.0

