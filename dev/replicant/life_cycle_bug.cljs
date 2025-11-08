(ns replicant.life-cycle-bug)

(defn render [_ _]
  [:div
   [:h1 {:replicant/key :render
         :replicant/on-render [[:actions/log [:rendered-ok]]]}
    "On render"]
   [:h1 {:replicant/key :on-mount
         :replicant/on-mount [[:actions/log [:only-mounted-ok]]]}
    "On mount"]
   [:h1 {:replicant/key :on-unmount
         :replicant/on-unmount [[:actions/log [:only-unmounted-ok]]]}
    "On unmount"]
   [:h1 {:replicant/key :on-mount-and-unmount
         :replicant/on-mount [[:actions/log [:mounted-ok]]]
         :replicant/on-unmount [[:actions/log [:unmounted-missing]]]}
    "On mount and unmount"]])

(def example
  {:title "Life-cycle bug"
   :k :life-cycle
   :f render})
