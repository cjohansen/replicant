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
                [:li {:key 1} [:div.square.wobble]]
                [:li {:key 2} [:div.square.wobble.green]]
                [:li {:key 3} [:div.square.wobble.orange]]
                [:li {:key 4} [:div.square.wobble.yellow]]
                ])

  )
