(ns surfer.config
  (:require [surfer.utils :as utils])
  (:require [environ.core :refer [env]])
  (:require [starfish.core :as sf]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint])
  (:require [clojure.tools.logging :as log])
  (:require [cemerick.friend [credentials :as creds]])
  (:import [java.io File]))

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

(def PORT
  (:http-port CONFIG))

(def DID 
  (sf/did (or (:did (:agent CONFIG)) (sf/random-did)))) 

(def LOCAL-URL
  (str "http://localhost:" PORT))

(def LOCAL-DDO
  {:id (str DID)
   :service 
    [{:type "Ocean.Invoke.v1"
      :serviceEndpoint (str LOCAL-URL "/api/v1")}
     {:type "Ocean.Meta.v1"
      :serviceEndpoint (str LOCAL-URL "/api/v1/meta")}
     {:type "Ocean.Auth.v1"
      :serviceEndpoint (str LOCAL-URL "/api/v1/auth")}
     {:type "Ocean.Storage.v1"
      :serviceEndpoint (str LOCAL-URL "/api/v1/assets")}]})

(def DDO
  {:id (str DID)}) 

(def USER-CONFIG-FILE (get-in CONFIG [:security :user-config]))

(defn load-userfile
  [^File userfile]
  (let [udata (edn/read-string (slurp userfile))]
    ;; we need to hash the passwords. Do this here to avoid potential leaks
    ;; by storing plaintext passwords in memory.
    (seq (map (fn [user]
                (assoc user :password (creds/hash-bcrypt (:password user))))
              udata))))

(def DEFAULT-USER-DATA
  [{:id "789e3f52da1020b56981e1cb3ee40c4df72103452f0986569711b64bdbdb4ca6"
    :username "test"
    :password (utils/new-random-id 16)}
   {:id "9671e2c4dabf1b0ea4f4db909b9df3814ca481e3d110072e0e7d776774a68e0d"
    :username "Aladdin"
    :password (utils/new-random-id 16)}])

(def USER-CONFIG
  (try
    (when-let [userfile (io/file USER-CONFIG-FILE)]
      (when (not (.exists userfile))
        ;; a config file is specified, but doesn't exist, so we create a default one
        (log/info "User configuration file specified but not found")
        (with-open [writer (io/writer USER-CONFIG-FILE)]
          (binding [*out* writer]
            (pprint/pprint DEFAULT-USER-DATA))
          (log/info (str "Created default user configuration file: " USER-CONFIG-FILE))))
      (load-userfile userfile))

  (catch Throwable t
    (.printStackTrace t)
    (log/error (str "Problem loading users from [" USER-CONFIG-FILE "] : " t)))))
