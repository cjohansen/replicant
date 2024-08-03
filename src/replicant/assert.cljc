(ns replicant.assert
  (:require [cljs.env :as env]
            [replicant.console-logger :as console]
            [replicant.hiccup :as hiccup])
  (:refer-clojure :exclude [assert])
  #?(:cljs (:require-macros [replicant.assert])))

(def current-context (atom nil))
(def current-node (atom nil))
(def error (atom nil))

(defn assert? []
  (if-let [options (and cljs.env/*compiler*
                        (:options @cljs.env/*compiler*))]
    (cond
      (contains? options :replicant/asserts?)
      (:replicant/asserts? options)

      (contains? (:closure-defines options) "replicant/asserts?")
      (get-in options [:closure-defines "replicant/asserts?"])

      (not (#{:advanced :simple} (:optimizations options)))
      true

      :else false)
    false))

(defmacro enter-node [headers]
  (when (assert?)
    `(when ~headers
       (when-let [ctx# (:replicant/context (hiccup/attrs ~headers))]
         (reset! current-context ctx#))
       (reset! current-node (hiccup/sexp ~headers)))))

(defmacro assert [test title message & [hiccup]]
  (when (assert?)
    `(when (not ~test)
       (let [fn# (:fn-name @current-context)
             fd# (:data @current-context)]
         (reset! error
          (cond-> {:title ~title
                   :message ~message
                   :hiccup (or ~hiccup @current-node)}
            fn# (assoc :fname fn#)
            fd# (assoc :data fd#)))))))

;; Install default reporter

(defmacro configure []
  (when (assert?)
    `(add-watch error ::default (fn [_# _# _# error#] (console/report error#)))))

;; API

(defn add-reporter [k f]
  (remove-watch error ::default)
  (add-watch error k (fn [_ _ _ error] (f error))))

(defn remove-reporter [k]
  (remove-watch error k))
