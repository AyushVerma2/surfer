(ns surfer.app-context)

(defn new-context [env database]
  {:env env
   :database database})

(defn database
  "Database Component

   Abstraction over the concrete database implementation.

   See `surfer.component.web-server` namespace."
  [app-context]
  (:database app-context))

(defn env
  "Env component

   See `surfer.component.web-server` namespace."
  [app-context]
  (:env app-context))
