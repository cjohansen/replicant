# Replicant

<svg xmlns="http://www.w3.org/2000/svg"
     xml:space="preserve"
     style="float:right; width: 96px; height: 96px;fill-rule:evenodd;clip-rule:evenodd;stroke-linejoin:round;stroke-miterlimit:2"
     viewBox="0 0 1080 1080">
  <path fill="#4fb348" d="M876.452 152.555c136.818 12.348 195.668 81.688 192.708 196.198h-87.197c1.569-66.295-16.004-119.066-52.748-157.356-14.398-15.003-31.913-27.882-52.763-38.353v-.489Zm98.715 270.164h87.213c-9.48 55.52-38.53 92.323-91.749 108.344 55.789 39.542 76.849 90.867 66.189 152.957l-44.388 184.981h-86.379l42.751-178.151c.149-.622.278-1.249.386-1.879 9.983-58.16-3.188-108.49-41.722-150.197 36.949-23.921 58.758-61.19 67.282-111.131a29.34 29.34 0 0 0 .417-4.924Z"/>
  <path fill="#00a29a" d="M757.149 152.309c138.837 11.646 198.514 81.149 195.537 196.444h-88.511c1.643-69.4-17.651-123.977-58.046-162.647-13.679-13.096-29.954-24.431-48.98-33.797Zm101.023 270.41h87.726c-9.475 55.52-38.529 92.323-91.745 108.344 55.785 39.542 76.848 90.867 66.191 152.957l-44.391 184.981h-87.689l42.752-178.151c.149-.622.278-1.249.386-1.879 9.983-58.16-3.188-108.49-41.722-150.197 36.948-23.921 58.758-61.19 67.281-111.131.153-.894.263-1.79.332-2.685h.506c.126-.747.25-1.493.373-2.239Z"/>
  <path fill="#0086ff" d="M76.208 847.157c-36.752-5.562-64.959-37.32-64.959-75.614 0-42.21 34.269-76.478 76.478-76.478a76.45 76.45 0 0 1 20.676 2.832c32.178 9.028 55.802 38.6 55.802 73.646 0 42.209-34.268 76.478-76.478 76.478-3.915 0-7.761-.295-11.519-.864Zm56.524-262.05a77.324 77.324 0 0 1-6.804.299c-42.209 0-76.478-34.268-76.478-76.478 0-42.209 34.269-76.478 76.478-76.478 13.663 0 26.494 3.591 37.6 9.879 23.204 13.139 38.878 38.053 38.878 66.599 0 39.917-30.647 72.732-69.674 76.179Zm-18.148 84.136 11.77-54.569c58.168-.23 105.321-47.525 105.321-105.746 0-35.555-17.586-67.035-44.526-86.209H828.11c-9.475 55.52-38.529 92.323-91.745 108.344 55.784 39.542 76.848 90.867 66.191 152.957l-44.391 184.981H539.756l34.836-151.189c13.135-57.004-4.455-118.062-91.882-118.062H353.796l-71.217 274.677H112.245c46.557-11.07 81.229-52.96 81.229-102.884 0-49.085-33.515-90.403-78.89-102.3Zm-1.803-330.67 117.483-187.485h389.845c152.584 6.696 217.9 77.175 214.789 197.665H178.595v-10.18h-65.814Z"/>
</svg>

Replicant is a data-driven rendering library for Clojure(Script). It renders
hiccup to strings or DOM nodes. Over and over. Efficiently, without a single
dependency.

## Install

```clj
no.cjohansen/replicant {:mvn/version "2025.01.28"}
```

## Documentation

Learn about using Replicant:

- [Replicant user guide](https://replicant.fun/learn/)
- [Reference API docs](https://cljdoc.org/d/no.cjohansen/replicant/)
- [Replicant in the wild](https://replicant.fun/in-the-wild/)

## Design goal

The user interface is a function of application state. Whenever state changes,
call this function to update the UI. The function receives all application state
and returns the entire user interface as hiccup data -- every time. State (data)
in, hiccup (data) out. No local state, no atoms, no subscriptions, no
networking. Just a pure function from data to hiccup.

Replicant's design goal is to make this programming model feasible.

Replicant is a **rendering library**. That's it. There's no state management, no
async rendering, no networking utilities. There's just a single function that
renders and rerenders your hiccup to the DOM in an efficient manner.

## Inspiration

In 2013, React launched with the idea that your UI was just a function of your
application state. I still believe this is the best idea the frontend
development community has had in the past 20 years.

While working on [Dumdom](https://github.com/cjohansen/dumdom),
[Anders](https://github.com/duckyuck), [Magnar](https://magnars.com/) and I
discovered how to make functions such as event handlers data-driven, without
making the library prescriptive.

[Snabbdom](https://github.com/snabbdom) taught me that components are not a
necessary feature of a virtual DOM renderer. Life-cycle hooks can just as easily
be attached to DOM nodes.

[m1p](https://github.com/cjohansen/m1p) spawned the idea that the rendering
library could resolve placeholders like `[:i18n ::some-key]` during rendering,
eliminating an entire `walk` through the UI - an idea that turned in Replican
aliases (whose name was inspired by
[chassis](https://github.com/onionpancakes/chassis)).

## Interoperability

Replicant interoperates nicely with anything that works on data. It has hooks
that exposes DOM elements for interoperability with other libraries.

Interoperability with component libraries like React is technically possible
through hooks, but not recommended. Virtual DOM libraries and frameworks are not
lightweight enough that I would encourage anyone to use more than one of them in
the same application.

Replicant was very deliberately designed to not include certain features popular
in other rendering libraries, such as component local state (or components, for
that matter). Enabling Replicant to render components written for other
libraries would effectively introduce an escape hatch that would undermine
Replicant's assumptions. Given that I don't see "reusable components across
libraries and frameworks" as an attractive goal, this is not complexity I am
interesting in taking on.

For truly reusable extensions of the browser you can use web components (like
[u-elements](https://u-elements.github.io/)) and have Replicant render custom
elements, e.g.:

```clj
[:u-tabs
 [:u-tablist
  [:u-tab "Tab 1"]
  [:u-tab "Tab 2"]
  [:u-tab "Tab 3"]]
 [:u-tabpanel "Panel 1"]
 [:u-tabpanel "Panel 2"]
 [:u-tabpanel "Panel 3"]]
```

## Status

Replicant is stable, performant and feature complete. Its public APIs will not
be intentionally changed. It's used in production by several apps.

## Performance

Replicant performance is being tuned using
https://github.com/krausest/js-framework-benchmark. See [benchmarking
instructions](benchmarking.md) for how to run locally.

## Contribute

Want to help make it fast? Fix a bug? Awesome, please help in any way you can.
Bug fixes should come with test cases demonstrating the problem. Performance
improvements should come with some sort of numbers demonstrating.

Run tests with:

```sh
clojure -X:dev:test
```

...or start a REPL and evaluate at will.

If you have an idea for a new feature, please discuss it before you write any
code. Open an issue or drop by
[#replicant](https://clojurians.slack.com/archives/C06JZ4X334N) on the
[Clojurians Slack](http://clojurians.net/).

## Changelog

### 2025.01.28

First public release.
