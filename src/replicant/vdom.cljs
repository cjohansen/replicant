(ns replicant.vdom
  (:require-macros [replicant.vdom]))

(defn create [tag-name attrs children child-ks sexp]
  #js [tag-name attrs children child-ks sexp])

(defn vdom? [x]
  (array? x))
