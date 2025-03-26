(ns replicant.memory
  (:require [replicant.dom :as d]))

(defn app [{:keys [text]}]
  [:div {:replicant/on-mount
         (fn [{:replicant/keys [remember]}]
           (remember {:mounted-at (js/Date.)}))

         :replicant/on-update
         (fn [{:replicant/keys [memory]}]
           (prn "My memory is" memory))}
   [:h1 "Component with memory"]
   [:p text]])

(defn render [text]
  (d/render js/document.body (app {:text text})))

(defn ^:export start [text]
  (set! (.-innerHTML js/document.body) "")
  (render text))

(comment

  (start "It'll remember what time it was mounted")
  (render "This is poor use of this feature - the intended use is to keep a reference to stuff from third party JS library integrations.")
  (render "It's still a good enough smoke test")

  )
