(ns surfer.core
  (:require 
    [surfer systems]
    [surfer.store :as store]
    [surfer.ckan :refer :all :as ckan]
    [clj-http.client :as client]
    [surfer.systems :refer [base-system]]
    [system.repl :refer [set-init! go start reset stop]]
    [clojure.data.json :as json]))


(defn -main
  "Start a production system, unless a system is passed as argument (as in the dev-run task)."
  [& args]
  (let [system (or (first args) #'base-system)]
    (set-init! system)
    (start)))


(comment
  (-main) ;; launch the app
  (reset) ;; stop and restart the app

  (let [repo "http://demo.ckan.org"
        packs (ckan/package-list repo)
        some-packs (take 20 (shuffle packs))]
    (doall (pmap #(import-package repo % ) some-packs)))
  
  (time 
    (let [ks (store/all-keys)] 
      (doall (pmap #(do % (client/get (str "http://localhost:8080/metadata/" (rand-nth ks)))) 
                   (range 1000)))))
  )