(ns replicant.contenteditable-bug
  (:require [replicant.dom :as d]))

(defonce store
  (atom (list "Deleting the contents of this div, then clicking add, reproduces the bug.")))

(defn app [fields]
  [:div
   [:h1 "A bug that occurs when content is editable"]
   [:button {:on {:click #(swap! store conj "another field")}} "add"]
   (for [field fields] [:div {:contenteditable true :style {:border "solid red"}} field])])

(defn ^:export start []
  (set! (.-innerHTML js/document.body) "<div id=\"app\"></div>")
  (let [el (js/document.getElementById "app")]
    (add-watch store :re-render (fn [_ _ _ _] (d/render el (app @store)))))
  (swap! store identity))
