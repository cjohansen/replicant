(ns replicant.dev-actions)

(defn get-example [examples k]
  (first (filter (comp #{k} :k) examples)))

(defn process-actions [{:keys [store dispatch-actions examples]} action args]
  (case action
    :actions/go-to
    (swap! store (fn [state]
                   (let [k (first args)
                         example (get-example examples k)]
                     (cond-> (assoc state :example k)
                       (:initial-data example)
                       (assoc k (:initial-data example))))))

    :actions/assoc-in
    (apply swap! store assoc-in args)

    :actions/conj-in
    (let [[path v] args]
      (swap! store update-in path conj v))

    :actions/log
    nil

    (let [k (:example @store)]
      (if-let [impl (get-in (get-example examples k) [:actions action])]
        (dispatch-actions nil (apply impl (get @store k) args))
        (println "Unknown action" action)))))
