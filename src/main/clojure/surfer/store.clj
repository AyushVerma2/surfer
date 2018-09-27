(ns surfer.store
  (:require [surfer.utils :as u]
            [clojure.data.json :as json]))

(defonce database (atom {}))

(defn register [asset-metadata-str]
  (let [hash (u/hex-string (u/sha256 asset-metadata-str))]
    (swap! database assoc hash asset-metadata-str)
    hash))

(defn lookup [id-str]
  (get @database id-str))