(ns surfer.demo.asset.content
  (:require [clojure.java.io :as io]
            [clojure.data.json :as data.json]
            [starfish.core :as sf]))

(defn json-reader [asset & [key-fn]]
  (with-open [input-stream (sf/content-stream asset)]
    (data.json/read (io/reader input-stream) :key-fn (or key-fn keyword))))
