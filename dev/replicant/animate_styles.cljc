(ns replicant.animate-styles)

(defn render [state k]
  (let [{:keys [peek?]} (get state k)
        attrs {:style {:background-color "red"
                       :transition "background-color 0.5s"}
               :replicant/mounting {:style {:background-color "blue"}}
               :replicant/unmounting {:style {:background-color "blue"}}
               }]
    [:div
     [:div attrs "A"]
     (when peek?
       [:div attrs "B"])
     nil
     [:div attrs "C"]
     [:button
      {:on {:click [[:actions/assoc-in [k :peek?] (not peek?)]]}}
      "Peek-a-boo"]]))

(def example
  {:title "Animating styles on mount/unmount"
   :k :animation
   :f render})
