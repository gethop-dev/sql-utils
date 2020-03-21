(defproject magnet/sql-utils "0.4.9-SNAPSHOT"
  :description "A library designed as a thin convenience wapper over clojure.java.jdbc"
  :url "https://github.com/magnetcoop/sql-utils"
  :license {:name "Mozilla Public Licence 2.0"
            :url "https://www.mozilla.org/en-US/MPL/2.0/"}
  :min-lein-version "2.9.0"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [clojure.java-time "0.3.2"]
                 [cheshire "5.10.0"]
                 [duct/logger "0.3.0"]
                 [org.clojure/java.jdbc "0.7.11"]
                 [org.postgresql/postgresql "42.2.11"]]
  :deploy-repositories [["snapshots" {:url "https://clojars.org/repo"
                                      :username :env/clojars_username
                                      :password :env/clojars_password
                                      :sign-releases false}]
                        ["releases"  {:url "https://clojars.org/repo"
                                      :username :env/clojars_username
                                      :password :env/clojars_password
                                      :sign-releases false}]]
  :codox
  {:output-path "docs/api"
   :metadata {:doc/format :markdown}}
  :profiles
  {:dev [:project/dev :profiles/dev]
   :repl {:repl-options {:host "0.0.0.0"
                         :port 4001}}
   :profiles/dev {}
   :project/dev {:plugins [[jonase/eastwood "0.3.4"]
                           [lein-cljfmt "0.6.2"]
                           [lein-codox "0.10.7"]]
                 :dependencies [[com.h2database/h2 "1.4.199"]]}})
