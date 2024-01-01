(ns replicant.vdom)

(defmacro tag-name [vdom]
  `(nth ~vdom 0))

(defmacro attrs [vdom]
  `(nth ~vdom 1))

(defmacro children [vdom]
  `(nth ~vdom 2))

(defmacro sexp [vdom]
  `(nth ~vdom 3))
