(ns replicant.vdom)

(defmacro tag-name [vdom]
  `(nth ~vdom 0))

(defmacro attrs [vdom]
  `(nth ~vdom 1))

(defmacro children [vdom]
  `(nth ~vdom 2))

(defmacro child-ks [vdom]
  `(nth ~vdom 3))

(defmacro sexp [vdom]
  `(nth ~vdom 4))

(defmacro create [tag-name attrs children child-ks sexp]
  `[~tag-name ~attrs ~children ~child-ks ~sexp])
