(ns surfer.core
  (:require
    [surfer system]
    [surfer.store :as store]
    [surfer.config :as config]
    [surfer.ckan :refer :all :as ckan]
    [surfer.utils :as utils :refer [port-available?]]
    [clj-http.client :as client]
    [surfer.system :refer [new-system]]
    [com.stuartsierra.component :as component]
    [clojure.tools.logging :as log]
    [clojure.data.json :as json]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn -main
  "Start a production system, unless a system is passed as argument (as in the dev-run task)."
  [& args]
  (let [port (config/CONFIG :http-port)]
    (if (port-available? port)
      (component/start (new-system))
      (log/error "Unable to start surfer, port unavailable:" port))))

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
      (doall (pmap #(do % (client/get (str "http://localhost:3030/metadata/" (rand-nth ks))))
                   (range 1000)))))
  )
