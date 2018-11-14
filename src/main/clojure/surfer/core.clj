(ns surfer.core
  (:require 
    [surfer systems]
    [surfer.store :as store]
    [surfer.ckan :refer :all :as ckan]
    [clj-http.client :as client]
    [surfer.systems :refer [base-system]]
    [system.repl :refer [set-init! go start reset stop]]
    [clojure.data.json :as json]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn -main
  "Start a production system, unless a system is passed as argument (as in the dev-run task)."
  [& args]
  (let [system #'base-system]
    (set-init! system)
    (start)))

(comment
  (-main) ;; launch the app
  (reset) ;; stop and restart the app
  
  (package-list "https://data.gov.uk")
  (package-list "https://data.gov.sg")

  (let [repo "https://data.gov.uk"
        packs (ckan/package-list repo)
        some-packs (take 20 (shuffle packs))]
    (import-packages repo some-packs))
  
  ;; timing test for 1000 metadata requests. Currently about 2000 req/s
  (time 
    (let [ks (store/all-keys)] 
      (doall (pmap #(do % (client/get (str "http://localhost:8080/metadata/" (rand-nth ks)))) 
                   (range 1000)))))
  )