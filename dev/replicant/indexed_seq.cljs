(ns replicant.indexed-seq)

(defn render [_ _]
  (let [[_ & hiccup] [[:div "1"] [:div "2"] [:div "3"]]]
    hiccup))

(def example
  {:title "Indexed seq"
   :k :indexed-seq
   :f render})
