(ns replicant.hydration-demo)

(defn render [state k]
  (if (get-in state [k :client?])
    [:div
     [:h1 "Rendered on the client"]]
    [:div
     [:h1 "Rendered on the server"]
     [:button
      {:on {:click [[:actions/assoc-in [k :client?] true]]}}
      "Click me"]]))

(def example
  {:title "Hydration"
   :k :hydration
   :f #'render
   :hydrate? true})
