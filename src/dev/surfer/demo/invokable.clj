(ns surfer.demo.invokable
  (:require [starfish.core :as sf]))

(defn invokable-odd? [params]
  (let [n (:n params)]
    {:n n
     :odd? (odd? n)}))

(def operation-odd?
  (sf/in-memory-operation (sf/invokable-metadata #'invokable-odd?)))

(defn invokable-inc [params]
  (update params :n inc))

(def operation-inc
  (sf/in-memory-operation (sf/invokable-metadata #'invokable-inc)))