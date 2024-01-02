(ns replicant.hiccup)

(defmacro tag-name [headers]
  `(aget ~headers 0))

(defmacro id [headers]
  `(aget ~headers 1))

(defmacro classes [headers]
  `(aget ~headers 2))

(defmacro rkey [headers]
  `(aget ~headers 3))

(defmacro attrs [headers]
  `(aget ~headers 4))

(defmacro children [headers]
  `(aget ~headers 5))

(defmacro html-ns [headers]
  `(aget ~headers 6))

(defmacro sexp [headers]
  `(aget ~headers 7))
