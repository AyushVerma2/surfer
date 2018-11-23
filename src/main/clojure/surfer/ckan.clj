(ns surfer.ckan
  (:require
    [clj-http.client :as client]
    [surfer.store :as store]
    [surfer.utils :as utils]
    [clojure.data.json :as json]))

(def ^:dynamic *import-userid* nil)

(defn convert-meta
  "Converts metadata from a CKAN asset to a Ocean Asset"
  ([pack]
    {:name (:name pack)
     :license (:license_title pack)
}))

(defn api-call [url]
  (let [body (:body (client/get url))
        json (json/read-str body :key-fn keyword)]
    (json :result)))

(defn tag-list 
  "Gets a list of tags from a CKAN repo"
  ([repo]
    (let [url (str repo "/api/3/action/tag_list")]
      (api-call url)))) 

(defn package-list 
  "Gets a list of packages from a CKAN repo"
  ([repo]
    (let [url (str repo "/api/3/action/package_list")]
      (api-call url)))) 

(defn package-show 
  "Gets CKAN package metadata for a given package ID"
  ([repo package]
    (let [url (str repo "/api/3/action/package_show?id=" package)]
      (api-call url))))

(defn import-package 
  "Import a package from the CKAN repo and list on the marketplace"
  ([repo package-name]
    (let [pdata (package-show repo package-name)
          odata (convert-meta pdata)
          assetid (store/register-asset (json/write-str odata))]
      (store/create-listing 
        {:assetid assetid
         :info (utils/remove-nil-values {:title (:name odata)
                                         :description (:description odata)
                                         :source (str "This asset was automatically generated by import from the CKAN repository " repo " at " (str (java.time.LocalDateTime/now)))})
         :userid (or *import-userid* (throw (IllegalArgumentException. "*import-userid* must be bound to import CKAN asset")))})))) 

(defn import-packages [repo package-names]
  (doall (pmap #(import-package repo % ) package-names)))

(defn import-all [repo]
  (let [packages (package-list repo)]
    (doseq [p packages] (import-package repo p))
    (println (str (count packages) " packages imported")))) 

(defn get-data 
  "Gets the data for a given resource"
  [resource]
  ((client/get (resource "url")) :body))