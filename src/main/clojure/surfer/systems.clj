(ns surfer.systems
  (:require
    [system.core :refer [defsystem]]
    [system.components
      [http-kit :refer [new-web-server]]
   ;; [h2 :refer [new-h2-database DEFAULT-MEM-SPEC DEFAULT-DB-SPEC]]
     ]
    [environ.core :refer [env]]
    [surfer.handler :refer [app]]
    [surfer.store :as store]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(if (env :surfer-create-db) (store/create-db!) )

(defsystem base-system
  [;; :db (new-h2-database (select-database env) #(create-table! {} {:connection %}))
   :web (new-web-server 
          (int (or (env :http-port) 8080)) 
          #(surfer.handler/app %) ;; hack because system.components.http-kit need a fn? to use a handler directly
          )])

