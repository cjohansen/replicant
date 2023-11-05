# Replicant - A Clojure(Script) DOM rendering library

Replicant takes hiccup and replicates its structure in the browser's DOM. Over
and over. Efficiently.

```clj
(require '[replicant.dom :as d])

(def el (js/document.getElementById "app"))

;; Render to the DOM - creates all elements
(d/render el
  [:ul.cards
    [:li {:key 1} "Item #1"]
    [:li {:key 2} "Item #2"]
    [:li {:key 3} "Item #3"]
    [:li {:key 4} "Item #4"]])

;; This render call will only result in one DOM node being moved.
(d/render el
  [:ul.cards
    [:li {:key 1} "Item #1"]
    [:li {:key 3} "Item #3"]
    [:li {:key 2} "Item #2"]
    [:li {:key 4} "Item #4"]])
```

This Readme will be fledged out quite soon.
