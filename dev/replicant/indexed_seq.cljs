(ns replicant.indexed-seq
  (:require [replicant.dom :as d]))

(defn ^:export start []
  (set! (.-innerHTML js/document.body) "<div id=\"app\"></div>")
  (let [el (js/document.getElementById "app")]
    (let [[_ & hiccup] [[:div "1"] [:div "2"] [:div "3"]]]
      (d/render el hiccup))))
