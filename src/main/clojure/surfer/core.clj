(ns surfer.core
  (:require 
    [surfer systems]
    [clj-http.client :as client]
    [surfer.systems :refer [base-system]]
    [system.repl :refer [set-init! go start reset stop]]
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

(defn get-data 
  "Gets the data for a given resource"
  [resource]
  ((client/get (resource "url")) :body))

(defn -main
  "Start a production system, unless a system is passed as argument (as in the dev-run task)."
  [& args]
  (let [system (or (first args) #'base-system)]
    (set-init! system)
    (start)))


(comment
  (-main) ;; launch the app
  (reset) ;; stop and restart the app

  
  )