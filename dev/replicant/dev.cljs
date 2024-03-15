(ns replicant.dev
  (:require [replicant.contenteditable-bug :as ceb]
            [replicant.dom :as d]))

(defn app [{:keys [square?]}]
  [:div
   [:h1 "Watch it go!"]
   [:input {:type "text" :value "Hehe"}]
   (when square?
     [:div {:style {:transition "width 0.5s, height 200ms"
                    :width 100
                    :height 200
                    :background "red"
                    :overflow "hidden"}
            :on {:click [:some-data-for-your-handler]}
            :replicant/mounting {:style {:width 0 :height 0}}
            :replicant/unmounting {:style {:width 0 :height 0}}
            }
      "Colored square"])
   [:p {:replicant/key "p"} (if square? "Square!" "It's gone!")]
   ])

(defn animated [{:keys [peek?]}]
  (let [attrs {:style {:background-color "red"
                       :transition "background-color 0.5s"}
               :replicant/mounting {:style {:background-color "blue"}}
               :replicant/unmounting {:style {:background-color "blue"}}
               }]
    [:div
     [:div attrs "A"]
     (when peek?
       [:div attrs "B"])
     nil
     [:div attrs "C"]]))

(comment

  (enable-console-print!)

  (set! *print-namespace-maps* false)

  (d/set-dispatch!
   (fn [& args]
     (prn "OHOI!" args)))

  (ceb/start)

  (do
    (set! (.-innerHTML js/document.body) "<div id=\"app\"></div>")
    (def el (js/document.getElementById "app"))
    (d/render el (app {:square? true}))
    )

  (d/render el (app {}))

  (d/render
   el
   [:div
    [:div {:style {:animation "fadein 2s ease"}} "A"]
    (when true
      [:div {:style {:animation "fadein 2s ease"}} "B"])
    nil ;; <-- denne er ny
    [:div {:style {:animation "fadein 2s ease"}} "C"]])


  (d/render el (animated {})) ;; Ok
  (d/render el (animated {:peek? true})) ;; Ok
  (d/render el (animated {})) ;; Ok
  (d/render el (animated {:peek? true})) ;; BOOM


  (->> [:div
        [:h1 {:style {"background" "var(--bg)"
                      "--bg" "red"}} "Watch it go!"]]
       (d/render el))

  )






(comment


  (->> [:div
        [:ul.cards
         [:li {:replicant/key 1} [:div.square.wobble]]
         [:li {:replicant/key 2} [:div.square.wobble.green]]
         [:li {:replicant/key 3} [:div.square.wobble.orange]]
         [:li {:replicant/key 4} [:div.square.wobble.yellow]]]
        [:div {:style {:transition "width 0.25s"
                       :width 100
                       :height 200
                       :background "red"}
               :replicant/mounting {:style {:width 0}}}]]
       (d/render el))


)
