(ns replicant.assert-example)

(defn render [state k]
  (let [{:keys [square?] :as props} (get state k)]
    [:div {:replicant/context {:fn-name "replicant.dev/app"
                               :data props}}
     [:h1 "Watch it go!"]
     [:input {:type "text" :value "Hehe"}]
     (when square?
       [:div#3.lol.
        {;;:className "some classes"
         :style {:transition "width 0.5s, height 200ms"
                 :width 100
                 :height 200
                 :background "red"
                 :overflow "hidden"}
         :on {:click [:some-data-for-your-handler]}
         :replicant/mounting {:style {:width 0 :height 0}}
         :replicant/unmounting {:style {:width 0 :height 0}}}
        "Colored square"])
     [:p {:replicant/key "p"} (if square? "Square!" "It's gone!")]
     [:button {:on {:click [[:actions/assoc-in [k :square?] (not square?)]]}}
      "Animate square"]]))

(def example
  {:title "Assert example (warnings in the console)"
   :k :assert
   :f render})
