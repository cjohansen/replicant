(ns replicant.contenteditable-bug)

(def initial-fields
  (list "Deleting the contents of this div, then clicking add, reproduces the bug."))

(defn render [state k]
  [:div
   [:h1 "A bug that occurs when content is editable"]
   [:div
    [:button {:on {:click [[:actions/conj-in [k :fields] "another field"]]}} "add"]
    (for [field (get-in state [k :fields])]
      [:div {:contenteditable true :style {:border "solid red"}} field])]])

(def example
  {:title "Content editable bug"
   :initial-data {:fields initial-fields}
   :k :contenteditable
   :f render})
