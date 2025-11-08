(ns replicant.select-bug
  (:require [replicant.core :as c]
            [replicant.dom :as d]))

(def options
  [["banana" "Banana"]
   ["apple" "Apple"]
   ["orange" "Orange"]])

(defn app [{:keys [selected]}]
  [:select {:on {:input [:select]}}
   (for [[id text] options]
     [:option
      (cond-> {:value id}
        (= id selected) (assoc :selected true))
      text])])

(defonce !el (atom nil))

(defn ^:export start []
  (set! (.-innerHTML js/document.body) "<div id=\"app\"></div>")

  (reset! !el (js/document.getElementById "app"))

  (let [store (atom {})]

    (add-watch store ::self
               (fn [_ _ _ state]
                 (d/render @!el (app state))))

    (d/set-dispatch!
     (fn [e action]
       (case (first action)
         :select (if-let [selected (some-> e :replicant/node .-value)]
                   (swap! store assoc :selected selected)
                   (swap! store assoc :selected (second action))))))

    (swap! store assoc :selected "banana")))

(defn ^:export init! []
  (start))

(defn replicant-dispatch!
  "Dispatch event data outside of Replicant actions"
  [e data]
  (let [el @!el]
    (if (and c/*dispatch* el)
      (if (get-in @d/state [el :rendering?])
        (js/requestAnimationFrame #(c/*dispatch* e data))
        (c/*dispatch* e data))
      (throw (js/Error. "Cannot dispatch custom event data without a global event handler. Call replicant.core/set-dispatch!")))))

(comment
  (init!)

  ;; Repro
  ;; 1. Manually select "apple" in the browser
  (replicant-dispatch! nil [:select "banana"]) ; 2. Evaluate => Banana is selected in the app
  (replicant-dispatch! nil [:select "apple"])  ; 3. Evaluate => Banana is still selected in the app
                                               ;    Yet, the dom inspector shows the "apple" option as selected
  (replicant-dispatch! nil [:select "orange"])

  :rcf)
