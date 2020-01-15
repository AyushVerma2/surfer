(ns surfer.orchestration
  (:require [clojure.alpha.spec :as s]
            [com.stuartsierra.dependency :as dep]
            [surfer.store :as store]
            [starfish.core :as sf]
            [surfer.invokable :as invoke]
            [surfer.app-context :as app-context]
            [clojure.string :as str]))

;; -- ORCHESTRATION EDGE

(s/def :orchestration-edge/source
  (s/and string? #(not (str/blank? %))))

(s/def :orchestration-edge/source-port
  keyword?)

(s/def :orchestration-edge/target
  (s/and string? #(not (str/blank? %))))

(s/def :orchestration-edge/target-port
  keyword?)

(s/def :orchestration-edge/ports
  (s/coll-of keyword? :kind vector? :count 2))

(s/def :orchestration-edge/schema
  (s/schema [:orchestration-edge/source
             :orchestration-edge/source-port
             :orchestration-edge/target
             :orchestration-edge/target-port]))

(s/def :orchestration-edge/source-root
  (s/select :orchestration-edge/schema [:orchestration-edge/source-port
                                        :orchestration-edge/target
                                        :orchestration-edge/target-port]))

(s/def :orchestration-edge/target-root
  (s/select :orchestration-edge/schema [:orchestration-edge/source
                                        :orchestration-edge/source-port
                                        :orchestration-edge/target-port]))

(s/def :orchestration-edge/node-to-node
  (s/select :orchestration-edge/schema [*]))

(s/def :orchestration-edge/edge
  (s/or :source-root :orchestration-edge/source-root
        :target-root :orchestration-edge/target-root
        :node-to-node :orchestration-edge/node-to-node))

;; -- ORCHESTRATION CHILD

(s/def :orchestration-child/id
  (s/and string? #(not (str/blank? %))))

(s/def :orchestration-child/did
  (s/and string? #(not (str/blank? %))))

(s/def :orchestration-child/schema
  (s/schema [:orchestration-child/id
             :orchestration-child/did]))

(s/def :orchestration-child/child
  (s/select :orchestration-child/schema [*]))


;; -- ORCHESTRATION

(s/def :orchestration/id
  (s/and string? #(not (str/blank? %))))

(s/def :orchestration/children
  (s/map-of (s/and string? #(not (str/blank? %))) :orchestration-child/child :min-count 1))

(s/def :orchestration/edges
  (s/coll-of :orchestration-edge/edge :min-count 1))

(s/def :orchestration/schema
  (s/schema [:orchestration/id
             :orchestration/children
             :orchestration/edges]))

(s/def :orchestration/orchestration
  (s/select :orchestration/schema [*]))


;; ORCHESTRATION INVOCATION

(s/def :orchestration-invocation/node
  (s/and string? #(not (str/blank? %))))

(s/def :orchestration-invocation/status
  #{:orchestration-invocation.status/scheduled
    :orchestration-invocation.status/running
    :orchestration-invocation.status/succeeded
    :orchestration-invocation.status/failed
    :orchestration-invocation.status/cancelled})

(s/def :orchestration-invocation/input
  (s/map-of keyword? any?))

(s/def :orchestration-invocation/output
  (s/map-of keyword? any?))

(s/def :orchestration-invocation/error
  (fn [x]
    (instance? Exception x)))

(s/def :orchestration-invocation/schema
  (s/schema [:orchestration-invocation/node
             :orchestration-invocation/status
             :orchestration-invocation/input
             :orchestration-invocation/output
             :orchestration-invocation/error]))

(s/def :orchestration-invocation/scheduled
  (s/and (s/select :orchestration-invocation/schema [:orchestration-invocation/node
                                                     :orchestration-invocation/status])
         #(= :orchestration-invocation.status/scheduled (:orchestration-invocation/status %))))

(s/def :orchestration-invocation/running
  (s/and (s/select :orchestration-invocation/schema [:orchestration-invocation/node
                                                     :orchestration-invocation/status
                                                     :orchestration-invocation/input])
         #(= :orchestration-invocation.status/running (:orchestration-invocation/status %))))

(s/def :orchestration-invocation/cancelled
  (s/and (s/select :orchestration-invocation/schema [:orchestration-invocation/node
                                                     :orchestration-invocation/status])
         #(= :orchestration-invocation.status/cancelled (:orchestration-invocation/status %))))

(s/def :orchestration-invocation/succeeded
  (s/and (s/select :orchestration-invocation/schema [:orchestration-invocation/node
                                                     :orchestration-invocation/status
                                                     :orchestration-invocation/input
                                                     :orchestration-invocation/output])
         #(= :orchestration-invocation.status/succeeded (:orchestration-invocation/status %))))

(s/def :orchestration-invocation/failed
  (s/and (s/select :orchestration-invocation/schema [:orchestration-invocation/node
                                                     :orchestration-invocation/status
                                                     :orchestration-invocation/input
                                                     :orchestration-invocation/error])
         #(= :orchestration-invocation.status/failed (:orchestration-invocation/status %))))

(s/def :orchestration-invocation/completed
  (s/or :cancelled :orchestration-invocation/cancelled
        :succeeded :orchestration-invocation/succeeded
        :failed :orchestration-invocation/failed))

;; ORCHESTRATION EXECUTION

(s/def :orchestration-execution/topo
  (s/coll-of string?))

(s/def :orchestration-execution/process
  (s/map-of (s/and string? #(not (str/blank? %)))
            (s/or :running :orchestration-invocation/running
                  :completed :orchestration-invocation/completed)))

;; --

(defn dep13->orchestration
  "Returns an Orchestration entity from a DEP 13 format."
  [m]
  {:orchestration/id (:id m)
   :orchestration/children (reduce
                             (fn [children [id {:keys [did]}]]
                               (assoc children (name id) #:orchestration-child {:id (name id)
                                                                                :did did}))
                             {}
                             (:children m))
   :orchestration/edges (map
                          (fn [{:keys [source sourcePort target targetPort]}]
                            (merge {:orchestration-edge/source-port (keyword sourcePort)
                                    :orchestration-edge/target-port (keyword targetPort)}
                                   (when source
                                     {:orchestration-edge/source source})
                                   (when target
                                     {:orchestration-edge/target target})))
                          (:edges m))})

(defn source-root-edge? [orchestration edge]
  (or (nil? (:orchestration-edge/source edge))
      (= (:orchestration/id orchestration)
         (:orchestration-edge/source edge))))

(defn target-root-edge? [orchestration edge]
  (or (nil? (:orchestration-edge/target edge))
      (= (:orchestration/id orchestration)
         (:orchestration-edge/target edge))))

(defn source-root-edges [orchestration]
  (filter (partial source-root-edge? orchestration) (:orchestration/edges orchestration)))

(defn target-root-edges [orchestration]
  (filter (partial target-root-edge? orchestration) (:orchestration/edges orchestration)))

(defn dependency-graph [orchestration]
  (let [edges (remove
                (fn [edge]
                  (or (source-root-edge? orchestration edge)
                      (target-root-edge? orchestration edge)))
                (:orchestration/edges orchestration))]
    (reduce
      (fn [graph {:orchestration-edge/keys [source target]}]
        (dep/depend graph target source))
      (dep/graph)
      edges)))

(defn edges=
  "Returns edges where nid (target) and dependency-nid (source) are connected.

   It's possible to have n edges from same source and target. That's the case
   whenever source is 'reused' to connect to a different port."
  [orchestration edge]
  (filter
    (fn [e]
      (= edge (select-keys e (keys edge))))
    (:orchestration/edges orchestration)))

(defn invokable-params [orchestration parameters process nid]
  (let [edges-input-redirect (filter
                               (fn [edge]
                                 (= (:orchestration-edge/target edge) nid))
                               (source-root-edges orchestration))

        params (if (seq edges-input-redirect)
                 (reduce
                   (fn [params {:orchestration-edge/keys [source-port target-port]}]
                     (assoc params target-port (get parameters source-port)))
                   {}
                   edges-input-redirect)
                 (some->> (get-in (dependency-graph orchestration) [:dependencies nid])
                          (map
                            (fn [dependency-nid]
                              (reduce
                                (fn [params {:orchestration-edge/keys [source-port target-port]}]
                                  (assoc params target-port (get-in process [dependency-nid :orchestration-invocation/output source-port])))
                                {}
                                (edges= orchestration #:orchestration-edge{:source dependency-nid
                                                                           :target nid}))))
                          (apply merge)))]
    (or params {})))

(defn output-mapping
  "Mapping of Operation's output to Orchestration's output.

   Orchestration's output may provide less than Operation's output."
  [orchestration process]
  (reduce
    (fn [output {:orchestration-edge/keys [source source-port target-port]}]
      (assoc output target-port (get-in process [source :orchestration-invocation/output source-port])))
    {}
    (target-root-edges orchestration)))

(defn prepare [nodes]
  (reduce
    (fn [process nid]
      (assoc process nid {:orchestration-invocation/node nid
                          :orchestration-invocation/status :orchestration-invocation.status/scheduled}))
    {"Root" {:orchestration-invocation/node "Root"
             :orchestration-invocation/status :orchestration-invocation.status/scheduled}}
    nodes))

(defn update-to-running [process nid input]
  (update process nid (fn [orchestration-invocation]
                        (merge orchestration-invocation #:orchestration-invocation {:status :orchestration-invocation.status/running
                                                                                    :input input}))))

(defn update-to-succeeded [process nid output]
  (update process nid (fn [orchestration-invocation]
                        (merge orchestration-invocation #:orchestration-invocation {:status :orchestration-invocation.status/succeeded
                                                                                    :output output}))))

(defn update-to-failed [process nid error]
  (update process nid (fn [orchestration-invocation]
                        (merge orchestration-invocation #:orchestration-invocation {:status :orchestration-invocation.status/failed
                                                                                    :error error}))))

(defn update-to-cancelled [process nid]
  (update process nid (fn [orchestration-invocation]
                        (merge orchestration-invocation #:orchestration-invocation {:status :orchestration-invocation.status/cancelled}))))

(defn cancel-scheduled [process]
  (reduce-kv
    (fn [process nid {:orchestration-invocation/keys [status] :as orchestration-invocation}]
      (let [status (if (= :orchestration-invocation.status/scheduled status)
                     :orchestration-invocation.status/cancelled
                     status)]
        (assoc process nid (assoc orchestration-invocation :orchestration-invocation/status status))))
    {}
    process))

(defn execute-sync [app-context orchestration & [params]]
  (let [nodes (dep/topo-sort (dependency-graph orchestration))

        root-nid "Root"

        root-process (-> (prepare nodes)
                         (update-to-running root-nid params))

        process (reduce
                  (fn [process nid]
                    (let [aid (get-in orchestration [:orchestration/children nid :orchestration-child/did])

                          metadata (-> (app-context/db app-context)
                                       (store/get-metadata aid {:key-fn keyword}))

                          invokable (invoke/invokable-operation app-context metadata)

                          invokable-params (invokable-params orchestration params process nid)

                          process (update-to-running process nid invokable-params)]
                      (try
                        (update-to-succeeded process nid (sf/invoke-result invokable invokable-params))
                        (catch Exception e
                          (reduced (-> process
                                       (update-to-failed nid e)
                                       (update-to-failed root-nid e)
                                       (cancel-scheduled)))))))
                  root-process
                  nodes)

        root-failed? (= :orchestration-invocation.status/failed
                        (get-in process [root-nid :orchestration-invocation/status]))

        process (if root-failed?
                  process
                  (update-to-succeeded process root-nid (output-mapping orchestration process)))]
    {:orchestration-execution/topo nodes
     :orchestration-execution/process process}))

(defn results [{:orchestration-execution/keys [process]}]
  {:status (name (get-in process ["Root" :orchestration-invocation/status]))
   :results (get-in process ["Root" :orchestration-invocation/output])
   :children (reduce-kv
               (fn [children k v]
                 (assoc children k {:status (name (:orchestration-invocation/status v))
                                    :results (:orchestration-invocation/output v)}))
               {}
               process)})