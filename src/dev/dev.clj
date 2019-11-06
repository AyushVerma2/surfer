(ns dev
  (:require [surfer.store :as store]
            [surfer.config :as config]
            [surfer.system :as system]
            [com.stuartsierra.component.repl :refer [set-init reset start stop system]]))

(set-init system/new-system)

