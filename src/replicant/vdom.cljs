(ns replicant.vdom
  (:require-macros [replicant.vdom]))

(defn create [tag-name attrs children child-ks sexp text]
  #js [tag-name (:replicant/key attrs) attrs children child-ks sexp text])
