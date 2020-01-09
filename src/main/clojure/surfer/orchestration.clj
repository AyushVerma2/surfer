(ns surfer.orchestration
  (:require [com.stuartsierra.dependency :as dep]
            [surfer.store :as store]
            [starfish.core :as sf]
            [surfer.invokable :as invoke]
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