(ns dev.gethop.sql-utils
  (:require [cheshire.core :as json]
            [clojure.java.jdbc :as jdbc]
            [clojure.java.jdbc.spec]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [duct.logger :refer [log]]
            [java-time :as jt]
            [java-time.pre-java8 :as jt-pre-j8])
  (:import [java.sql PreparedStatement SQLException]
           org.postgresql.jdbc.PgArray
           org.postgresql.util.PGobject))

(defn- convert-identifiers-option-fn
  [x]
  (str/lower-case (str/replace x \_ \-)))

(def ^:private convert-identifiers-option
  "Option map specifying how to convert ResultSet column names to keywords.
  It defaults to clojure.str/lower-case, but our keywords include
  hyphens instead of underscores. So we need to convert SQL
  underscores to hyphens in our keyworks."
  {:identifiers convert-identifiers-option-fn})

(defn- convert-entities-option-fn
  [x]
  (str/replace x \- \_))

(def ^:private convert-entities-option
  "Option map specifying how to convert Clojure keywords/string to SQL
  entity names. It defaults to identity, but our keywords include
  hyphens, which are invalid characters in SQL column names. So we
  change them to underscores."
  {:entities convert-entities-option-fn})

(defn- elapsed [start]
  (/ (double (- (System/nanoTime) start)) 1000000.0))

(extend-protocol clojure.java.jdbc/IResultSetReadColumn
  ;; PostgreSQL allows columns of a table to be defined as
  ;; variable-length multidimensional arrays. Queries including such
  ;; columns return their values as PgArray objects. We need to
  ;; convert those back into Clojure vectors to use them natively.  We
  ;; can extend `clojure.java.jdbc/IResultSetReadColumn` for any
  ;; clojure.java.jdbc/IResultSetReadColumn (see
  ;; http://clojure-doc.org/articles/ecosystem/java_jdbc/using_sql.html#protocol-extensions-for-transforming-values)
  PgArray
  (result-set-read-column [v _ _]
    (vec (.getArray v))))

(deftype JDBCArray [elements type-name]
  jdbc/ISQLParameter
  (set-parameter [_ stmt ix]
    (let [as-array (into-array Object elements)
          jdbc-array (.createArrayOf (.getConnection ^PreparedStatement stmt) type-name as-array)]
      (.setArray ^PreparedStatement stmt ix jdbc-array))))

(s/def ::jdbc-array #(instance? JDBCArray %))
(s/def ::coll->jdbc-array-args (s/cat :coll coll? :type-name string?))
(s/def ::coll->jdbc-array-ret ::jdbc-array)
(s/fdef coll->jdbc-array
  :args ::coll->jdbc-array-args
  :ret  ::coll->jdbc-array-ret)

(defn coll->jdbc-array
  [coll type-name]
  {:pre [(and (coll? coll)
              (string? type-name))]}
  (->JDBCArray coll type-name))

(s/def ::pg-object #(instance? PGobject %))
(s/def ::keyword->pg-enum-args (s/cat :kw keyword? :enum-type string?))
(s/def ::keyword->pg-enum-ret ::pg-object)
(s/fdef keyword->pg-enum
  :args ::keyword->pg-enum-args
  :ret  ::keyword->pg-enum-ret)

(defn keyword->pg-enum
  "Convert keyword `kw` into a Postgresql `enum-type` enum compatible object.
  `enum-type` is a string holding the name of the Postgresql enum type."
  [kw enum-type]
  {:pre [(and (keyword? kw)
              (string? enum-type))]}
  (doto (PGobject.)
    (.setType enum-type)
    (.setValue (name kw))))

(s/def ::pg-enum (s/or :string string? :pg-object ::pg-object))
(s/def ::pg-enum->keyword-args (s/cat :pg-enum ::pg-enum :enum-type string?))
(s/def ::pg-enum->keyword-ret keyword?)
(s/fdef pg-enum->keyword
  :args ::pg-enum->keyword-args
  :ret  ::pg-enum->keyword-ret)

(defn pg-enum->keyword
  "Convert `pg-enum` from `enum-type` compatible value into a keyword.
  `enum-type` is a string holding the name of the Postgresql enum
  type. `pg-enum` must be either a PGobject of `enum-type` type or a
  string[1]. Otherwise, it asserts.

  [1] This is because some versions of Postgresql or Postgresql
      client driver return enums as PGobjects and other as plain
      strings"
  [^PGobject pg-enum enum-type]
  {:pre [(and (or (and (s/valid? ::pg-object pg-enum)
                       (= (.getType pg-enum) enum-type))
                  (string? pg-enum))
              (string? enum-type))]}
  (if (string? pg-enum)
    (keyword pg-enum)
    (keyword (.getValue pg-enum))))

(s/def ::json->pg-jsonb-args (s/cat :json string?))
(s/def ::json->pg-jsonb-ret ::pg-object)
(s/fdef json->pg-jsonb
  :args ::json->pg-jsonb-args
  :ret  ::json->pg-jsonb-ret)

(defn json->pg-jsonb
  "Convert `json` string into a Postgresql jsonb compatible object.
  In order to persist JSON into Postgresql we need to wrap the json
  into a PGObject and set the \"jsonb\" type on it. See
  https://jdbc.postgresql.org/documentation/publicapi/org/postgresql/util/PGobject.html"
  [json]
  {:pre [(string? json)]}
  (doto (PGobject.)
    (.setType "jsonb")
    (.setValue json)))

(s/def ::nilable-map (s/nilable map?))
(s/def ::map->pg-jsonb-args (s/cat :m ::nilable-map))
(s/def ::map->pg-jsonb-ret ::pg-object)
(s/fdef map->pg-jsonb
  :args ::map->pg-jsonb-args
  :ret  ::map->pg-jsonb-ret)

(defn map->pg-jsonb
  "Convert map `m` into a Postgresql jsonb compatible object."
  [m]
  {:pre [(s/valid? ::nilable-map m)]}
  (-> m
      json/generate-string
      json->pg-jsonb))

(s/def ::nilable-coll (s/nilable coll?))
(s/def ::coll->pg-jsonb-args (s/cat :m ::nilable-coll))
(s/def ::coll->pg-jsonb-ret ::pg-object)
(s/fdef coll->pg-jsonb
  :args ::coll->pg-jsonb-args
  :ret  ::coll->pg-jsonb-ret)

(defn coll->pg-jsonb
  "Convert coll `c` into a Postgresql jsonb compatible object."
  [c]
  {:pre [(s/valid? ::nilable-coll c)]}
  (-> c
      json/generate-string
      json->pg-jsonb))

(s/def ::pg-json (s/and ::pg-object #(some #{(.getType ^PGobject %)} ["json" "jsonb"])))
(s/def ::pg-json->coll-args (s/cat :pg-json ::pg-json))
(s/def ::pg-json->coll-ret coll?)
(s/fdef pg-json->coll
  :args ::pg-json->coll-args
  :ret ::pg-json->coll-ret)

(defn pg-json->coll
  "Convert PostgreSQL Object `pg-object` into a Clojure collection."
  [^PGobject pg-json]
  {:pre [(s/valid? ::pg-json pg-json)]}
  (json/decode (.getValue pg-json) #(keyword (convert-identifiers-option-fn %))))

(s/def ::instant #(jt/instant? %))
(s/def ::sql-timestamp #(instance? java.sql.Timestamp %))
(s/def ::instant->sql-timestamp-args (s/cat :v ::instant))
(s/def ::instant->sql-timestamp-ret ::sql-timestamp)
(s/fdef instant->sql-timestamp
  :args ::instant->sql-timestamp-args
  :ret  ::instant->sql-timestamp-ret)

(defn instant->sql-timestamp
  "Convert clojure.java-time/Instant `instant` into a SQL Timestamp"
  [instant]
  {:pre [(s/valid? ::instant instant)]}
  (jt-pre-j8/sql-timestamp instant "UTC"))

(defn- explain-sql-error [e]
  (if-not (instance? java.sql.SQLException e)
    {:success? false
     :error-details {:error-type :unkown-sql-error}}
    (if-let [sql-state (.getSQLState ^SQLException e)]
      (cond
        ;; Various types of contraint integrity errors (missing
        ;; primary key, NULL value for a non-NULL, columns, UNIQUE
        ;; value constratint, missing FOREIGN KEY, etc.
        ;;
        ;; From http://www.h2database.com/javadoc/org/h2/api/ErrorCode.html
        ;;   H2 #{23502 23503 23505 23506 23507 23513 23514}
        ;; From https://www.postgresql.org/docs/12/errcodes-appendix.html
        ;;   Postgresql #{23000 23001 23502 23503 23505 23514 23P01}
        ;; From https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-reference-error-sqlstates.html
        ;;   MySQL #{1022 1048 1052 1062 1169 1216 1217 1451 1452 1557 1586 1761 1762 1859}
        ;;      All of them mapped to SQLState 23000
        ;; From https://mariadb.com/kb/en/mariadb-error-codes/
        ;;   MariaDB #{1022 1048 1052 1062 1169 1216 1217 1451 1452 1557 1586 1761 1762 1859 4025}
        ;;      All of them mapped to SQLState 23000
        (re-matches #"^23[0-9P]{3}" sql-state)
        {:success? false
         :error-details {:error-type :integrity-constraint-violation}}

        :else
        {:success? false
         :error-details {:error-type :other-sql-error}})
      {:success? false
       :error-details {:error-type :unkown-sql-error}})))

(s/def ::db-spec :clojure.java.jdbc.spec/db-spec)
(s/def ::logger #(satisfies? duct.logger/Logger %))
(s/def ::sql-statement :clojure.java.jdbc.spec/sql-params)
(s/def ::success? boolean?)
(s/def ::return-values coll?)
(s/def ::error-type keyword?)
(s/def ::error-details (s/keys :req-un [::error-type]))
(s/def ::sql-query-args (s/cat :db-spec ::db-spec
                               :logger ::logger
                               :sql-statement ::sql-statement))
(s/def ::sql-query-ret (s/keys :req-un [::success?]
                               :opt-un [::return-values
                                        ::error-details]))
(s/fdef sql-query
  :args ::sql-query-args
  :ret  ::sql-query-ret)

(defn sql-query
  "FIXME: document this function"
  [db-spec logger sql-statement]
  {:pre [(and (s/valid? ::db-spec db-spec)
              (s/valid? ::logger logger)
              (s/valid? ::sql-statement sql-statement))]}
  (let [start (System/nanoTime)]
    (try
      (let [result (jdbc/query db-spec sql-statement convert-identifiers-option)
            msec (elapsed start)]
        (log logger :trace ::sql-query-success {:msec msec
                                                :count (count result)
                                                :sql-statement sql-statement})
        {:success? true :return-values result})
      (catch Exception e
        (let [msec (elapsed start)]
          (log logger :error ::sql-query-error {:msec msec
                                                :ex-message (.getMessage e)
                                                :sql-statement sql-statement})
          (explain-sql-error e))))))

(s/def ::table :clojure.java.jdbc.spec/identifier)
(s/def ::cols coll?)
(s/def ::values coll?)
(s/def ::sql-insert!-args (s/cat :db-spec ::db-spec
                                 :logger ::logger
                                 :table ::table
                                 :cols ::cols
                                 :values ::values))
(s/def ::inserted-values integer?)
(s/def ::sql-insert!-ret (s/keys :req-un [::success?]
                                 :opt-un [::inserted-values
                                          ::error-details]))
(s/fdef sql-insert!
  :args ::sql-insert!-args
  :ret  ::sql-insert!-ret)

(defn sql-insert!
  "FIXME: document this function"
  [db-spec logger table cols values]
  {:pre [(and (s/valid? ::db-spec db-spec)
              (s/valid? ::logger logger)
              (s/valid? ::cols cols)
              (s/valid? ::values values))]}
  (let [start (System/nanoTime)]
    (try
      (let [count (first (jdbc/insert! db-spec table cols values convert-entities-option))
            msec (elapsed start)]
        (log logger :trace ::sql-insert!-success {:msec msec
                                                  :count count
                                                  :cols cols
                                                  :values values})
        {:success? true :inserted-values count})
      (catch Exception e
        (let [msec (elapsed start)]
          (log logger :error ::sql-insert!-error {:msec msec
                                                  :ex-message (.getMessage e)
                                                  :cols cols
                                                  :values values})
          (explain-sql-error e))))))

(s/def ::sql-insert-multiple!-args (s/cat :db-spec ::db-spec
                                          :logger ::logger
                                          :table ::table
                                          :cols ::cols
                                          :values ::values))
(s/def ::sql-insert-multiple!-ret (s/keys :req-un [::success?]
                                          :opt-un [::inserted-values
                                                   ::error-details]))
(s/fdef sql-insert-multiple!
  :args ::sql-insert-multiple!-args
  :ret  ::sql-insert-multiple!-ret)

(defn sql-insert-multi!
  "FIXME: document this function"
  [db-spec logger table cols values]
  {:pre [(and (s/valid? ::db-spec db-spec)
              (s/valid? ::logger logger)
              (s/valid? ::cols cols)
              (s/valid? ::values values))]}
  (let [start (System/nanoTime)]
    (try
      (let [count (count (jdbc/insert-multi! db-spec table cols values convert-entities-option))
            msec (elapsed start)]
        (log logger :trace ::sql-insert-multi!-success {:msec msec
                                                        :count count
                                                        :cols cols
                                                        :values values})
        {:success? true :inserted-values count})
      (catch Exception e
        (let [msec (elapsed start)]
          (log logger :error ::sql-insert-multi!-error {:msec msec
                                                        :ex-message (.getMessage e)
                                                        :cols cols
                                                        :values values})
          (explain-sql-error e))))))

(s/def ::set-map (s/map-of :clojure.java.jdbc.spec/identifier
                           :clojure.java.jdbc.spec/sql-value))
(s/def ::where-clause (s/spec :clojure.java.jdbc.spec/where-clause))
(s/def ::sql-update!-args (s/cat :db-spec ::db-spec
                                 :logger ::logger
                                 :table ::table
                                 :set-map ::set-map
                                 :where-clase ::where-clause))
(s/def ::processed-values integer?)
(s/def ::sql-update!-ret (s/keys :req-un [::success?]
                                 :opt-un [::processed-values
                                          ::error-details]))
(s/fdef sql-update!
  :args ::sql-update!-args
  :ret  ::sql-update!-ret)

(defn sql-update!
  "FIXME: document this function"
  [db-spec logger table set-map where-clause]
  {:pre [(and (s/valid? ::db-spec db-spec)
              (s/valid? ::logger logger)
              (s/valid? ::table table)
              (s/valid? ::set-map set-map)
              (s/valid? ::where-clause where-clause))]}
  (let [start (System/nanoTime)]
    (try
      (let [count (first (jdbc/update! db-spec table set-map where-clause convert-entities-option))
            msec (elapsed start)]
        (log logger :trace ::sql-update!-success {:msec msec
                                                  :count count
                                                  :set-map set-map
                                                  :where-clause where-clause})
        {:success? true :processed-values count})
      (catch Exception e
        (let [msec (elapsed start)]
          (log logger :error ::sql-update!-error {:msec msec
                                                  :ex-message (.getMessage e)
                                                  :set-map set-map
                                                  :where-clause where-clause})
          (explain-sql-error e))))))

(s/def ::sql-update-or-insert!-args (s/cat :db-spec ::db-spec
                                           :logger ::logger
                                           :table ::table
                                           :set-map ::set-map
                                           :where-clase ::where-clause))
(s/def ::sql-update-or-insert!-ret (s/keys :req-un [::success?]
                                           :opt-un [::processed-values
                                                    ::error-details]))
(s/fdef sql-update-or-insert!
  :args ::sql-update-or-insert!-args
  :ret  ::sql-update-or-insert!-ret)

(defn sql-update-or-insert!
  "FIXME: document this function"
  [db-spec logger table set-map where-clause]
  {:pre [(and (s/valid? ::db-spec db-spec)
              (s/valid? ::logger logger)
              (s/valid? ::table table)
              (s/valid? ::set-map set-map)
              (s/valid? ::where-clause where-clause))]}
  (let [start (System/nanoTime)]
    (try
      (jdbc/with-db-transaction [t-conn db-spec]
        (let [count (first (jdbc/update! t-conn table set-map where-clause convert-entities-option))
              msec (elapsed start)]
          (cond
            ;; Nothing update, so insert a new row.
            (zero? count)
            (let [cols (keys set-map)
                  values (vals set-map)
                  start (System/nanoTime)
                  count (first (jdbc/insert! t-conn table cols values convert-entities-option))
                  msec (elapsed start)]
              (log logger :trace ::sql-insert!-success {:msec msec
                                                        :count count
                                                        :cols cols
                                                        :values values})
              {:success? true :processed-values count})

            ;; A single row updated, we are good to go!
            (= 1 count)
            (do
              (log logger :trace ::sql-update!-success {:msec msec
                                                        :count count
                                                        :set-map set-map
                                                        :where-clause where-clause})
              {:success? true :processed-values count})

            ;; Whoops, we updated too many rows. Roll the update back. Throwig an exception
            ;; will roll back the update, leaving the database untouched.
            :else
            (throw (Exception. "sql-update-or-insert! tried to update more than one row!")))))
      (catch Exception e
        (let [msec (elapsed start)]
          (log logger :error ::sql-update-or-insert!-error {:msec msec
                                                            :ex-message (.getMessage e)
                                                            :set-map set-map
                                                            :where-clause where-clause})
          (explain-sql-error e))))))

(s/def ::sql-delete!-args (s/cat :db-spec ::db-spec
                                 :logger ::logger
                                 :table ::table
                                 :where-clause ::where-clause))
(s/def ::deleted-values integer?)
(s/def ::sql-delete!-ret (s/keys :req-un [::success?]
                                 :opt-un [::deleted-values
                                          ::error-details]))
(s/fdef sql-delete!
  :args ::sql-delete!-args
  :ret  ::sql-delete!-ret)

(defn sql-delete!
  "FIXME: document this function"
  [db-spec logger table where-clause]
  {:pre [(and (s/valid? ::db-spec db-spec)
              (s/valid? ::logger logger)
              (s/valid? ::table table)
              (s/valid? ::where-clause where-clause))]}
  (let [start (System/nanoTime)]
    (try
      (let [count (first (jdbc/delete! db-spec table where-clause convert-entities-option))
            msec (elapsed start)]
        (log logger :trace ::sql-delete-success {:msec msec
                                                 :count count
                                                 :where-clause where-clause})
        {:success? true :deleted-values count})
      (catch Exception e
        (let [msec (elapsed start)]
          (log logger :error ::sql-delete-error {:msec msec
                                                 :ex-message (.getMessage e)
                                                 :where-clause where-clause})
          (explain-sql-error e))))))

(s/def ::sql-execute!-args (s/cat :db-spec ::db-spec
                                  :logger ::logger
                                  :sql-statement ::sql-statement))
(s/def ::sql-execute!-ret (s/keys :req-un [::success?]
                                  :opt-un [::processed-values
                                           ::error-details]))
(s/fdef sql-execute!
  :args ::sql-execute!-args
  :ret  ::sql-execute!-ret)

(defn sql-execute!
  "FIXME: document this function"
  [db-spec logger sql-statement]
  {:pre [(and (s/valid? ::db-spec db-spec)
              (s/valid? ::logger logger)
              (s/valid? ::sql-statement sql-statement))]}
  (let [start (System/nanoTime)]
    (try
      (let [count (first (jdbc/execute! db-spec sql-statement))
            msec (elapsed start)]
        (log logger :trace ::sql-execute!-success {:msec msec
                                                   :count count
                                                   :sql-statement sql-statement})
        {:success? true :processed-values count})
      (catch Exception e
        (let [msec (elapsed start)]
          (log logger :error ::sql-execute!-error {:msec msec
                                                   :ex-message (.getMessage e)
                                                   :sql-statement sql-statement})
          (explain-sql-error e))))))
