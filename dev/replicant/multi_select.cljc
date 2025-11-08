(ns replicant.multi-select
  (:require [clojure.string :as str]))

(def state (atom {:selected #{"cat" "bird"}}))

(defn option [selected value txt]
  [:option {:value value :selected (contains? selected value)}
   txt])

(defn render [state k]
  (let [{:keys [selected]} (get state k)]
    [:div {:style {:font-family "sans-serif" :padding "1rem"}}
     [:form
      [:div "Selected: "
       (if (seq selected)
         (str/join ", " selected)
         "(none)")]
      [:select {:multiple true
                :size 3
                :on {:change [[:actions/assoc-in [k :selected] :event.target/selected-option-values]]}
                :style {:margin-top "0.5rem"
                        :min-width "8rem"}}
       (option selected "dog" "Dog")
       (option selected "cat" "Cat")
       (option selected "bird" "Bird")]]]))

(def example
  {:title "Multi-select"
   :k :multi-select
   :f render
   :initial-data {:selected #{"cat" "bird"}}})
