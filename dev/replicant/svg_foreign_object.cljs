(ns replicant.svg-foreign-object)

(defn render [_ _]
  [:svg {:width 200 :height 200}
   [:foreignObject {:x 50 :y 50 :width 100 :height 100}
    [:div {:style {:width "100px" :height "100px"}}
     "Hello World"]]])

(def example
  {:title "SVG foreign object"
   :k :foreign-object
   :f render})
