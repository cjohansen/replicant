(ns replicant.assert
  (:require #?(:clj [replicant.env :as env])
            [replicant.console-logger :as console]
            [replicant.hiccup-headers :as hiccup])
  (:refer-clojure :exclude [assert])
  #?(:cljs (:require-macros [replicant.assert])))

(def current-context (atom nil))
(def current-node (atom nil))
(def error (atom nil))

(defn ^:no-doc assert? []
  #?(:clj (env/enabled? :replicant/asserts? (env/dev?))))

(defmacro ^:no-doc enter-node [headers]
  (when (assert?)
    `(when ~headers
       (when-let [ctx# (or (:replicant/context (hiccup/attrs ~headers))
                           (:replicant/context (meta (hiccup/sexp ~headers))))]
         (reset! current-context ctx#))
       (reset! current-node (hiccup/sexp ~headers)))))

(defmacro ^:no-doc assert [test title message & [hiccup]]
  (when (assert?)
    `(when (not ~test)
       (let [fn# (:fn-name @current-context)
             alias# (:alias @current-context)
             fd# (:data @current-context)]
         (reset! error
          (cond-> {:title ~title
                   :message ~message
                   :hiccup (or ~hiccup @current-node)}
            fn# (assoc :fname fn#)
            alias# (assoc :alias alias#)
            fd# (assoc :data fd#)))))))

;; Install default reporter

(defmacro ^:no-doc configure []
  (when (assert?)
    `(add-watch error ::default (fn [_# _# _# error#] (console/report error#)))))

;; API

(defn ^:export add-reporter
  "Add assert error exporter. `k` is a keyword, `f` is a function that will be
  called with an assert error, a map of
  `{:title :message :hiccup :fname :alias :data}`."
  [k f]
  (remove-watch error ::default)
  (add-watch error k (fn [_ _ _ error] (f error))))

(defn ^:export remove-reporter
  "Remove a previously added reporter, using the same `k` that was used to
  register it. To remove the default reporter, use `:replicant.assert/default`
  as `k`."
  [k]
  (remove-watch error k))
