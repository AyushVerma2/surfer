(ns surfer.test-users
  (:require
    [surfer.store :as store]
    [surfer.utils :as u]
    [cemerick.friend [credentials :as creds]]
    [clojure.test :refer :all]
    [com.stuartsierra.component :as component]
    [surfer.system :as system]
    [surfer.database :as database]
    [surfer.utils :as utils]))

(def test-system
  nil)

(defn system-fixture [f]
  (let [system (component/start
                 (system/new-system {:web-server
                                     {:port (utils/random-port)}}))]

    (alter-var-root #'test-system (constantly system))

    (try
      (f)
      (finally
        (component/stop system)

        (alter-var-root #'test-system (constantly nil))))))

(use-fixtures :once system-fixture)

(deftest test-register
  (let [user {:username (str "bob" (System/currentTimeMillis))
              :password (creds/hash-bcrypt "OpenSesame")}

        db (database/db (system/h2 test-system))

        id (store/register-user db user)]
    (is (u/valid-user-id? id))
    (is (= id (:id (store/get-user db id))))))