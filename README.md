# sq-utils
[![Build Status](https://travis-ci.org/magnetcoop/sql-utils.svg?branch=master)](https://travis-ci.org/magnetcoop/sql-utils)
[![Clojars Project](https://img.shields.io/clojars/v/sq-utils.svg)](https://clojars.org/sq-utils)

A Clojure library designed as a thin convenience wapper over [`clojure.java.jdbc`](https://github.com/clojure/java.jdbc). It wraps most used `clojure.java.jdbc` methods in transactions and logs all query results details (for both successful and failed ones) to the provided logger. 

The logger must implement the [`duct.loggger/Logger`](https://github.com/duct-framework/logger) protocol. Note that `nil` is valid logger value, although not a very useful one (all log entries are discarded).


## Installation

[![Clojars Project](https://clojars.org/magnet/sql-utils/latest-version.svg)](https://clojars.org/magnet/sql-utils)

## Usage

See [API Docs](/doc/api) and [`clojure.java.jdbc`](https://github.com/clojure/java.jdbc) documentation.

## License

Copyright 2019 Magnet S Coop

This Source Code Form is subject to the terms of the Mozilla Public License,
v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain
one at https://mozilla.org/MPL/2.0/
