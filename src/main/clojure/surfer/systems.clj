(ns surfer.systems
  (:require
    [system.core :refer [defsystem]]
    [system.components
      [http-kit :refer [new-web-server]]
   ;; [h2 :refer [new-h2-database DEFAULT-MEM-SPEC DEFAULT-DB-SPEC]]
     ]
    [surfer.handler :refer [app]]
    [surfer.config :refer [CONFIG]]
    [surfer.store :as store])
  (:require [clojure.tools.logging :as log]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(def PORT (Integer/parseInt (str (or (CONFIG :http-port) 8080))))

(log/debug "Server configured for port:" PORT)

(defsystem base-system
  [;; :db (new-h2-database (select-database env) #(create-table! {} {:connection %}))
   :web (new-web-server 
          PORT
          #(surfer.handler/app %) ;; hack because system.components.http-kit need a fn? to use a handler directly
          )])

