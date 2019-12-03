(ns surfer.asset
  (:require [surfer.storage :as storage]
            [surfer.store :as store]
            [clojure.java.jdbc :as jdbc]
            [starfish.core :as sf]
            [clojure.data.json :as data.json]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [byte-streams]))

(defn import!
  "Import, register and persist on disk, the asset file.

   `asset-path+metadata` is a tuple where the first item is the path of the
    file, and the second is the metadata map."
  [db storage-path asset-path+metadata & [{:keys [overwrite?]}]]
  (let [[asset-path metadata] asset-path+metadata]
    (let [file (io/file asset-path)

          metadata (merge metadata {:size (str (.length file))
                                    :contentHash (sf/digest (byte-streams/to-byte-array file))})

          metadata-str (data.json/write-str metadata)

          metadata-sha (sf/digest metadata-str)

          exist? (seq (jdbc/query db ["SELECT ID FROM METADATA WHERE ID=?" metadata-sha]))

          store! (fn []
                   (store/register-asset db metadata-str)
                   (storage/save storage-path metadata-sha file)
                   [metadata-sha metadata-str])]
      (if exist?
        (when overwrite?
          (store!))
        (store!)))))

(defn import-edn! [db storage-path datasets-path]
  (let [dataset-path->metadata (-> (io/file datasets-path)
                                   (slurp)
                                   (edn/read-string))]
    (->> dataset-path->metadata
         (mapv #(import! db storage-path % {:overwrite? true}))
         (remove nil?))))
