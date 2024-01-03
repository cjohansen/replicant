(ns replicant.dev
  (:require [replicant.dom :as d]))

(comment

  (set! *print-namespace-maps* false)
  (set! (.-innerHTML js/document.body) "<div id=\"app\"></div>")

  (d/set-dispatch!
   (fn [& args]
     (prn "OHOI!" args)))

  (def el (js/document.getElementById "app"))

  (d/render el [:ul.cards
                [:li {:replicant/key 1} [:div.square.wobble]]
                [:li {:replicant/key 2} [:div.square.wobble.green]]
                [:li {:replicant/key 3} [:div.square.wobble.orange]]
                [:li {:replicant/key 4} [:div.square.wobble.yellow]]
                ])

  )
