(ns dev
  (:require [surfer.store :as store]
            [surfer.config :as config]
            [surfer.systems :as systems]
            [system.repl :refer [set-init! go reset start stop]]))

(set-init! #'systems/system)

