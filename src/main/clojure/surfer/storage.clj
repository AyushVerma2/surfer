(ns surfer.storage
  (:require [clojure.java.io :as io])
  (:require [surfer.utils :as utils])
  (:import [java.io File DataInputStream InputStream]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(def STORAGE-PATH (str (System/getProperty "user.home") "/.ocean"))
(def checked-path (atom false))

(defn- get-storage-path 
  "Gets a storage path given a valid asset ID"
  ([assetID]
    (when-not @checked-path
      (let [^File file (io/file STORAGE-PATH)]
        (when-not (.isDirectory file)
          (.mkdir file)))
      (reset! checked-path true))
    (str STORAGE-PATH "/" assetID ".ocb")))

(defn save 
  "Saves data to the storage location for a given asset.

   Data may be an InputStream, Reader, File, byte[], char[], or String."
  ([assetID data]
    (when-not (utils/valid-asset-id? assetID)
      (throw (IllegalArgumentException. (str "Asset ID not valid [" assetID "]"))))
    (let [path (get-storage-path assetID)
          file (io/file path)]
      (io/copy data file))))

(defn bytes-from-stream 
  "Fully reads an input stream into an array of bytes"
  (^bytes [^InputStream input-stream]
    (let [dis (DataInputStream. input-stream)])))

(defn load-stream 
  "Gets an input stream for the sepcified asset ID.

   Returns null is the asset data does not exist.

   Should be used inside with-open to ensure the InputStream is properly
   closed."
  ([assetID]
    (when-not (utils/valid-asset-id? assetID)
      (throw (IllegalArgumentException. (str "Asset ID not valid [" assetID "]"))))
    (let [path (get-storage-path assetID)
          ^File file (io/file path)]
      (when (.isFile file)
        (io/input-stream file)))))