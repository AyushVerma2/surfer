(ns surfer.orchestration
  (:require [com.stuartsierra.dependency :as dep]
            [surfer.store :as store]
            [starfish.core :as sf]
            [surfer.invoke :as invoke]
            [surfer.app-context :as app-context]))

(defn dependency-graph [orchestration]
  (let [edges (remove
                (fn [{:keys [source target]}]
                  (or (= source (:id orchestration))
                      (= target (:id orchestration))))
                (:edges orchestration))]
    (reduce
      (fn [graph {:keys [source target]}]
        (dep/depend graph target source))
      (dep/graph)
      edges)))

(defn dependency-edges
  "Returns edges where nid (target) and dependency-nid (source) are connected.

   It's possible to have n edges from same source and target. That's the case
   whenever source is 'reused' to connect to a different port."
  [orchestration nid dependency-nid]
  (filter
    (fn [{:keys [source target]}]
      (and (= dependency-nid source)
           (= nid target)))
    (:edges orchestration)))

(defn dependency-ports
  "Returns ports where nid (target) and dependency-nid (source) are connected."
  [orchestration nid dependency-nid]
  (->> (dependency-edges orchestration nid dependency-nid)
       (map :ports)))

(defn params [orchestration process nid]
  (let [dependency-graph (dependency-graph orchestration)
        params (some->> (get (:dependencies dependency-graph) nid)
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
                        (apply merge))]
    (or params {})))

(defn execute [app-context orchestration]
  (let [nodes (dep/topo-sort (dependency-graph orchestration))

        process (reduce
                  (fn [process nid]
                    (let [aid (get-in orchestration [:children nid])

                          metadata (-> (app-context/db app-context)
                                       (store/get-metadata aid {:key-fn keyword}))

                          invokable (invoke/invokable-operation app-context metadata)

                          params (params orchestration process nid)]
                      (assoc process nid {:input params
                                          :output (sf/invoke-result invokable params)})))
                  {}
                  nodes)]
    {:topo nodes
     :process process}))

(defn results [{:keys [topo process]}]
  {:status "succeeded"
   :results (get-in process [(last topo) :output])
   :children (->> process
                  (map
                    (fn [[k v]]
                      [k {:status "succeeded"
                          :results (:output v)}]))
                  (into {}))})