# <img src="logo.svg" align="right" width="200"> Replicant

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
