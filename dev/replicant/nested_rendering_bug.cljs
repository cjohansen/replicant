(ns replicant.nested-rendering-bug
  (:require [replicant.dom :as d]))

(defonce store
  (atom {:explode? false
         :counter 0}))

(defn app [data]
  (println "Render!")
  [:div
   [:h1 "How to trip Replicant over"]
   [:p
    "When the app first mounts, Replicant initializes the state for the "
    "DOM element, which includes a flag stating that a render is currently underway. "
    "On the second render, a node is mounted with a onmount hook that causes a recursive "
    "render. Replicant is supposed to guard against this and defer the call, but "
    "it forgets to update its internal rendering? flag to true, and thus keeps rendering "
    "until the stack blows. Pretty stupid!"]
   [:button {:on {:click #(swap! store assoc :explode? true)}} "Thar she blows!"]
   [:h2 "Explosion counter: " (:counter data)]
   (when (:explode? data)
     [:div {:replicant/on-mount #(swap! store update :counter inc)}])])

(defn ^:export start []
  (set! (.-innerHTML js/document.body) "<div id=\"app\"></div>")
  (let [el (js/document.getElementById "app")]
    (add-watch store :re-render (fn [_ _ _ _] (d/render el (app @store)))))
  (swap! store identity))
