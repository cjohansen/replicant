(ns replicant.range)

(defn render-value [_ _]
  [:div
   [:p "Range inputs have implicit default min/max of 0/100. If the input is
   rendered with a value above 100, the value attribute must be set after the
   min and max attributes, otherwise the browser clamps the value."]
   [:input {:type "range"
            :value 150
            :min 100
            :max 200}]])

(def example-1
  {:title "Range input with value > 100"
   :k :range-value
   :f render-value})

(defn render-default-value [_ _]
  [:div
   [:p "Range inputs have implicit default min/max of 0/100. If the input is
   rendered with a default value above 100, the value attribute must be set
   after the min and max attributes, otherwise the browser clamps the value."]
   [:input {:type "range"
            :default-value 150
            :min 100
            :max 200}]])

(def example-2
  {:title "Range input with default value > 100"
   :k :range-default-value
   :f render-default-value})
