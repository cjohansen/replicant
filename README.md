# Replicant - A Clojure(Script) DOM rendering library

Replicant takes hiccup and replicates its structure in the browser's DOM. Over
and over. Efficiently. It is pure ClojureScript, and is currently a bit slower
than React but a bit faster than reagent for most things. Work in progress.

Replicant has no local state and no components. It has a render function that
takes a DOM element and some hiccup, and it has a single life-cycle hook that is
called with data about what kind of event occurred (mount, update, move, etc).
It supports event handlers and life-cycle hooks as data.

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
