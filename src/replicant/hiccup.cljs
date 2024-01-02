(ns replicant.hiccup
  (:require-macros [replicant.hiccup]))

(defn headers? [x]
  (array? x))
