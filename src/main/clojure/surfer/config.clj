(ns surfer.config
  (:require [environ.core :refer [env]])
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint])
  (:require [clojure.tools.logging :as log]))

(def CONFIG-PATH (or (env :config-path) "surfer-config.edn"))

(if (.exists (io/file CONFIG-PATH))
  (log/info "Loading surfer configuration from: " CONFIG-PATH)
  (let [config (edn/read-string (slurp (io/resource "surfer-config.edn")))]
    (log/info "Creating Surfer default configuration: " CONFIG-PATH)
    (let []
      (with-open [writer (io/writer CONFIG-PATH)]
         (binding [*out* writer]
           (pprint/pprint config))))
    (log/info "Default configuration created at: " CONFIG-PATH)))

(def LOADED-CONFIG (edn/read-string (slurp CONFIG-PATH)))

(def DEFAULT-CONFIG
  {:http-port 8080})

(def CONFIG (merge 
              DEFAULT-CONFIG
              LOADED-CONFIG))



