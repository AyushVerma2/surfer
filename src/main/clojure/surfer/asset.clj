(ns surfer.asset
  (:require [surfer.storage :as storage]
            [surfer.store :as store]
            [clojure.java.jdbc :as jdbc]
            [starfish.core :as sf]
            [clojure.data.json :as data.json]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [surfer.database :as database]))

(defn import!
  "Import, register and persist on disk, the asset file.

   `asset-path+metadata` is a tuple where the first item is the path of the
    file, and the second is the metadata map."
  [database storage-path asset-path+metadata & [{:keys [overwrite?]}]]
  (let [[dataset-path metadata] asset-path+metadata]
    (let [dataset (io/file dataset-path)

          metadata (merge metadata {:size (str (.length dataset))
                                    :contentHash (sf/digest (slurp dataset))})

          metadata-str (data.json/write-str metadata)

          metadata-sha (sf/digest metadata-str)

          exist? (seq (jdbc/query (database/db database) ["SELECT ID FROM METADATA WHERE ID=?" metadata-sha]))

          store! (fn []
                   (store/register-asset (database/db database) metadata-str)
                   (storage/save storage-path metadata-sha dataset)
                   [metadata-sha metadata-str])]
      (if exist?
        (when overwrite?
          (store!))
        (store!)))))

(defn import-datasets! [database storage-path datasets-path]
  (let [dataset-path->metadata (-> (io/file datasets-path)
                                   (slurp)
                                   (edn/read-string))]
    (->> dataset-path->metadata
         (mapv #(import! database storage-path % {:overwrite? true}))
         (remove nil?))))
