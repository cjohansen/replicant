(ns replicant.nested-rendering-bug)

(defn render [state k]
  (let [{:keys [counter explode?]} (get state k)]
    [:div
     [:h1 "How to trip Replicant over"]
     [:p
      "When the app first mounts, Replicant initializes the state for the "
      "DOM element, which includes a flag stating that a render is currently underway. "
      "On the second render, a node is mounted with a onmount hook that causes a recursive "
      "render. Replicant is supposed to guard against this and defer the call, but "
      "it forgets to update its internal rendering? flag to true, and thus keeps rendering "
      "until the stack blows. Pretty stupid!"]
     [:button {:on {:click [[:actions/assoc-in [k :explode?] true]]}} "Thar she blows!"]
     [:h2 "Explosion counter: " counter]
     (when explode?
       [:div {:replicant/on-mount
              [[:actions/assoc-in [k :counter] (inc counter)]]}])]))

(def example
  {:title "Nested rendering bug"
   :k :nested-rendering
   :f render
   :initial-data {:explode? false
                  :counter 0}})
