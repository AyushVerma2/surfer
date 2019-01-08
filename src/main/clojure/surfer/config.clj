(ns surfer.config
  (:require [environ.core :refer [env]])
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint])
  (:require [clojure.tools.logging :as log]))

(def CONFIG_PATH (or (env :config-path) "surfer-config.edn"))

(if (.exists (io/file CONFIG_PATH))
  (log/info "Loading surfer configuration from: " CONFIG_PATH)
  (let [config (edn/read-string (slurp (io/resource "surfer-config.edn")))]
    (log/info "Creating Surfer default configuration: " CONFIG_PATH)
    (let []
      (with-open [writer (io/writer CONFIG_PATH)]
         (binding [*out* writer]
           (pprint/pprint config))))
    (log/info "Default configuration created at: " CONFIG_PATH)))

(def CONFIG (edn/read-string (slurp CONFIG_PATH)))



