(ns surfer.orchestration
  (:require [com.stuartsierra.dependency :as dep]
            [surfer.store :as store]
            [starfish.core :as sf]
            [surfer.invoke :as invoke]
            [surfer.database :as database]
            [surfer.system :as system]))

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

(defn execute [context orchestration]
  (let [{:keys [dependencies] :as dependency-graph} (dependency-graph orchestration)

        nodes (dep/topo-sort dependency-graph)

        process (reduce
                  (fn [process nid]
                    (let [aid (get-in orchestration [:children nid])

                          metadata (-> (system/context->db context)
                                       (store/get-metadata aid {:key-fn keyword}))

                          invokable (invoke/invokable-operation context metadata)

                          params (when (seq (get-in metadata [:operation :params]))
                                   (some->> (get dependencies nid)
                                            (map
                                              (fn [dependency-nid]
                                                (reduce
                                                  (fn [params [port-out port-in]]
                                                    (assoc params port-in (get-in process [dependency-nid :output port-out])))
                                                  {}
                                                  (dependency-ports orchestration nid dependency-nid))))
                                            (apply merge)))
                          params (or params {})]
                      ;; Can't pass nil params; empty map is fine though.
                      (assoc process nid {:input params
                                          :output (sf/invoke-result invokable params)})))
                  {}
                  nodes)]
    {:topo nodes
     :process process}))
