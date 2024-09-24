(ns replicant.test
  (:require [clojure.walk :as walk]
            [replicant.alias :as alias]
            [replicant.core :as r]
            [replicant.hiccup :as hiccup]))

(defn alias-hiccup? [x]
  (and (r/hiccup? x) (qualified-keyword? (first x))))

(defn ->hiccup [headers]
  (when headers
    (or (hiccup/text headers)
        (into [(keyword (hiccup/tag-name headers))
               (let [attrs (r/get-attrs headers)]
                 (cond-> (hiccup/attrs headers)
                   (:id attrs) (assoc :id (:id attrs))
                   (:classes attrs) (assoc :class (set (:classes attrs)))))]
              (r/flatten-seqs (hiccup/children headers))))))

(defn expand-aliased-hiccup [x opt]
  (if (alias-hiccup? x)
    (->> (r/get-hiccup-headers nil x)
         (r/get-alias-headers opt)
         ->hiccup)
    x))

(defn get-opts [aliases]
  {:aliases (or aliases (alias/get-aliases))})

(defn aliasexpand-1 [hiccup & [{:keys [aliases]}]]
  (walk/postwalk #(expand-aliased-hiccup % (get-opts aliases)) hiccup))

(defn aliasexpand [hiccup & [{:keys [aliases]}]]
  (walk/prewalk #(expand-aliased-hiccup % (get-opts aliases)) hiccup))
