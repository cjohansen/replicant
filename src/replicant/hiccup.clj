(ns replicant.hiccup)

(defmacro tag-name [headers]
  `(nth ~headers 0))

(defmacro id [headers]
  `(nth ~headers 1))

(defmacro class [headers]
  `(nth ~headers 2))

(defmacro rkey [headers]
  `(nth ~headers 3))

(defmacro attrs [headers]
  `(nth ~headers 4))

(defmacro children [headers]
  `(nth ~headers 5))

(defmacro html-ns [headers]
  `(nth ~headers 6))

(defmacro sexp [headers]
  `(nth ~headers 7))
