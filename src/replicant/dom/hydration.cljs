(ns replicant.dom.hydration
  (:require [replicant.alias :as alias]
            [replicant.core :as r]
            [replicant.dom :as d]
            [replicant.env :as env]
            [replicant.hydration :as hydration]))

(defn ^:export hydrate [^js el hiccup & [{:keys [aliases alias-data on-alias-exception]}]]
  (let [renderer (d/create-renderer)]
    (if (contains? @d/state el)
      (throw (ex-info "Node already controlled by Replicant, cannot hydrate" {:el el}))
      (vswap! d/state assoc el {:renderer renderer
                                :unmounts (volatile! #{})
                                :unmount-hooks (volatile! (r/node-map))
                                :rendering? true}))
    (let [aliases (or aliases (alias/get-registered-aliases))
          hiccup (if alias-data
                   (env/with-dev-key hiccup [aliases alias-data])
                   (env/with-dev-key hiccup aliases))
          {:keys [vdom]} (hydration/hydrate renderer el hiccup {:aliases aliases
                                                                :alias-data alias-data
                                                                :on-alias-exception on-alias-exception})]
      (vswap! d/state update el merge (cond-> {:rendering? false}
                                        vdom (assoc :current vdom))))
    el))
