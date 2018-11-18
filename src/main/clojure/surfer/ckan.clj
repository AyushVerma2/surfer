(ns surfer.ckan
  (:require
    [clj-http.client :as client]
    [surfer.store :as store]
    [clojure.data.json :as json]))

(defn api-call [url]
  (let [body (:body (client/get url))
        json (json/read-str body)]
    (json "result")))

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
  [repo package]
  (let [url (str repo "/api/3/action/package_show?id=" package)]
    (api-call url)))

(defn import-package [repo package-name]
  (let [pdata (package-show repo package-name)]
    (store/register-asset (json/write-str pdata)))) 

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