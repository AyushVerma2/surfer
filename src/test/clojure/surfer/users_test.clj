(ns surfer.users-test
  (:require
    [surfer.store :as store]
    [surfer.utils :as u]
    [cemerick.friend [credentials :as creds]]
    [clojure.test :refer :all]
    [surfer.system :as system]
    [surfer.database :as database]
    [surfer.test.fixture :as fixture]))

(def test-system
  nil)

(use-fixtures :once (fixture/system-fixture #'test-system))

(deftest ^:integration test-register
  (let [user {:username (str "bob" (System/currentTimeMillis))
              :password (creds/hash-bcrypt "OpenSesame")}

        db (database/db (system/h2 test-system))

        id (store/register-user db user)]
    (is (u/valid-user-id? id))
    (is (= id (:id (store/get-user db id))))))