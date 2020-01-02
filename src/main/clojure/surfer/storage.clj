(ns surfer.storage
  (:require [clojure.java.io :as io]
            [surfer.utils :as utils]
            [clojure.string :as str]
            [starfish.core :as sf]
            [byte-streams])
  (:import (java.io File InputStream)))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn storage-path-exist? [path]
  (let [^File file (io/file path)]
    (.isDirectory file)))

(defn mkdir-storage-path [path]
  (let [^File file (io/file path)]
    (.mkdirs file)))

(defn asset-path [storage-path asset-id]
  (str storage-path "/" asset-id ".ocb"))

(defn get-asset-path
  "Gets a storage path given a valid Asset ID."
  [storage-path id]
  (when (str/blank? storage-path)
    (throw (ex-info "Can't get asset path. Nil or empty storage path."
                    {:storage-path storage-path
                     :asset-id id})))

  (when (str/blank? id)
    (throw (ex-info "Can't get asset path. Nil or empty Asset ID."
                    {:storage-path storage-path
                     :asset-id id})))

  (when-not (utils/valid-asset-id? id)
    (throw (ex-info "Can't get asset path. Invalid Asset ID."
                    {:storage-path storage-path
                     :asset-id id})))

  (asset-path storage-path id))

(defn save
  "Saves data to the storage location for a given asset.

   Data may be an InputStream, Reader, File, byte[], char[], or String."
  [storage-path asset-id data]
  (let [asset-path (get-asset-path storage-path asset-id)]

    (when-not (storage-path-exist? storage-path)
      (mkdir-storage-path storage-path))

    (io/copy data (io/file asset-path))))

(defn asset-input-stream
  "Gets an input stream for the specified asset ID.

   Returns null is the asset data does not exist.

   Should be used inside with-open to ensure the InputStream is properly
   closed."
  ^InputStream [storage-path id]
  (let [path (get-asset-path storage-path id)
        ^File file (io/file path)]
    (io/input-stream file)))

(defn hash-check
  "`object` is anything which can be converted to byte-array (e.g., File, InputStream).
   `expected-hash` is the expected hash for `object`."
  [object expected-hash]
  (let [actual-hash (sf/digest (byte-streams/to-byte-array object))]
    {:matches? (= expected-hash actual-hash)
     :expected expected-hash
     :actual actual-hash}))