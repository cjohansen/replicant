(ns replicant.on-mount-bug)

(defn render [_state k]
  [:h1 {:replicant/key :the-header
        :replicant/on-mount [[:actions/assoc-in [k] (rand-int 10)]]}
   "We keep mounting."])

(def example
  {:title "On-mount bug"
   :k :on-mount
   :f render})
