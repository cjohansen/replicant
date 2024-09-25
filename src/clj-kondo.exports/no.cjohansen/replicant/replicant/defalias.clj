(ns replicant.defalias
  (:require [clj-kondo.hooks-api :as api]))

(defn- extract-docstr
  [[docstr? & forms :as remaining-forms]]
  (if (api/string-node? docstr?)
    [docstr? forms]
    [(api/string-node "no docs") remaining-forms]))

(defn defalias [{:keys [node]}]
  (let [[fname & forms] (rest (:children node))
        fname-sexp (api/sexpr fname)
        [docstr [attr-map & body]] (extract-docstr forms)]
    (when-not (qualified-keyword? fname-sexp)
      (api/reg-finding! (assoc (meta fname)
                               :message (str "Alias name must be qualified keyword: `" fname-sexp "`")
                               :type :replicant/alias)))
    {:node
     (api/token-node
      (list*
       (api/token-node 'do)
       (api/list-node
        (list*
         (api/token-node 'defn)
         (api/token-node (symbol (str (name (api/sexpr fname)))))
         docstr
         attr-map
         body))
       (api/token-node (symbol (str (name (api/sexpr fname)))))))}))

(comment
  (require '[clj-kondo.core :as clj-kondo])

  (defn get-findings [code]
    (:findings
     (with-in-str
       (str
        '(require '[replicant.alias :refer [defalias]])
        code)
       (clj-kondo.core/run! {:lint ["-"]}))))

  (def code
    '(defalias :ui/button [attrs children]
       [:button {:on {:click (:actions attrs)}}
        children]))

  (defalias {:node (api/parse-string (str code))})
  (get-findings code)

  )
