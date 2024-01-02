(ns replicant.vdom)

(defmacro tag-name [vdom]
  `(aget ~vdom 0))

(defmacro attrs [vdom]
  `(aget ~vdom 1))

(defmacro children [vdom]
  `(aget ~vdom 2))

(defmacro child-ks [vdom]
  `(aget ~vdom 3))

(defmacro sexp [vdom]
  `(aget ~vdom 4))
