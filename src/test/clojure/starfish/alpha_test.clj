(ns starfish.alpha-test
  (:require [clojure.test :refer :all]
            [starfish.alpha :as sfa]
            [starfish.core :as sf])
  (:import (sg.dex.starfish.impl.remote RemoteAccount)
           (sg.dex.starfish.impl.memory MemoryAgent LocalResolverImpl)))

(defmethod sfa/resolve-agent "1acd41655b2d8ea3f3513cc847965e72c31bbc9bfc38e7e7ec901852bd3c457c" [resolver did ddo]
  (MemoryAgent/create resolver did))

(deftest remote-account-test
  (testing "Username & Password"
    (let [credentials (.getCredentials ^RemoteAccount (sfa/remote-account "foo" "bar"))]
      (is (= #{"username" "password"} (set (keys credentials))))))

  (testing "Token"
    (let [credentials (.getCredentials ^RemoteAccount (sfa/remote-account "x"))]
      (is (= #{"token"} (set (keys credentials)))))))

(deftest did->agent-test
  (testing "Resolve Agent"
    (let [did (sf/did "did:dex:1acd41655b2d8ea3f3513cc847965e72c31bbc9bfc38e7e7ec901852bd3c457c")]

      (testing "Able to resolve"
        (binding [sfa/*resolver* (LocalResolverImpl.)]
          (sfa/register! did {})
          (is (sfa/did->agent did))))

      (testing "Unable to resolve"
        (binding [sfa/*resolver* (LocalResolverImpl.)]
          (is (nil? (sfa/did->agent did))))))))
