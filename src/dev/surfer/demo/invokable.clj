(ns surfer.demo.invokable
  (:require [starfish.core :as sf]))

(defn ^{:params {"n" "json"}} invokable-odd? [params]
  (let [n (:n params)]
    {:n n
     :odd? (odd? n)}))

(def invokable-odd?-metadata
  (sf/invokable-metadata #'invokable-odd? (meta #'invokable-odd?)))

(def operation-odd?
  (sf/in-memory-operation invokable-odd?-metadata))

;; TODO
(defn invokable-asset-odd? [params]
  ;; Asset -> Map
  (let [n 1]
    {:n n
     :odd? (odd? n)}))

(defn invokable-asset2-odd? [params]
  ;; Asset -> Asset
  (let [n 1]
    {:n n
     :odd? (odd? n)}))

(defn ^{:params {"n" "json"}} invokable-inc [params]
  (update params :n inc))

(def invokable-inc-metadata
  (sf/invokable-metadata #'invokable-inc (meta #'invokable-inc)))

(def operation-inc
  (sf/in-memory-operation invokable-inc-metadata))