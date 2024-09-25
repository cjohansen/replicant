(ns replicant.defalias
  (:require [clj-kondo.hooks-api :as api]))

(defn- extract-docstr
  [[docstr? & forms :as remaining-forms]]
  (if (api/string-node? docstr?)
    [docstr? forms]
    [(api/string-node "no docs") remaining-forms]))

(defn defalias [{:keys [node]}]
  (let [[fname & forms] (rest (:children node))
        [docstr [attr-map & body]] (extract-docstr forms)]
    {:node
     (api/list-node
      (list*
       (api/token-node 'defn)
       fname
       docstr
       attr-map
       body))}))

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
    '(defalias button [attrs children]
       [:button {:on {:click (:actions attrs)}}
        children]))

  (defalias {:node (api/parse-string (str code))})
  (get-findings code)

  )
