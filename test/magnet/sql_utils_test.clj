(ns magnet.sql-utils-test
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.spec.test.alpha :as stest]
            [clojure.test :refer :all]
            [duct.logger :as logger]
            [magnet.sql-utils :as sql-utils])
  (:import org.postgresql.util.PGobject
           magnet.sql_utils.JDBCArray
           java.util.UUID))

(def db-spec "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1")

(defrecord AtomLogger [logs]
  logger/Logger
  (-log [logger level ns-str file line id event data]
    (swap! logs conj [level ns-str file line event data])))

(def roles [{:name "admin" :description "Application admin"}
            {:name "customer" :description "Application customer"}
            {:name "guest" :description "Application guest"}])

(def role-table-ddl
  "CREATE TABLE role (
     name VARCHAR(128) PRIMARY KEY,
     description VARCHAR(2048) NOT NULL)")

(def table-with-underscores-ddl
  "CREATE TABLE table_with_underscores (
     name VARCHAR(128) PRIMARY KEY,
     description VARCHAR(2048) NOT NULL)")

(defn enable-instrumentation []
  (-> (stest/enumerate-namespace 'magnet.sql-utils) stest/instrument))

(use-fixtures
  :once (fn reset-db [f]
          (enable-instrumentation)
          (jdbc/execute! db-spec role-table-ddl)
          (jdbc/execute! db-spec table-with-underscores-ddl)
          (f)
          (jdbc/execute! db-spec "DROP ALL OBJECTS")))

(deftest sql-utils!
  (let [role (first roles)
        delete-condition ["name = ?" (:name role)]
        update-condition delete-condition
        query ["SELECT name, description FROM role WHERE name = ?" (:name role)]
        logs (atom [])
        logger (->AtomLogger logs)]
    (testing "sql-insert!"
      (let [{:keys [success? inserted-values]} (sql-utils/sql-insert! db-spec logger :role
                                                                      (keys role) (vals role))]
        (is (and success?
                 (= 1 inserted-values)))))
    (testing "sql-insert! duplicated primary key/unique column"
      (let [{:keys [success? error-details]} (sql-utils/sql-insert! db-spec logger :role
                                                                    (keys role) (vals role))]
        (is (and (not success?)
                 (= (:error-type error-details)
                    :integrity-constraint-violation)))))
    (testing "sql-insert! no primary key"
      (let [{:keys [success? error-details]} (sql-utils/sql-insert! db-spec logger :role
                                                                    (keys (dissoc role :name))
                                                                    (vals (dissoc role :name)))]
        (is (and (not success?)
                 (= (:error-type error-details)
                    :integrity-constraint-violation)))))
    (testing "sql-insert! NULL value for non-NULL column"
      (let [role {:name "new-name", :description nil}
            {:keys [success? error-details]} (sql-utils/sql-insert! db-spec logger :role
                                                                    (keys role)
                                                                    (vals role))]
        (is (and (not success?)
                 (= (:error-type error-details)
                    :integrity-constraint-violation)))))
    (testing "sql-delete!"
      (let [{:keys [success? deleted-values]} (sql-utils/sql-delete! db-spec logger :role
                                                                     delete-condition)]
        (is (and success?
                 (= 1 deleted-values)))))
    (testing "sql-delete! using tables with underscores in their name"
      (let [{:keys [success? deleted-values]} (sql-utils/sql-delete! db-spec logger
                                                                     :table-with-underscores
                                                                     delete-condition)]
        (is (and success?
                 (= 0 deleted-values)))))

    ;; Empty role table to prepare for multiple insertion tests.
    (jdbc/execute! db-spec "TRUNCATE TABLE role")

    (testing "sql-insert-multi!"
      (let [cols (keys (first roles))
            values (map vals roles)
            {:keys [success? inserted-values]} (sql-utils/sql-insert-multi! db-spec logger :role
                                                                            cols values)]
        (is (and success?
                 (= (count roles) inserted-values)))))
    (testing "sql-insert-multi! duplicated primary key/unique column"
      (let [cols (keys (first roles))
            values (map vals (conj roles (first roles)))
            {:keys [success? error-details]} (sql-utils/sql-insert-multi! db-spec logger :role
                                                                          cols values)]
        (is (and (not success?)
                 (= (:error-type error-details)
                    :integrity-constraint-violation)))))
    (testing "sql-insert-multi! no primary key"
      (let [cols (keys (dissoc (first roles) :name))
            values (map vals (map #(dissoc % :name) roles))
            {:keys [success? error-details]} (sql-utils/sql-insert-multi! db-spec logger :role
                                                                          cols values)]
        (is (and (not success?)
                 (= (:error-type error-details)
                    :integrity-constraint-violation)))))
    (testing "sql-insert-multi! NULL value for non-NULL column"
      (let [cols (keys (first roles))
            values (map vals (map #(assoc % :description nil) roles))
            role {:name "new-name", :description nil}
            {:keys [success? error-details]} (sql-utils/sql-insert-multi! db-spec logger :role
                                                                          cols values)]
        (is (and (not success?)
                 (= (:error-type error-details)
                    :integrity-constraint-violation)))))
    (testing "sql-query"
      (let [{:keys [success? return-values]} (sql-utils/sql-query db-spec logger query)]
        (is (and success?
                 (= role (first return-values))))))
    (testing "sql-update!"
      (let [set-map {:description "An updated description"}
            {:keys [success? processed-values]} (sql-utils/sql-update! db-spec logger :role
                                                                       set-map
                                                                       update-condition)]
        (is (and success?
                 (= 1 processed-values)))))
    (testing "sql-update! duplicated primary/unique column"
      (let [set-map {:name "customer"}
            {:keys [success? error-details]} (sql-utils/sql-update! db-spec logger :role
                                                                    set-map
                                                                    update-condition)]
        (is (and (not success?)
                 (= (:error-type error-details)
                    :integrity-constraint-violation)))))
    (testing "sql-update! no primary key"
      (let [set-map {:name nil, :description "An updated description"}
            {:keys [success? error-details]} (sql-utils/sql-update! db-spec logger :role
                                                                    set-map
                                                                    update-condition)]
        (is (and (not success?)
                 (= (:error-type error-details)
                    :integrity-constraint-violation)))))
    (testing "sql-update! NULL value for non-NULL column"
      (let [set-map {:description nil}
            {:keys [success? error-details]} (sql-utils/sql-update! db-spec logger :role
                                                                    set-map
                                                                    update-condition)]
        (is (and (not success?)
                 (= (:error-type error-details)
                    :integrity-constraint-violation)))))
    (testing "sql-execute!"
      (let [sql-statement ["DELETE FROM role"]
            {:keys [success? processed-values]} (sql-utils/sql-execute! db-spec logger sql-statement)]
        (is (and success?
                 (= (count roles) processed-values)))))
    (testing "sql-execute! duplicated primary key/unique column"
      (let [sql-statement ["INSERT INTO role (name, description) VALUES ('admin', 'Admin desc'),('admin', 'Other admin desc')"]
            {:keys [success? error-details]} (sql-utils/sql-execute! db-spec logger sql-statement)]
        (is (and (not success?)
                 (= (:error-type error-details)
                    :integrity-constraint-violation)))))
    (testing "sql-execute! no primary key"
      (let [sql-statement ["INSERT INTO role (description) VALUES ('Customer desc')"]
            {:keys [success? error-details]} (sql-utils/sql-execute! db-spec logger sql-statement)]
        (is (and (not success?)
                 (= (:error-type error-details)
                    :integrity-constraint-violation)))))
    (testing "sql-execute! NULL value for non-NULL column"
      (let [sql-statement ["INSERT INTO role (name) VALUES ('guest')"]
            {:keys [success? error-details]} (sql-utils/sql-execute! db-spec logger sql-statement)]
        (is (and (not success?)
                 (= (:error-type error-details)
                    :integrity-constraint-violation)))))
    (testing "keyword->pg-enum"
      (let [result (sql-utils/keyword->pg-enum :test-keyword "test-enum")]
        (is (and (instance? PGobject result)
                 (= "test-enum" (.getType result))
                 (= "test-keyword" (.getValue result))))))
    (testing "pg-enum->keyword (PGobject)"
      (let [pg-enum (doto (PGobject.)
                      (.setType "test-enum")
                      (.setValue "test-keyword"))
            result (sql-utils/pg-enum->keyword pg-enum "test-enum")]
        (is (= result :test-keyword))))
    (testing "pg-enum->keyword (string)"
      (let [pg-enum "test-keyword"
            result (sql-utils/pg-enum->keyword pg-enum "test-enum")]
        (is (= result :test-keyword))))
    (testing "pg-enum->keyword (PGobject + wrong enum-type)"
      (let [pg-enum (doto (PGobject.)
                      (.setType "test-enum")
                      (.setValue "test-keyword"))]
        (is (thrown? java.lang.AssertionError (sql-utils/pg-enum->keyword pg-enum "other-test-enum")))))
    (testing "pg-enum->keyword (string + wrong enum-type)"
      (let [pg-enum "test-keyword"
            result (sql-utils/pg-enum->keyword pg-enum "other-test-enum")]
        (is (= result :test-keyword))))
    (testing "map->pg-jsonb! (regular map)"
      (let [result (sql-utils/map->pg-jsonb {:test-1 "test-1"
                                             :test-2 "test-2"})]
        (is (and (instance? PGobject result)
                 (= "jsonb" (.getType result))
                 (= "{\"test-1\":\"test-1\",\"test-2\":\"test-2\"}"
                    (.getValue result))))))
    (testing "map->pg-jsonb! (empty map)"
      (let [result (sql-utils/map->pg-jsonb {})]
        (is (and (instance? PGobject result)
                 (= "jsonb" (.getType result))
                 (= "{}" (.getValue result))))))
    (testing "map->pg-jsonb! (nil)"
      (let [result (sql-utils/map->pg-jsonb nil)]
        (is (and (instance? PGobject result)
                 (= "jsonb" (.getType result))
                 (= "null" (.getValue result))))))
    (testing "coll->pg-jsonb! (map)"
      (let [result (sql-utils/coll->pg-jsonb {:test-keyword "test-value"})]
        (is (and (instance? PGobject result)
                 (= "jsonb" (.getType result))
                 (= "{\"test-keyword\":\"test-value\"}" (.getValue result))))))
    (testing "coll->pg-jsonb! (vector)"
      (let [result (sql-utils/coll->pg-jsonb ["test-1" "test-2" :a])]
        (is (and (instance? PGobject result)
                 (= "jsonb" (.getType result))
                 (= "[\"test-1\",\"test-2\",\"a\"]" (.getValue result))))))
    (testing "coll->pg-jsonb! (list)"
      (let [result (sql-utils/coll->pg-jsonb '("test-1" "test-2" :a))]
        (is (and (instance? PGobject result)
                 (= "jsonb" (.getType result))
                 (= "[\"test-1\",\"test-2\",\"a\"]" (.getValue result))))))
    (testing "coll->pg-jsonb! (set)"
      (let [result (sql-utils/coll->pg-jsonb #{"test-1" "test-2" :a})]
        (is (and (instance? PGobject result)
                 (= "jsonb" (.getType result))
                 (= "[\"test-1\",\"test-2\",\"a\"]" (.getValue result))))))
    (testing "coll->pg-jsonb! (mixed and nested)"
      (let [result (sql-utils/coll->pg-jsonb {:a [1 2 3] :b #{"test-1" "test-2" :a}})]
        (is (and (instance? PGobject result)
                 (= "jsonb" (.getType result))
                 (= "{\"a\":[1,2,3],\"b\":[\"test-1\",\"test-2\",\"a\"]}"
                    (.getValue result))))))
    (testing "coll->pg-jsonb! (nil)"
      (let [result (sql-utils/coll->pg-jsonb nil)]
        (is (and (instance? PGobject result)
                 (= "jsonb" (.getType result))
                 (= "null" (.getValue result))))))
    (testing "coll->jdbc-array"
      (are [coll type-name] (let [result (sql-utils/coll->jdbc-array coll type-name)]
                              (instance? JDBCArray result))
        [1 2 3 4] "integer"
        '("a" "b" "c") "text"
        [(UUID/randomUUID) (UUID/randomUUID)] "uuid"))
    (testing "pg-json->coll"
      (let [pg-object (PGobject.)]
        (.setValue pg-object "[{\"a\":[1,2,3],\"b\":[\"test-1\",\"test-2\",1]}]")
        (.setType pg-object "json")
        (is (= (sql-utils/pg-json->coll pg-object)
               [{:a [1 2 3] :b ["test-1" "test-2" 1]}]))))))

(deftest sql-utils-logging
  (let [role (first roles)
        delete-condition ["name = ?" (:name role)]
        update-condition delete-condition
        query ["SELECT name, description FROM role WHERE name = ?" (:name role)]
        logs (atom [])
        logger (->AtomLogger logs)]
    (testing "successful queries produce TRACE logging entries!"
      (let [{:keys [success? inserted-values]} (sql-utils/sql-insert! db-spec logger :role
                                                                      (keys role) (vals role))
            [level ns-str file line event data] (first @logs)]
        (is (and success?
                 (= :trace level)))))
    (testing "failing queries produce ERROR logging entries!"
      (let [{:keys [success? inserted-values]} (sql-utils/sql-insert! db-spec logger :role
                                                                      (keys role) (vals role))
            [level ns-str file line event data] (second @logs)]
        (is (and (not success?)
                 (= :error level)))))))
