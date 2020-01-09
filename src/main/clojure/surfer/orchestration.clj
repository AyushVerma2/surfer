(ns surfer.orchestration
  (:require [clojure.alpha.spec :as s]
            [com.stuartsierra.dependency :as dep]
            [surfer.store :as store]
            [starfish.core :as sf]
            [surfer.invokable :as invoke]
            [surfer.app-context :as app-context]
            [clojure.string :as str]
            [clojure.walk :as walk]))

;; -- ORCHESTRATION EDGE

(s/def :orchestration-edge/source
  (s/and string? #(not (str/blank? %))))

(s/def :orchestration-edge/target
  (s/and string? #(not (str/blank? %))))

(s/def :orchestration-edge/ports
  (s/coll-of keyword? :kind vector? :count 2))

(s/def :orchestration-edge/schema
  (s/schema [:orchestration-edge/source
             :orchestration-edge/target
             :orchestration-edge/ports]))

(s/def :orchestration-edge/orchestration-edge
  (s/select :orchestration-edge/schema [*]))


;; -- ORCHESTRATION

(s/def :orchestration/id
  (s/and string? #(not (str/blank? %))))

(s/def :orchestration/children
  (s/map-of (s/and string? #(not (str/blank? %)))
            (s/and string? #(not (str/blank? %)))
            :min-count 1))

(s/def :orchestration/edges
  (s/coll-of :orchestration-edge/orchestration-edge :min-count 1))

(s/def :orchestration/schema
  (s/schema [:orchestration/id
             :orchestration/children
             :orchestration/edges]))

(s/def :orchestration/orchestration
  (s/select :orchestration/schema [*]))

;; --

(defn dep13->orchestration
  "Returns an Orchestration entity from a DEP 13 format."
  [m]
  {:orchestration/id (:id m)
   :orchestration/children (walk/stringify-keys (:children m))
   :orchestration/edges (map
                          (fn [{:keys [source sourcePort target targetPort]}]
                            {:orchestration-edge/source source
                             :orchestration-edge/target target
                             :orchestration-edge/ports [(keyword sourcePort) (keyword targetPort)]})
                          (:edges m))})

(defn dependency-graph [orchestration]
  (let [edges (remove
                (fn [{:orchestration-edge/keys [source target]}]
                  (or (= source (:orchestration/id orchestration))
                      (= target (:orchestration/id orchestration))))
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

(defn dependency-ports
  "Returns ports where nid (target) and dependency-nid (source) are connected."
  [orchestration nid dependency-nid]
  (->> (edges= orchestration #:orchestration-edge{:source dependency-nid
                                                  :target nid})
       (map :orchestration-edge/ports)))

(defn root-source-edges [orchestration]
  (filter
    (fn [{:keys [source]}]
      (= (:id orchestration) source))
    (:edges orchestration)))

(defn root-target-edges [orchestration]
  (filter
    (fn [{:keys [target]}]
      (= (:id orchestration) target))
    (:edges orchestration)))

(defn invokable-params [orchestration parameters process nid]
  (let [root-source-edges (filter
                            (fn [{:keys [target]}]
                              (= nid target))
                            (root-source-edges orchestration))

        params (if (seq root-source-edges)
                 (reduce
                   (fn [params {:keys [ports]}]
                     (let [[o-in n-in] ports]
                       (assoc params n-in (get parameters o-in))))
                   {}
                   root-source-edges)
                 (some->> (get-in (dependency-graph orchestration) [:dependencies nid])
                          (map
                            (fn [dependency-nid]
                              (let [dependency-output (fn [dependency-nid output-key]
                                                        (get-in process [dependency-nid :output output-key]))

                                    make-params (fn [params [port-out port-in]]
                                                  (assoc params port-in (dependency-output dependency-nid port-out)))]
                                (reduce
                                  make-params
                                  {}
                                  (dependency-ports orchestration nid dependency-nid)))))
                          (apply merge)))]
    (or params {})))

(defn output-mapping
  "Mapping of Operation's output to Orchestration's output.

   Orchestration's output may provide less than Operation's output."
  [orchestration process]
  (->> (root-target-edges orchestration)
       (map
         (fn [{:keys [source ports]}]
           (let [[n-out o-out] ports]
             [o-out (get-in process [source :output n-out])])))
       (into {})))

(defn execute [app-context orchestration & [params]]
  (let [nodes (dep/topo-sort (dependency-graph orchestration))

        process (reduce
                  (fn [process nid]
                    (let [aid (get-in orchestration [:children nid])

                          metadata (-> (app-context/db app-context)
                                       (store/get-metadata aid {:key-fn keyword}))

                          invokable (invoke/invokable-operation app-context metadata)

                          invokable-params (invokable-params orchestration params process nid)]
                      (assoc process nid {:input invokable-params
                                          :output (sf/invoke-result invokable invokable-params)})))
                  {"Root" {:input params}}
                  nodes)

        output (output-mapping orchestration process)

        ;; Update Orchestration's `output`
        ;; See the process reducer above - `input` is already set for the Orchestration
        process (assoc-in process ["Root" :output] output)]
    {:topo nodes
     :process process}))

(defn results [{:keys [process]}]
  {:status "succeeded"
   :results (get-in process ["Root" :output])
   :children (->> process
                  (map
                    (fn [[k v]]
                      [k {:status "succeeded"
                          :results (:output v)}]))
                  (into {}))})