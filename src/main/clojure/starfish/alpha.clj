(ns starfish.alpha
  (:require [starfish.core :as sf]
            [clojure.data.json :as data.json])
  (:import (sg.dex.starfish Resolver)
           (sg.dex.starfish.impl.memory LocalResolverImpl)))

(def ^:dynamic *resolver*
  (LocalResolverImpl.))

(defn register!
  ([did ddo]
   (register! *resolver* did ddo))
  ([^Resolver resolver did ddo]
   (.registerDID resolver did (data.json/write-str ddo))))

(defmulti resolve-agent
  "Resolves Agent for the giving `resolver`, `did` and `ddo`.

   Dispatches by DID ID (Agent ID)."
  (fn [resolver did ddo]
    (sf/did-id did)))

(defn did->agent
  ([did]
   (did->agent *resolver* did))
  ([resolver did]
   (when-let [s (.getDDOString resolver did)]
     (let [ddo (data.json/read-str s :key-fn str)]
       (resolve-agent resolver did ddo)))))
