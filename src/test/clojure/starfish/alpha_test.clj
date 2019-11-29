(ns starfish.alpha-test
  (:require [clojure.test :refer :all]
            [starfish.alpha :as sfa]
            [starfish.core :as sf])
  (:import (sg.dex.starfish.impl.remote RemoteAccount)
           (sg.dex.starfish.impl.memory MemoryAgent LocalResolverImpl)
           (sg.dex.starfish Resolver)))

(defmethod sfa/resolve-agent "1acd41655b2d8ea3f3513cc847965e72c31bbc9bfc38e7e7ec901852bd3c457c" [resolver did ddo]
  (MemoryAgent/create resolver did))

(deftest remote-account-test
  (testing "Username & Password"
    (let [credentials (.getCredentials ^RemoteAccount (sfa/remote-account "foo" "bar"))]
      (is (= #{"username" "password"} (set (keys credentials))))))

  (testing "Token"
    (let [credentials (.getCredentials ^RemoteAccount (sfa/remote-account "x"))]
      (is (= #{"token"} (set (keys credentials)))))))

(deftest register!-test
  (testing "Register DID & DDO"
    (let [local-resolver (LocalResolverImpl.)
          did (sf/did "did:dex:1acd41655b2d8ea3f3513cc847965e72c31bbc9bfc38e7e7ec901852bd3c457c")]

      (testing "Able to get DDO"
        (binding [sfa/*resolver* local-resolver]
          (sfa/register! did {:name "Surfer"})
          (is (= {"name" "Surfer"} (.getDDO ^Resolver sfa/*resolver* did)))
          (is (= {"name" "Surfer"} (.getDDO ^Resolver local-resolver did)))))

      (testing "Unable to get DDO"
        (is (= nil (.getDDO ^Resolver sfa/*resolver* did)))))))

(deftest did->agent-test
  (testing "Resolve Agent"
    (let [did (sf/did "did:dex:1acd41655b2d8ea3f3513cc847965e72c31bbc9bfc38e7e7ec901852bd3c457c")]

      (testing "Able to resolve"
        (let [local-resolver (LocalResolverImpl.)]
          (binding [sfa/*resolver* local-resolver]
            (sfa/register! did {})
            (is (sfa/did->agent did))
            (is (sfa/did->agent local-resolver did)))))

      (testing "Unable to resolve"
        (binding [sfa/*resolver* (LocalResolverImpl.)]
          (is (nil? (sfa/did->agent did))))))))
