(ns surfer.systems
  (:require
    [system.core :refer [defsystem]]
    [system.components
      [http-kit :refer [new-web-server]]
   ;; [h2 :refer [new-h2-database DEFAULT-MEM-SPEC DEFAULT-DB-SPEC]]
     ]
    [environ.core :refer [env]]
    [surfer.handler :refer [app]]))

(defsystem base-system
  [;; :db (new-h2-database (select-database env) #(create-table! {} {:connection %}))
   :web (new-web-server (Integer. (or (env :http-port) 8080)) app)])

