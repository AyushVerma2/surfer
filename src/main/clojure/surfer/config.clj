(ns surfer.config
  (:require [environ.core :refer [env]])
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint])
  (:require [clojure.tools.logging :as log])
  (:require [cemerick.friend [credentials :as creds]]))

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

(def USER-CONFIG-FILE (get-in CONFIG [:security :user-config]))

(def USER-CONFIG 
  (try
    (when-let [userfile (io/file USER-CONFIG-FILE)]
    ;; we have a user config filename, so try to load accordingly
      (when (.exists userfile)
        (let [udata (edn/read-string (slurp userfile))]
            ;; we need to hash the passwords. Do this here to avoid potential leaks
            ;; by storing plaintext passwords in memory.
            (seq (map (fn [user]
                       (assoc user :password (creds/hash-bcrypt (:password user)))
                       )
                     udata)))))
    
  (catch Throwable t
    (.printStackTrace t) 
    (log/error (str "Problem loading users from [" USER-CONFIG-FILE "] : " t)))))



