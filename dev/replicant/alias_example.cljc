(ns replicant.alias-example
  (:require [replicant.alias :refer [defalias]]))

(defalias app-alias [{:keys [square?]}]
  [:div {:on-click "Hmm"}
   [:h1 "Watch it go!"]
   [:input {:type "text" :value "Hehe"}]
   (when square?
     [:div#3.lol
      {:style {:transition "width 0.5s, height 200ms"
               :width 100
               :height 200
               :background "red"
               :overflow "hidden"}
       :on {:click [:some-data-for-your-handler]}
       :replicant/mounting {:style {:width 0 :height 0}}
       :replicant/unmounting {:style {:width 0 :height 0}}
       }
      "Colored square"])
   [:p {:replicant/key "p"} (if square? "Square!" "It's gone!")]])

(defn render [state k]
  [:div
   [app-alias (get state k)]])

(def example
  {:title "Alias example"
   :k :alias
   :f render})
