(ns magnet.sql-utils
  (:require [cheshire.core :as json]
            [clojure.java.jdbc :as jdbc]
            [clojure.java.jdbc.spec]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [duct.logger :refer [log]]
            [java-time :as jt]
            [java-time.pre-java8 :as jt-pre-j8])
  (:import org.postgresql.jdbc.PgArray
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
  ;; http://clojure.atlassian.net/browse/JDBC-46)
  PgArray
  (result-set-read-column [v _ _]
    (vec (.getArray v))))

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

(s/def ::pg-enum->keyword-args (s/cat :val ::pg-object :enum-type string?))
(s/def ::pg-enum->keyword-ret keyword?)
(s/fdef pg-enum->keyword
  :args ::pg-enum->keyword-args
  :ret  ::pg-enum->keyword-ret)

(defn pg-enum->keyword
  "Convert `pg-enum` from `enum-type` enum compatible PGobject into a keyword.
  `enum-type` is a string holding the name of the Postgresql enum type.
  Asserts if `pg-enum` doesn't contain a value of type `enum-type`"
  [pg-enum enum-type]
  {:pre [(and (s/valid? ::pg-object pg-enum)
              (string? enum-type)
              (= (.getType pg-enum) enum-type))]}
  (keyword (.getValue pg-enum)))

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

(s/def ::db-spec :clojure.java.jdbc.spec/db-spec)
(s/def ::logger #(satisfies? duct.logger/Logger %))
(s/def ::sql-statement :clojure.java.jdbc.spec/sql-params)
(s/def ::success? boolean?)
(s/def ::return-values coll?)
(s/def ::sql-query-args (s/cat :db-spec ::db-spec
                               :logger ::logger
                               :sql-statement ::sql-statement))
(s/def ::sql-query-ret (s/keys :req-un [::success?]
                               :opt-un [::return-values]))
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
        (log logger :info ::sql-query-success {:msec msec
                                               :count (count result)
                                               :sql-statement sql-statement})
        {:success? true :return-values result})
      (catch Exception e
        (let [result {:success? false}
              msec (elapsed start)]
          (log logger :error ::sql-query-error {:msec msec
                                                :ex-message (.getMessage e)
                                                :sql-statement sql-statement})
          result)))))

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
                                 :opt-un [::inserted-values]))
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
        (log logger :info ::sql-insert!-success {:msec msec
                                                 :count count
                                                 :cols cols
                                                 :values values})
        {:success? true :inserted-values count})
      (catch Exception e
        (let [result {:success? false}
              msec (elapsed start)]
          (log logger :error ::sql-insert!-error {:msec msec
                                                  :ex-message (.getMessage e)
                                                  :cols cols
                                                  :values values})
          result)))))

(s/def ::sql-insert-multiple!-args (s/cat :db-spec ::db-spec
                                          :logger ::logger
                                          :table ::table
                                          :cols ::cols
                                          :values ::values))
(s/def ::sql-insert-multiple!-ret (s/keys :req-un [::success?]
                                          :opt-un [::inserted-values]))
(s/fdef sql-insert-mulitple!
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
        (log logger :info ::sql-insert-multi!-success {:msec msec
                                                       :count count
                                                       :cols cols
                                                       :values values})
        {:success? true :inserted-values count})
      (catch Exception e
        (let [result {:success? false}
              msec (elapsed start)]
          (log logger :error ::sql-insert-multi!-error {:msec msec
                                                        :ex-message (.getMessage e)
                                                        :cols cols
                                                        :values values})
          result)))))

(s/def ::set-map (s/map-of :clojure.java.jdbc.spec/identifier
                           :clojure.java.jdbc.spec/sql-value))
(s/def ::where-clause (s/spec :clojure.java.jdbc.spec/where-clause))
(s/def ::sql-update!-args (s/cat :db-spec ::db-spec
                                 :logger ::logger
                                 :table ::table
                                 :set-map ::set-map
                                 :where-clase ::where-clause))
(s/def ::sql-update!-ret (s/keys :req-un [::success?]
                                 :opt-un [::inserted-values]))
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
        (log logger :info ::sql-update!-success {:msec msec
                                                 :count count
                                                 :set-map set-map
                                                 :where-clause where-clause})
        {:success? true :processed-values count})
      (catch Exception e
        (let [result {:success? false}
              msec (elapsed start)]
          (log logger :error ::sql-update!-error {:msec msec
                                                  :ex-message (.getMessage e)
                                                  :set-map set-map
                                                  :where-clause where-clause})
          result)))))

(s/def ::sql-delete!-args (s/cat :db-spec ::db-spec
                                 :logger ::logger
                                 :table ::table
                                 :where-clause ::where-clause))
(s/def ::sql-delete!-ret (s/keys :req-un [::success?]
                                 :opt-un [::inserted-values]))
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
      (let [count (first (jdbc/delete! db-spec table where-clause convert-identifiers-option))
            msec (elapsed start)]
        (log logger :info ::sql-delete-success {:msec msec
                                                :count count
                                                :where-clause where-clause})
        {:success? true :deleted-values count})
      (catch Exception e
        (let [result {:success? false}
              msec (elapsed start)]
          (log logger :error ::sql-delete-error {:msec msec
                                                 :ex-message (.getMessage e)
                                                 :where-clause where-clause})
          result)))))

(s/def ::sql-execute!-args (s/cat :db-spec ::db-spec
                                  :logger ::logger
                                  :sql-statement ::sql-statement))
(s/def ::sql-execute!-ret (s/keys :req-un [::success?]
                                  :opt-un [::inserted-values]))
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
        (log logger :info ::sql-execute!-success {:msec msec
                                                  :count count
                                                  :sql-statement sql-statement})
        {:success? true :processed-values count})
      (catch Exception e
        (let [result {:success? false}
              msec (elapsed start)]
          (log logger :error ::sql-execute!-error {:msec msec
                                                   :ex-message (.getMessage e)
                                                   :sql-statement sql-statement})
          result)))))
