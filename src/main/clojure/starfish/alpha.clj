(ns starfish.alpha
  (:require [starfish.core :as sf]
            [clojure.data.json :as data.json])
  (:import (sg.dex.starfish Resolver)
           (sg.dex.starfish.impl.memory LocalResolverImpl)
           (sg.dex.starfish.impl.remote RemoteAccount)
           (sg.dex.starfish.util Utils)
           (java.util HashMap Map)))

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
   (when-let [ddo-str (.getDDOString resolver did)]
     (let [ddo (data.json/read-str ddo-str :key-fn str)]
       (resolve-agent resolver did ddo)))))
