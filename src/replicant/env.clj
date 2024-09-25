(ns replicant.env
  (:require [cljs.env :as env]
            [clojure.string :as str]))

(defn get-property [config]
  (System/getProperty
   (str (when-let [ns (namespace config)]
          (str ns ".")) (str/replace (name config) #"\?" ""))))

(defn get-define [config]
  (str (when-let [ns (namespace config)]
         (str ns "/")) (name config)))

(defn get-config [config]
  (if-let [options (or (some-> cljs.env/*compiler* deref :options)
                       (when (get-property config)
                         {config true}))]
    (let [define (get-define config)]
      (cond
        (contains? options config)
        (config options)

        (contains? (:closure-defines options) define)
        (get-in options [:closure-defines define])

        :else nil))
    nil))

(defn enabled? [config & [default]]
  (boolean (if-let [cfg (get-config config)]
             (get cfg config)
             default)))

(defn dev? []
  (if-let [options (some-> cljs.env/*compiler* deref :options)]
    (not (#{:advanced :simple} (:optimizations options)))
    (enabled? :replicant/dev?)))
