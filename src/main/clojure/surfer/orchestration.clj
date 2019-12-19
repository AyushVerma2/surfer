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
                                                (let [;; Find ports where dependency-nid is source and nid is target.
                                                      ports-coll (->> (:edges orchestration)
                                                                      (filter
                                                                        (fn [{:keys [source target]}]
                                                                          (and (= dependency-nid source)
                                                                               (= nid target))))
                                                                      (map :ports))]
                                                  (reduce
                                                    (fn [params [port-out port-in]]
                                                      (assoc params port-in (get-in process [dependency-nid port-out])))
                                                    {}
                                                    ports-coll))))
                                            (apply merge)))]
                      ;; Can't pass nil params; empty map is fine though.
                      (assoc process nid (sf/invoke-result invokable (or params {})))))
                  {}
                  nodes)]
    {:topo nodes
     :process process}))
