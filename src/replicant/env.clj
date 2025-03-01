(ns ^:no-doc replicant.env
  (:require [clojure.string :as str]))

(def ^:private cljs-available?
  (try
    (require 'cljs.env)
    true
    (catch Throwable _ false)))

(defmacro get-cljs-options []
  (when cljs-available?
    `(some-> cljs.env/*compiler* deref :options)))

(defn get-property [config]
  (not-empty
   (System/getProperty
    (str (when-let [ns (namespace config)]
           (str ns ".")) (str/replace (name config) #"\?" "")))))

(defn get-define [config]
  (str (when-let [ns (namespace config)]
         (str ns "/")) (name config)))

(defn get-config [config]
  (if-let [options (or (get-cljs-options)
                       (when (get-property config)
                         {config true}))]
    (let [define (get-define config)]
      (cond
        (contains? options config)
        options

        (contains? (:closure-defines options) define)
        (get options :closure-defines)

        :else nil))
    nil))

(defn enabled? [config & [default]]
  (boolean (if-let [cfg (get-config config)]
             (get cfg config)
             default)))

(defn dev? []
  (if-let [options (get-cljs-options)]
    (not (#{:advanced :simple} (:optimizations options)))
    (enabled? :replicant/dev?)))

(defmacro with-dev-key [hiccup k]
  (if (dev?)
    `(let [hiccup# ~hiccup]
       (if (vector? hiccup#)
         (if (map? (second hiccup#))
           (update-in hiccup# [1 :replicant/key] (fn [k#]
                                                   (if k#
                                                     [k# ~k]
                                                     ~k)))
           (into [(first hiccup#) {:replicant/key ~k}] (rest hiccup#)))
         hiccup#))
    hiccup))
