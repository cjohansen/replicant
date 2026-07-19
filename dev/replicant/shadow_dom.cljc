(ns replicant.shadow-dom)

(defn render [state k]
  (if (get-in state [k :shadow?])
    [:div {:shadow {}}
     (if (get-in state [k :button])
       [:p "You clicked the shadow button, well done. "
        [:a {:style {:text-decoration "underline" :color "blue"}
             :on {:click [[:actions/assoc-in [k :button] false]
                          [:actions/assoc-in [k :shadow?] false]]}} "Try it again"] "."]
       [:button {:on {:click [[:actions/assoc-in [k :button] true]]}} "A shadowy button"])]
    [:div
     [:p "There is no shadow. Click the button to experience it."]
     [:button {:on {:click [[:actions/assoc-in [k :shadow?] true]]}}
      "Bring me the shadows"]]))

(def example
  {:title "Shadow DOM"
   :k :shadow-dom
   :f render})
