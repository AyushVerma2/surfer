(ns surfer.demo.invokable
  (:require [starfish.core :as sf]))

(defn invokable-odd? [params]
  (let [n (:n params)]
    {:n n
     :odd? (odd? n)}))

(def operation-odd?
  (sf/in-memory-operation (sf/invokable-metadata #'invokable-odd?)))

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

(defn invokable-inc [params]
  (update params :n inc))

(def operation-inc
  (sf/in-memory-operation (sf/invokable-metadata #'invokable-inc)))