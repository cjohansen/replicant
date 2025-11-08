(ns replicant.memory-example)

(def texts
  ["It'll remember what time it was mounted"
   "This is poor use of this feature - the intended use is to keep a reference to stuff from third party JS library integrations."
   "It's still a good enough smoke test"])

(defn render [state k]
  (let [{:keys [text n]} (get state k)]
    [:div {:replicant/on-mount
           (fn [{:replicant/keys [remember]}]
             (remember {:mounted-at (js/Date.)}))

           :replicant/on-update
           (fn [{:replicant/keys [memory]}]
             (prn "My memory is" memory))}
     [:h1 "Component with memory"]
     [:p text]
     [:button {:on {:click [[:actions/assoc-in [k]
                             {:n (inc n)
                              :text (nth texts (mod (inc n) (count texts)))}]]}}
      "Nudge"]]))

(def example
  {:title "Replicant memory"
   :k :memory
   :f render
   :initial-data {:n 0
                  :text (first texts)}})
