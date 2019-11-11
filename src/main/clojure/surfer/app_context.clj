(ns surfer.app-context)

(defn new-context [env database]
  {:env env
   :database database})

(defn database [app-context]
  (:database app-context))

(defn env [app-context]
  (:env app-context))
