# Replicant - A Clojure(Script) DOM rendering library

Replicant turns hiccup into DOM. Over and over. Efficiently, without a single
dependency.

```clj
(require '[replicant.dom :as d])

(def el (js/document.getElementById "app"))

;; Render to the DOM - creates all elements
(d/render el
  [:ul.cards
    [:li {:replicant/key 1} "Item #1"]
    [:li {:replicant/key 2} "Item #2"]
    [:li {:replicant/key 3} "Item #3"]
    [:li {:replicant/key 4} "Item #4"]])

;; This render call will only result in one DOM node being moved.
(d/render el
  [:ul.cards
    [:li {:replicant/key 1} "Item #1"]
    [:li {:replicant/key 3} "Item #3"]
    [:li {:replicant/key 2} "Item #2"]
    [:li {:replicant/key 4} "Item #4"]])
```

## Status

Replicant is starting to shape up, but is still under development. Details are
subject to change. The current focus is on finalizing APIs and hardening by
porting some large UIs to it.

## Features

- Efficient hiccup to DOM renders and re-renders
- Represent entire UIs with serializable data
- Rich life-cycle hooks (mount, unmount, update attributes, move, etc)
- Data-driven hooks and DOM event handlers
- Stateless and component-free
- Style/class/attribute overrides during mounting and unmounting for easy
  transitions
- Small API surface: A few functions and a handful of keywords
- Inline styles with Clojure maps
- Class lists with Clojure collections
- `innerHTML` support
- Rendering to strings
- No dependencies

## Performance

Replicant performance is being tuned using
https://github.com/krausest/js-framework-benchmark. See [benchmarking
instructions](#benchmarking) for how to run locally.

<a id="data-hooks"></a>
## Data-driven hooks and events

As originally introduced in [dumdom](https://github.com/cjohansen/dumdom), and
described in [my talk about data-driven UIs](https://vimeo.com/861600197),
Replicant allows you to register a global function for handling events and
life-cycles. This way your hiccup can be free of opaque functions, setting you
up for good rendering performance and fully serializable UI data.

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
  (fn [replicant-data handler-data]
    (prn "Click!")))

(replicant/render
  (js/document.getElementById "app")
  [:h1 {:on {:click [:whatever]}} "Click me"])
```

In the above example, the first argument `replicant-data` is a map with
information about the event from Replicant. Specifically, it contains the keys:

- `:replicant/trigger`, which will have the value `:replicant.trigger/dom-event`,
- `:replicant/js-event`, which will contain a reference to the DOM event object, and
- `:replicant/node`, which will contain a reference to the DOM node the event occurred in.

The second argument `handler-data` is data from the hiccup element, e.g.
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
  [:h1 {:replicant/on-render ["Update data"]} "Hi!"])
```

`replicant-data` is the same map from before. For lifecycle hooks, the map contains:
- `:replicant/trigger`, which will have the value `:replicant.trigger/life-cycle`,
- `:replicant/life-cycle`, which describes what kind of event occurred, having
    one the following values:
  - `:replicant.life-cycle/mount`
  - `:replicant.life-cycle/unmount`
  - `:replicant.life-cycle/update`
- `:replicant/node` will contain a reference to the DOM node the event occurred in.

The second argument, `hook-data` is whatever data you set on
`:replicant/on-render`.

`:replicant/on-render` can also take a function, in which case it will be called
with a single argument - the hook details map described above.

## Keys

Replicant uses keys to identify nodes when the overall structure changes. Set
`:replicant/key` in the attributes map of any element that you do not want
recreated unnecessarily. This key is local to the parent element (e.g. you may
reuse the key at different levels). When it is set, Replicant will know to reuse
the corresponding DOM element, even when it changes positions, etc. If you have
CSS transitions on an element, you very likely want to give it a key.

<a id="data-transitions"></a>
## Data-driven transitions

Sometimes it's nice when elements smoothly transition into and/or out of being.
Replicant enables this by supporting overrides for inline styles, classes, and
indeed any attribute during mount and/or unmount.

### Mounting styles/classes/attributes

```clj
[:h1 {:style {:transition "left 0.25s"
              :position "absolute"
              :left 0}
      :replicant/mounting {:style {:left "-100%"}}}
 "Hello world"]
```

When this element is mounted to the DOM, it will slide in from the left. When
initially rendered, it will have these styles:

```clj
{:transition "left 0.25s"
 :position "absolute"
 :left "-100%"}
```

Once mounted, it will be updated to:

```clj
{:transition "left 0.25s"
 :position "absolute"
 :left 0}
```

Which causes the CSS transition to trigger, and move the element in from the
left.

Mounting styles are merged into your ordinary styles. Other attributes are
completely overwritten. Classes are partly overwritten: the classes from the
hiccup symbol will always be included, but `:class` will be overwritten:

```clj
[:h1.heading
  {:class [:mounted]
   :replicant/mounting {:class [:mounting]}}
 "Hello world"]
```

During mounting, this element will have the classes `"heading mounting"`, and
after mounting it will have the classes `"heading mounted"`.

### Unmounting styles/classes/attributes

Replicant supports changing an element's attributes, classes and styles to
trigger a transition as the element leaves the DOM. When you do this, the node
will not be removed from the DOM until its transitions have completed. The
life-cycle hook will trigger after the element has transitioned and been removed
from the DOM.

To expand on the mounting example, this component:

```clj
[:h1 {:style {:transition "left 0.25s"
              :position "absolute"
              :left 0}
      :replicant/mounting {:style {:left "-100%"}}
      :replicant/unmounting {:style {:left "-100%"}}}
 "Hello world"]
```

Would slide in from the left when mounted, and then slide out to the left again
when unmounted. Only after the slide transition completes will it be removed
from the DOM.

#### Class overrides

Given this CSS:

```css
.pane {
  transition: left 0.25s;
  position: absolute;
  left: 0;
}

.mounting {
  left: -100%;
}
```

And this hiccup:

```clj
[:h1.pane {:replicant/mounting {:class :mounting}}
  "Hello world"]
```

The pane would slide in from the left, as it would be mounted with the two
classes `"pane mounting"` and after mount it would have only `"pane"`. Note that
classes from the hiccup symbol are added to both mounting and mounted classes.

This feature was inspired by a similar feature in
[snabbdom](https://github.com/snabbdom/snabbdom).

## innerHTML

Sometimes all you have is a string of pre-rendered HTML. Replicant can render it
for you via `innerHTML`:

```clj
[:div {:innerHTML "<h1>Oh, well</h1>"}]
```

When using `:innerHTML` any child elements will be ignored (without warning at
the time being).

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

## API Reference

### `(replicant.dom/render el hiccup)`

Render the `hiccup` into the element `el`. Any pre-existing content not created
by Replicant will be removed. Subsequent calls will efficiently update the
rendered DOM elements by comparing the new and old `hiccup`.

### `(replicant.dom/unmount el)`

Unmounts elements rendered in `el` and clean up internal state held for this
node.

<a id="set-dispatch"></a>
### `(replicant.dom/set-dispatch! f)`

Register the function to call when life cycle hooks and/or event handlers are
data, not functions (see [data-driven hooks](#data-hooks)). The function will be
called with two arguments. The first is a map with details about the trigger,
with the following keys:

- `:replicant/trigger` Either `:replicant.trigger/dom-event` or
  `:replicant.trigger/life-cycle`
- `:replicant/node` The triggering node
- `:replicant/js-event` the JavaScript event object, when the trigger is
  `:replicant.trigger/dom-event`
- `:replicant/details` A vector of keywords indicating what caused the
  life-cycle hook when the trigger is `:replicant.trigger/life-cycle`. One or
  more of`:replicant/move-node`, `:replicant/updated-attrs`,
  `:replicant/updated-children`.

The second argument is the data passed to the hiccup life-cycle/event handler
attribute.

<a id="string-render"></a>
### `(replicant.string/render hiccup & [{:keys [indent]}])`

Render "replicant flavored hiccup" to a string. Optionally pass `:indent` to
format the HTML string for human consumption.

### Keyword reference

Keywords in the attributes map:

- `:replicant/on-mount` - A hook to be called when the element mounts. Either a
  function or arbitrary data, see [data-driven hooks](#data-hooks).
- `:replicant/on-unmount` - A hook to be called when the element unmounts.
  Either a function or arbitrary data, see [data-driven hooks](#data-hooks).
- `:replicant/on-render` - A hook to be called when the element renders
  (including when it mounts and unmounts). Either a function or arbitrary data,
  see [data-driven hooks](#data-hooks).
- `:replicant/mounting` - Attribute (including class, styles) overrides to apply
  while node is mounting, see [data-driven transitions](#data-transitions).
- `:replicant/unmounting` - Attribute (including class, styles) overrides to apply
  while node is unmounting, see [data-driven transitions](#data-transitions).
- `:replicant/on-update` - A hook to be called when the element renders,
  excluding when it mounts and unmounts. Either a function or arbitrary data,
  see [data-driven hooks](#data-hooks).

Keywords used with [hook and event handler dispatch](#set-dispatch):

- `:replicant/trigger`
- `:replicant.trigger/dom-event`
- `:replicant.trigger/life-cycle`
- `:replicant/js-event`
- `:replicant/node`
- `:replicant.life-cycle/mount`
- `:replicant.life-cycle/unmount`
- `:replicant.life-cycle/update`
- `:replicant/details`
- `:replicant/move-node`
- `:replicant/updated-attrs`
- `:replicant/updated-children`

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

Replicant is a **rendering library**. That's it. There's no state management,
there's no async rendering, there's no networking utilities. There's just a
single function that renders and rerenders your hiccup to the DOM in an
efficient manner.

### What about components?

Replicant does not have components. Some common reasons to have components
include:

1. Capture UI components in reusable artifacts
2. Use knowledge about domain data to short-circuit rendering for performance
3. Attach life-cycle hooks (used for animations, DOM manipulation, etc)
4. Wire in cross-cutting concerns (i18n, theming, etc)

Clojure provides the function as a mechanism for creating reusable pieces of
logic. Components are just functions that return hiccup, e.g. something like
`(button {,,,})` which returns the appropriate hiccup for a button.

Short-circuiting rendering (e.g. something akin to React's original
`shouldComponentUpdate`) is generally not necessary, as Replicant is already
efficient enough. Should you have some heavy transformations from domain data to
hiccup, you can use `memoize` or other more specialized tools. Since
"components" are just functions that return hiccup, you don't need framework
specific tooling to optimize your code.

Life-cycles are genuinely useful. That's why you can attach them directly to
hiccup nodes, and Replicant will trigger them for you. You are not limited to
placing hooks on "components" - any node in the hiccup tree can have them.

Replicant does not **need** to know about cross-cutting concerns like i18n,
theming, etc. Since the entire UI can be represented as data, you can implement
concerns like these with pure data transformations. However, there are speed
gains to be had if you can postpone such transformations to just in time for
rendering, which is why Replicant will eventually provide a hook for this. The
hook will be a global one, and does not necessitate a bespoke component
abstraction.

## Contribute

Want to help make it fast? Awesome, please help in any way you can.

## Tests

There are tests. Run them like so:

```sh
clojure -X:dev:test
```

...or start a REPL and evaluate at will.

## Benchmarking

To run the benchmark, check out
[js-framework-benchmark](https://github.com/cjohansen/js-framework-benchmark) and
follow these steps:

```sh
git checkout https://github.com/cjohansen/js-framework-benchmark.git
cd js-framework-benchmark
npm ci
npm run install-server
npm start
```

Leave the server running.

### Compiling the test runner

In another terminal:

```sh
cd webdriver-ts
npm ci
npm run compile
```

### Build Replicant

Finally, build Replicant to run its benchmark:

```sh
cd frameworks/keyed/replicant
npm run build-prod
```

You should now be able to manually interact with Replicant on
http://localhost:8080/frameworks/keyed/replicant/

### Run the benchmark

With Replicant built, you can go to the root directory to run the benchmark:

```sh
npm run bench keyed/replicant
```

This will take about 5 minutes, and Chrome will open and close several times.
When it's done, generate the report:

```sh
npm run results
```

And open http://localhost:8080/webdriver-ts-results/dist/index.html

It is also possible to run an all-in-one benchmark, validation, and report. This
will run the benchmark headlessly:

```sh
npm run rebuild-ci keyed/replicant
```

### Comparing frameworks

It can be useful to compare to some other frameworks. To do so, build them and
run the benchmark for each one. Here are some suggestions:

```sh
cd frameworks/keyed/react
npm run build-prod
cd ../..
npm run bench keyed/react

cd frameworks/keyed/reagent
npm run build-prod
cd ../..
npm run bench keyed/reagent

cd frameworks/keyed/vanillajs
npm run build-prod
cd ../..
npm run bench keyed/vanillajs
```

### Benchmarking and testing changes

If you want to try to optimize or tweak Replicant, you might want to run several
benchmarks. For all I know, the directory name is used to key the frameworks in
the report, so my current workflow consists of copying the
`frameworks/keyed/replicant` directory, making changes to it, and running the
benchmark, e.g.:

```sh
cp -r frameworks/keyed/replicant frameworks/keyed/replicant-head
cd frameworks/keyed/replicant-head
cp -r ~/projects/replicant/src/replicant src/.
cd ../../../
npm run bench keyed/replicant-head
npm run results
```

If you make changes to the code, you can start by running a faster test, to
avoid running a long benchmark on broken code:

```sh
npm run isKeyed -- --headless true keyed/replicant-xyz
```
