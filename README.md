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

## Status

Replicant is starting to shape up, but is still under development. Details are
subject to change. The current focus is on improving performance.

## Performance

Replicant performance is being measured with
https://github.com/krausest/js-framework-benchmark. It is currently a little
slower than React, but faster than reagent. It is much faster than
[dumdom](https://github.com/cjohansen/dumdom), which can be considered
Replicant's predecessor.

## Data-driven hooks and events

As originally introduced in [dumdom](https://github.com/cjohansen/dumdom), and
described in [my talk about data-driven UIs](https://vimeo.com/861600197),
Replicant allows you to register a global function for handling events and
life-cycles. This way your hiccup can be all serializable data, setting you up
for the best possible rendering performance.

```clj
[:h1 {:on {:click [:whatever]}}
  "Click me"]
```

When Replicant encounters this hiccup, it will register a click handler that
will pass the data (`[:whatever]`) to a global dispatcher. You register this by
calling `replicant.dom/set-dispatch!`:

```clj
(require '[replicant.dom :as replicant])

(replicant/set-dispatch!
  (fn [replicant-data event handler-data]
    (prn "Click!")))

(replicant/render
  (js/document.getElementById "app")
  [:h1 {:on {:click [:whatever]}} "Click me"])
```

In the above example, `replicant-data` is a map with information about the event
from Replicant. Specifically, it contains the key `:replicant/event`, which will
have the value `:replicant.event/dom-event` for DOM events. `event` is the DOM
event object, and `handler-data` is data from the hiccup element, e.g.
`[:whatever]`. The same global dispatch will be called for all events that are
expressed as data, and you would typically use the data to decide what to do.

You can of course also register a function event handler if you want. It will be
called with just the DOM event object.

### Life-cycle hooks

Replicant life-cycle hooks can also be expressed with data:

```clj
(require '[replicant.dom :as replicant])

(replicant/set-dispatch!
  (fn [replicant-data hook-data]
    (prn "DOM changed")))

(replicant/render
  (js/document.getElementById "app")
  [:h1 {:replicant/on-update ["Update data"]} "Hi!"])
```

`replicant-data` is the same map from before. For lifecycle hooks,
`:replicant/event` will have the value `:replicant.event/life-cycle`.
Additionally, the map will have a key `:replicant/life-cycle` describing what
kind of event occurred:

- `:replicant/mount`
- `:replicant/unmount`
- `:replicant/update`

`:replicant/node` will contain a reference to the DOM element in question. For
some operations, `:replicant/details` will contain a vector with keywords about
the kind(s) of change occurred:

- `:replicant/move-node`
- `:replicant/updated-attrs`
- `:replicant/updated-children`

The second argument, `hook-data` is whatever data you set on
`:replicant/on-update`.

`:replicant/on-update` can also take a function, in which case it will be called
with a single argument - the hook details map described above.

## Keys

Replicant uses keys to identify nodes when the overall structure changes. Set
`:key` in the attributes map of any element that you do not want recreated
unnecessarily. This key is local to the parent element (e.g. you may reuse the
key at different levels). When it is set, Replicant will know to reuse the
corresponding DOM element, even when it changes positions, etc. If you have CSS
transitions on an element, you very likely want to give it a key.

## Differences from hiccup

Replicant has a more liberal understanding of hiccup data than the main hiccup
library.

### Styles

You may express `:style` as a map of styles, e.g.:

```clj
[:div {:style {:background "red", :width 320}}
  "A small red thing"]
```

Using maps for styles is suggested, as string values for the style attribute
will be parsed to maps.

### Classes

You can specify classes on the hiccup "selector", e.g. `[:h1.heading "Hello"]`.
In addition, you can use `:class`, which takes a few different values:

- A string
- A keyword
- A collection of string|keyword

The suggested value is keywords and collections of keywords. Strings will be
split on space.

## API

### `(replicant.dom/render el hiccup)`

### `(replicant.dom/set-dispatch! f)`

## Rationale

After 10+ years, React's original premise still stands out to me as the ideal
approach to frontend development: Code as if every update is a full re-render.
This gives you a delightfully straight-forward development model that does not
have to be explicit about how a UI must change to go from one state to the next.
Instead, you just describe the next state, and the rendering library figures it
out for you.

This model has so many benefits that I recently gave a [40 minute talk about
it](https://vimeo.com/861600197).

React and other popular frontend technologies are not mere rendering libraries.
Instead they have taken on the role of holistic UI frameworks: handling state
changes, and packing on increasing amounts of features to do more and more
things inside UI components.

Replicant is a **rendering libary**. That's it. There's no state management,
there's no async rendering, there's no networking utilities. There's just a
single function that renders and rerenders your hiccup to the DOM in an
efficient manner.

### What about components?

Replicant does not have components. Some common reasons to have components
include:

1. Capture UI components in reusable artifacts
2. Wire in cross-cutting concerns (i18n, theming, etc)
3. Use knowledge about domain data to short-circuit rendering for performance
4. Attach life-cycle hooks (used for animations, DOM manipulation, etc)

With Replicant, you would use a function to capture UI components, e.g.
something like `(button {,,,})` which returns the appropriate hiccup for a
button.

Replicant does not need to know about cross-cutting concerns like i18n, theming,
etc. The benefit of using hiccup for rendering is that hiccup is data.
Cross-cutting concerns can be implemented as pure data transformations. These
can be applied before your pass data to Replicant for rendering.

Short-circuiting rendering (e.g. something akin to React's original
`shouldComponentUpdate`) is generally not necessary, as Replicant is already
efficient enough. Should you have some heavy domain data to hiccup
transformations however, you can use `memoize` or other more specialized tools.
Since "components" are just functions that return hiccup, you don't need
framework specific tooling to optimize your code.

Life-cycles are genuinly useful. That's why you can attach them directly to
hiccup nodes, and Replicant will trigger them for you.

## Contribute

Want to help make it fast? Awesome, please help in any way you can.
