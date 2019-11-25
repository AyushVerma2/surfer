(ns starfish.alpha
  (:require [starfish.core :as sf]
            [clojure.data.json :as data.json])
  (:import (sg.dex.starfish Agent Resolver)
           (sg.dex.starfish.util DID)))

(defn register! [^Resolver resolver ^DID did ddo]
  (.registerDID resolver did (data.json/write-str ddo)))

(defn services [ddo]
  (->> (get ddo "service")
       (map #(get % "type"))
       (into #{})))

(defn get-ddo [resolver did]
  (clojure.data.json/read-str (.getDDOString resolver did) :key-fn str))

(defn get-agent [client resolver did]
  (if-let [procurer (:starfish/procurer client)]
    (procurer client resolver did)
    (throw (ex-info "Can't get Agent without a procurer." client))))

(defn get-asset [client ^DID did]
  (some
    (fn [^Resolver resolver]
      (let [ddo (get-ddo resolver did)
            capable? (every? (services ddo) #{"Ocean.Meta.v1" "Ocean.Storage.v1"})]
        (when capable?
          (.getAsset ^Agent (get-agent client resolver did) ^String (sf/did-path did)))))
    (:starfish/resolvers client)))


