(ns replicant.life-cycle-bug
  (:require [replicant.dom :as d]))

(defn app []
  [:div
   [:h1 {:replicant/key :render
         :replicant/on-render [:rendered-ok]}
    "On render"]
   [:h1 {:replicant/key :on-mount
         :replicant/on-mount [:only-mounted-ok]}
    "On mount"]
   [:h1 {:replicant/key :on-unmount
         :replicant/on-unmount [:only-unmounted-ok]}
    "On unmount"]
   [:h1 {:replicant/key :on-mount-and-unmount
         :replicant/on-mount [:mounted-ok]
         :replicant/on-unmount [:unmounted-missing]}
    "On mount and unmount"]])

(defn ^:export start []
  (set! (.-innerHTML js/document.body) "<div id=\"app\"></div>")

  (d/set-dispatch!
   (fn [replicant-data handler-data]
     (prn handler-data)
     (prn replicant-data)
     (prn)))

  (let [el (js/document.getElementById "app")]
    (d/render el (app))
    (d/render el [:div])))

(comment

  (start)

  )
