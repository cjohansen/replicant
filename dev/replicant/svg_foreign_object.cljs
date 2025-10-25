(ns replicant.svg-foreign-object
  (:require [replicant.dom :as d]))

(defn main []
  [:svg {:width 200 :height 200}
   [:foreignObject {:x 50 :y 50 :width 100 :height 100}
    [:div {:style {:width "100px" :height "100px"}}
     "Hello World"]]])

(defn ^:export start []
  (set! (.-innerHTML js/document.body) "<div id=\"app\"></div>")
  (let [el (js/document.getElementById "app")]
    (d/set-dispatch!
     (fn [_ action]
       (prn action)))
    (d/render el (main))))
