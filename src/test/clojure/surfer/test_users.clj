(ns surfer.test-users
  (:require 
    [surfer.store :as store]
    [surfer.utils :as u]
    [cemerick.friend [credentials :as creds]])
  (:require [clojure.test :refer :all]))


(deftest test-register
  (let [user {:username (str "bob" (System/currentTimeMillis))
              :password (creds/hash-bcrypt "OpenSesame")}
        id (store/register-user user)]
    (is (u/valid-user-id? id))
    ;; TODO Fix `nil` db
    (is (= id (:id (store/get-user nil id))))))