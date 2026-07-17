(ns replicant.css
  (:require [clojure.string :as str]
            [replicant.core :as r])
  #?(:cljs (:require-macros [replicant.css])))

(defn selector->string [selector]
  (if (coll? selector)
    (str/join " " selector)
    (str selector)))

(defn styles->string [styles]
  (str "{" (->> styles
                (mapv (fn [[k v]]
                        (str (name k) ":" (r/get-style-val k v))))
                (str/join ";")) "}"))

(defmacro stylesheet [& selector->styles]
  (->> selector->styles
       (partition 2)
       (map (juxt (comp selector->string first)
                  (comp styles->string second)))
       (map str/join)
       str/join))
