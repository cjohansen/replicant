(ns replicant.env
  (:require [cljs.env :as env]
            [clojure.string :as str]))

(defn get-property [config]
  (not-empty
   (System/getProperty
    (str (when-let [ns (namespace config)]
           (str ns ".")) (str/replace (name config) #"\?" "")))))

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
  (if-let [options (some-> cljs.env/*compiler* deref :options)]
    (not (#{:advanced :simple} (:optimizations options)))
    (enabled? :replicant/dev?)))

(defmacro with-dev-keys [hiccup aliases]
  (if (dev?)
    `(let [hiccup# ~hiccup
           aliases# ~aliases]
       (if (map? (second hiccup#))
         (update-in hiccup# [1 :replicant/key] (fn [k#] [k# aliases#]))
         (into [(first hiccup#) {:replicant/key aliases#}] (rest hiccup#))))
    hiccup))
