(ns replicant.on-mount-bug
  (:require [replicant.dom :as d]))

(defonce store
  (atom {}))

(defn app [_state]
  [:h1 {:replicant/key :the-header
        :replicant/on-mount [:nothing]}
   "We keep mounting."])

(defn ^:export start []
  (set! (.-innerHTML js/document.body) "<div id=\"app\"></div>")

  (d/set-dispatch!
   (fn [replicant-data handler-data]
     (js/console.log (clj->js replicant-data))
     (swap! store identity)
     ))

  (let [el (js/document.getElementById "app")]
    (add-watch store :re-render (fn [_ _ _ state] (d/render el (app state)))))

  (swap! store identity))

(comment
  (prn "Hello")

  (start)
  )
