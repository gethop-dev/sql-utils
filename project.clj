(defproject dev.gethop/sql-utils "0.4.14-SNAPSHOT"
  :description "A library designed as a thin convenience wrapper over clojure.java.jdbc"
  :url "https://github.com/gethop-dev/sql-utils"
  :license {:name "Mozilla Public Licence 2.0"
            :url "https://www.mozilla.org/en-US/MPL/2.0/"}
  :min-lein-version "2.9.8"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [clojure.java-time "0.3.2"]
                 [cheshire "5.10.0"]
                 [duct/logger "0.3.0"]
                 [org.clojure/java.jdbc "0.7.11"]
                 [org.postgresql/postgresql "42.2.11"]]
  :deploy-repositories [["snapshots" {:url "https://clojars.org/repo"
                                      :username :env/CLOJARS_USERNAME
                                      :password :env/CLOJARS_PASSWORD
                                      :sign-releases false}]
                        ["releases"  {:url "https://clojars.org/repo"
                                      :username :env/CLOJARS_USERNAME
                                      :password :env/CLOJARS_PASSWORD
                                      :sign-releases false}]]
  :codox
  {:output-path "docs/api"
   :metadata {:doc/format :markdown}}
  :profiles
  {:dev [:project/dev :profiles/dev]
   :repl {:repl-options {:host "0.0.0.0"
                         :port 4001}}
   :profiles/dev {}
   :project/dev {:plugins [[jonase/eastwood "1.2.3"]
                           [lein-cljfmt "0.8.0"]
                           [lein-codox "0.10.8"]]
                 :dependencies [[com.h2database/h2 "2.1.212"]]}})
