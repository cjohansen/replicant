(ns replicant.dev
  (:require [replicant.contenteditable-bug :as ceb]
            [replicant.dom :as d]))

(defn app [{:keys [square?] :as props}]
  [:div {:replicant/context {:fn-name "replicant.dev/app"
                             :data props}}
   [:h1 "Watch it go!"]
   [:input {:type "text" :value "Hehe"}]
   (when square?
     [:div {:className "some classes"
            :style {:transition "width 0.5s, height 200ms"
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

  (d/render el [:h1 "Hello world"])

  (d/render el [:div {:lol? "oh no"} "Hello"])

  (d/render el [:div {:className "wrong"} "Hello"])
  (d/render el [:div {:class "also wrong"} "Hello"])
  (d/render el [:div {:style "background: yellow"} "Hello"])
  (d/render el [:div {:replicant/context ^:replicant/internal {:fn-name 'replicant.dev/event-handler-ex}
                      :on {:keyUp [:some-data]}} "Hello"])

  (d/render el [:div {:style {32 "LOL"}} "Hello"])
  (d/render el [:div {:style {:backgroundColor "LOL"}} "Hello"])
  (d/render el [:div {:style {:onclick (fn [])}} "Not like that"])
  (d/render el [:div {:onClick (fn [])} "Not like that"])

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





(comment

;; I18n

(def dictionaries
  {:nb
   {:title "Min webside"
    :hello "Hei på deg!"
    :click "Klikk knappen"}

   :en
   {:title "My webpage"
    :hello "Hello world!"
    :click "Click the button"}})

(defn lookup-i18n [dictionary _attrs [k]]
  (get dictionary k))

;; A function that adds a bunch of tailwind classes to the markup

(defn button [{:keys [actions spinner? subtle?] :as btn} [text]]
  [:button.btn.max-sm:btn-block
   (cond-> (dissoc btn :spinner? :actions :subtle?)
     actions (assoc-in [:on :click] actions)
     subtle? (assoc :class "btn-neutral")
     (not subtle?) (assoc :class "btn-primary"))
   (when spinner?
     [:span.loading.loading-spinner])
   text])

;; Function to turn domain data into hiccup

(defn app [{:keys [locale]}]
  [:div {:replicant/key locale}
   [:h1 [:i18n/k :title]]
   [:p [:i18n/k :hello]]
   [:ui/button {:actions [[:do-stuff]]}
    [:i18n/k :click]]])

[:ui/button#special.btn-primary {:actions [[:do-stuff]]}
 [:i18n/k :click]]

;; Render

(defn render-app [state]
  (d/render
   el
   (app state)
   {:aliases {:i18n/k (partial lookup-i18n (dictionaries (:locale state)))
              :ui/button button}}))

;; Render in english
(render-app {:locale :en})

;; ...eller norsk
(render-app {:locale :nb})



)
