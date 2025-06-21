(ns replicant.input
  (:require [replicant.dom :as d]))

(defn app [state]
  [:div
   [:h1 "Input fields"]
   [:p "Load page with ?no-attrs to start without attributes"]
   (when-not (:readonly? state)
     [:p "Rendering without attributes"])
   [:form
    [:label.label "Read-only input"
     [:input {:readonly (:readonly? state)}]]
    [:label.label "Required input"
     [:input {:required (:required? state)}]]
    [:label.label "Disabled input"
     [:input {:disabled (:disabled? state)}]]
    (if (:readonly? state)
      [:button {:on {:click [:remove-attrs]}} "Strip attributes"]
      [:button {:on {:click [:add-attrs]}} "Reinstate attributes"])]])

(defn ^:export start []
  (set! (.-innerHTML js/document.body) "<div id=\"app\"></div>")
  (let [default-val {:readonly? true
                     :required? true
                     :disabled? true}
        store (atom (if (re-find #"no-attrs" js/location.href)
                      {}
                      default-val))
        el (js/document.getElementById "app")]

    (add-watch store ::render #(d/render el (app %4)))

    (d/set-dispatch!
     (fn [{:replicant/keys [^js dom-event]} action]
       (.preventDefault dom-event)
       (reset! store
               (case (first action)
                 :remove-attrs {}
                 :add-attrs default-val))))

    (swap! store assoc ::started (js/Date.))))

(comment

  (start)

  )
