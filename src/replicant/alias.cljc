(ns replicant.alias
  (:require [clojure.walk :as walk]
            [replicant.assert :as assert]
            [replicant.core :as r]
            [replicant.hiccup :as h]
            [replicant.hiccup-headers :as hiccup])
  #?(:cljs (:require-macros [replicant.alias])))

(def ^:no-doc aliases (atom {}))

(defmacro ^{:indent 2} aliasfn
  "Define a function to use as an alias function. Creates a function that wraps
  returned hiccup with debugging meta data when Replicant asserts are
  enabled (e.g. during development). When asserts are not enabled (default for
  production builds), creates a regular function with no added overhead.

  `aliasfn` is most commonly used through `defalias`"
  [alias & forms]
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

(defn register!
  "Register an alias. Associates the alias key `k` with the function `f`:

   ```clj
   (replicant.alias/register! :ui/a custom-link)
   ```"
  [k f]
  (swap! aliases assoc k f))

(defmacro defalias
  "Creates a function to render `alias` (a namespaced keyword), and registers
  it in the global registry. See `aliasfn` for details about the created function.
  The global registry is available through `replicant.alias/get-registered-aliases`."
  [alias & forms]
  (let [alias-kw (keyword (str *ns*) (name alias))
        alias-f `(aliasfn ~alias-kw ~@forms)]
    `(let [f# ~alias-f
           alias# ~alias-kw]
       (register! alias# f#)
       (def ~alias alias#))))

(defn get-registered-aliases
  "Returns globally registered aliases"
  []
  @aliases)

(defn ^:no-doc ->hiccup [headers]
  (when headers
    (or (hiccup/text headers)
        (into [(keyword (hiccup/tag-name headers))
               (let [attrs (r/get-attrs headers)]
                 (cond-> (hiccup/attrs headers)
                   (:id attrs) (assoc :id (:id attrs))
                   (:classes attrs) (assoc :class (set (:classes attrs)))))]
              (r/flatten-seqs (hiccup/children headers))))))

(defn ^:no-doc alias-hiccup? [x]
  (and (h/hiccup? x) (qualified-keyword? (first x))))

(defn ^:no-doc expand-aliased-hiccup [x opt]
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

(defn ^:no-doc get-opts [opt]
  (update opt :aliases #(or % (get-registered-aliases))))

(defn expand-1
  "Expand the first level of aliases in `hiccup`. The result may contain aliases
  if returned by the top-level aliases. If using aliases that are not in the
  global registry, pass `:aliases` in `opt`."
  [hiccup & [opt]]
  (let [opt (get-opts opt)]
    (walk/postwalk #(expand-aliased-hiccup % opt) hiccup)))

(defn expand
  "Recursively expand all aliases in `hiccup`. The result will not contain
  aliases. If using aliases that are not in the global registry, pass `:aliases`
  in `opt`."
  [hiccup & [opt]]
  (let [opt (get-opts opt)]
    (walk/prewalk #(expand-aliased-hiccup % opt) hiccup)))
