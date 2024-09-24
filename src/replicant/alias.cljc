(ns replicant.alias
  (:require [replicant.assert :as assert])
  #?(:cljs (:require-macros [replicant.alias])))

(def aliases (atom {}))

(defmacro defalias [alias & forms]
  (let [[_docstring [attr-map & body]]
        (if (string? (first forms))
          [(first forms) (next forms)]
          ["" forms])
        alias-f (if (assert/assert?)
                  `(fn [& args#]
                     (let [~attr-map args#]
                       (some-> (do ~@body)
                               (with-meta {:replicant/context
                                           {:alias ~alias
                                            :data (first args#)}}))))
                  `(fn ~attr-map ~@body))]
    `(let [f# ~alias-f]
       (swap! aliases assoc ~alias f#)
       nil)))

(defn get-aliases []
  @aliases)
