(ns surfer.storage
  (:require [clojure.java.io :as io])
  (:require [surfer.utils :as utils]
            [surfer.env :as env])
  (:import [java.io File DataInputStream InputStream]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn storage-path [env]
  (env/storage-config env [:path]))

(defn storage-path-exist? [path]
  (let [^File file (io/file path)]
    (.isDirectory file)))

(defn mkdir-storage-path [path]
  (let [^File file (io/file path)]
    (.mkdir file)))

(defn get-asset-path
  "Gets a storage path given a valid Asset ID."
  [env asset-id]
  (let [storage-path (storage-path env)]
    (when-not (storage-path-exist? storage-path)
      (mkdir-storage-path storage-path))

    (str storage-path "/" asset-id ".ocb")))

(defn save
  "Saves data to the storage location for a given asset.

   Data may be an InputStream, Reader, File, byte[], char[], or String."
  [env asset-id data]
  (when-not (utils/valid-asset-id? asset-id)
    (throw (IllegalArgumentException. (str "Asset ID not valid [" asset-id "]"))))

  (let [path (get-asset-path env asset-id)
        file (io/file path)]
    (io/copy data file)))

(defn load-stream 
  "Gets an input stream for the specified asset ID.

   Returns null is the asset data does not exist.

   Should be used inside with-open to ensure the InputStream is properly
   closed."
  [env asset-id]
    (when-not (utils/valid-asset-id? asset-id)
      (throw (IllegalArgumentException. (str "Asset ID not valid [" asset-id "]"))))

    (let [path (get-asset-path env asset-id)
          ^File file (io/file path)]
      (when (.isFile file)
        (io/input-stream file))))