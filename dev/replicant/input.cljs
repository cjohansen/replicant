(ns replicant.input)

(def attrs
  {:readonly? true
   :required? true
   :disabled? true})

(defn render [state k]
  (let [{:keys [readonly? disabled? required?]} (get state k)]
    [:div
     [:h1 "Input fields"]
     [:p "Load page with ?no-attrs to start without attributes"]
     (when-not readonly?
       [:p "Rendering without attributes"])
     [:form
      [:label.label "Read-only input"
       [:input {:readonly readonly?}]]
      [:label.label "Required input"
       [:input {:required required?}]]
      [:label.label "Disabled input"
       [:input {:disabled disabled?}]]
      (if readonly?
        [:button {:type "button"
                  :on {:click [[:actions/assoc-in [k] {}]]}} "Strip attributes"]
        [:button {:type "button"
                  :on {:click [[:actions/assoc-in [k] attrs]]}} "Reinstate attributes"])]]))

(def example
  {:title "Input fields (readonly, disabled)"
   :k :input
   :f render
   :initial-data (if (re-find #"no-attrs" js/location.href)
                   {}
                   attrs)})
