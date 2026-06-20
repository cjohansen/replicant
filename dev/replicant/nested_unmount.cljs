(ns replicant.nested-unmount)

(defn render [state k]
  [:div {}
   (when (get-in state [k :mount?])
     [:h1
      [:span {:replicant/on-unmount [[:actions/assoc-in [k :unmounted?] true]]}
       "Can we know about unmounting parents?"]])
   (when (get-in state [k :unmounted?])
     [:p "Heading was unmounted and we knew about it"])
   (if (get-in state [k :mount?])
     [:button
      {:on {:click [[:actions/assoc-in [k :mount?] false]]}}
      "Unmount heading"]
     [:button
      {:on {:click [[:actions/assoc-in [k :mount?] true]
                    [:actions/assoc-in [k :unmounted?] false]]}}
      "Mount heading"])])

(def example
  {:title "Nested unmount hooks"
   :k :nested-unmount
   :f render})
