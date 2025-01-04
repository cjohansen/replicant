(ns replicant.dom)

;; This namespace makes it possible to use these functions in cljc files.

(defn ^:export render [_el _hiccup & [_opt]])

(defn ^:export unmount [_el])

(defn ^:export set-dispatch! [_f])
