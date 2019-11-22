(ns starfish.alpha
  (:require [starfish.core :as sf])
  (:import (sg.dex.starfish Agent Resolver)
           (sg.dex.starfish.util DID)))

(defn provides? [ddo services]
  (let [ddo-services (->> (get ddo "service")
                          (map #(get % "type"))
                          (into #{}))]
    (every? ddo-services services)))

(defn get-ddo [resolver did]
  (clojure.data.json/read-str (.getDDOString resolver did) :key-fn str))

(defn get-agent [client resolver did]
  (if-let [procurer (:starfish/procurer client)]
    (procurer client resolver did)
    (throw (ex-info "Can't get Agent without a procurer." client))))

(defn get-asset [client ^DID did]
  (some
    (fn [^Resolver resolver]
      (let [ddo (get-ddo resolver did)]
        (when (provides? ddo #{"Ocean.Meta.v1" "Ocean.Storage.v1"})
          (.getAsset ^Agent (get-agent client resolver did) ^String (sf/did-path did)))))
    (:starfish/resolvers client)))


