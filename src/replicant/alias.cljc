(ns replicant.alias
  (:require [clojure.walk :as walk]
            [replicant.assert :as assert]
            [replicant.core :as r]
            [replicant.hiccup :as hiccup])
  #?(:cljs (:require-macros [replicant.alias])))

(def aliases (atom {}))

(defmacro ^{:indent 2} aliasfn [alias & forms]
  (let [[_docstring [attr-map & body]]
        (if (string? (first forms))
          [(first forms) (next forms)]
          ["" forms])
        n-args (count attr-map)
        attr-map (cond
                   (= 0 n-args)
                   '[_ _]

                   (= 1 n-args)
                   (conj attr-map '_)

                   :else
                   attr-map)]
    (if (assert/assert?)
      `(with-meta
         (fn [& args#]
           (let [~attr-map args#
                 res# (do ~@body)]
             (cond-> res#
               (vector? res#)
               (with-meta
                 {:replicant/context
                  {:alias ~alias
                   :data (first args#)}}))))
         {:replicant/alias ~alias})
      `(with-meta (fn ~attr-map ~@body) {:replicant/alias ~alias}))))

(defmacro defalias [alias & forms]
  (let [alias-kw (keyword (str *ns*) (name alias))
        alias-f `(aliasfn ~alias-kw ~@forms)]
    `(let [f# ~alias-f
           alias# ~alias-kw]
       (swap! aliases assoc alias# f#)
       (def ~alias alias#))))

(defn get-registered-aliases []
  @aliases)

(defn ->hiccup [headers]
  (when headers
    (or (hiccup/text headers)
        (into [(keyword (hiccup/tag-name headers))
               (let [attrs (r/get-attrs headers)]
                 (cond-> (hiccup/attrs headers)
                   (:id attrs) (assoc :id (:id attrs))
                   (:classes attrs) (assoc :class (set (:classes attrs)))))]
              (r/flatten-seqs (hiccup/children headers))))))

(defn alias-hiccup? [x]
  (and (r/hiccup? x) (qualified-keyword? (first x))))

(defn expand-aliased-hiccup [x opt]
  (if (alias-hiccup? x)
    (let [headers (r/get-hiccup-headers nil x)
          defined? (get (:aliases opt) (hiccup/tag-name headers))]
      (when (and (not defined?) (false? (get opt :ignore-missing-alias? true)))
        (throw (ex-info (str "Tried to expand undefined alias " (hiccup/tag-name headers))
                        {:alias (hiccup/tag-name headers)})))
      (cond->> headers
        (get (:aliases opt) (hiccup/tag-name headers))
        (r/get-alias-headers opt)

        :then ->hiccup))
    x))

(defn get-opts [opt]
  (update opt :aliases #(or % (get-registered-aliases))))

(defn expand-1 [hiccup & [{:keys [aliases] :as opt}]]
  (let [opt (get-opts opt)]
    (walk/postwalk #(expand-aliased-hiccup % opt) hiccup)))

(defn expand [hiccup & [{:keys [aliases] :as opt}]]
  (let [opt (get-opts opt)]
    (walk/prewalk #(expand-aliased-hiccup % opt) hiccup)))
