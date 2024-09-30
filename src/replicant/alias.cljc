(ns replicant.alias
  (:require [clojure.walk :as walk]
            [replicant.assert :as assert]
            [replicant.core :as r]
            #?(:clj [replicant.env :as env])
            [replicant.hiccup :as hiccup])
  #?(:cljs (:require-macros [replicant.alias])))

(def aliases (atom {}))

(defmacro aliasfn [alias & forms]
  (let [[_docstring [attr-map & body]]
        (if (string? (first forms))
          [(first forms) (next forms)]
          ["" forms])
        alias-kw (keyword (str *ns*) (name alias))]
    (if (assert/assert?)
      `(with-meta
         (fn [& args#]
           (let [~attr-map args#]
             (some-> (do ~@body)
                     (with-meta {:replicant/context
                                 {:alias ~alias-kw
                                  :data (first args#)}}))))
         {:replicant/alias ~alias-kw})
      `(with-meta (fn ~attr-map ~@body) {:replicant/alias ~alias-kw}))))

(defmacro defalias [alias & forms]
  (let [alias-f `(aliasfn ~alias ~@forms)]
    `(let [f# ~alias-f
           alias# (:replicant/alias (meta ~alias-f))]
       (swap! aliases assoc alias# f#)
       (def ~alias alias#))))

(defmacro key-hiccup [hiccup aliases]
  #?(:clj (env/with-dev-keys hiccup aliases)
     ;; Just to silence clj-kondo
     :cljs (let [_ aliases]
             hiccup)))

(defn get-aliases []
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
    (let [headers (r/get-hiccup-headers nil x)]
      (cond->> headers
        (or (get (:aliases opt) (hiccup/ident headers))
            (false? (get opt :ignore-missing-alias? true)))
        (r/get-alias-headers opt)

        :then ->hiccup))
    x))

(defn get-opts [opt]
  (update opt :aliases #(or % (get-aliases))))

(defn expand-1 [hiccup & [{:keys [aliases] :as opt}]]
  (let [opt (get-opts opt)]
    (walk/postwalk #(expand-aliased-hiccup % opt) hiccup)))

(defn expand [hiccup & [{:keys [aliases] :as opt}]]
  (let [opt (get-opts opt)]
    (walk/prewalk #(expand-aliased-hiccup % opt) hiccup)))
