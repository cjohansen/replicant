(ns replicant.hydration
  (:require [replicant.core :as r]
            [replicant.hiccup-headers :as hiccup]
            [replicant.protocols :as replicant]
            [replicant.vdom :as vdom]))

(defn hydrate-node [{:keys [renderer] :as impl} el headers]
  (or (when-let [text (hiccup/text headers)]
        (vdom/create-text-node text))

      (when-let [alias-headers (r/get-alias-headers impl headers)]
        (let [vdom (hydrate-node impl el alias-headers)
              k (hiccup/rkey alias-headers)]
          (vdom/from-hiccup
           headers
           (hiccup/attrs headers)
           [vdom]
           (cond-> #{} k (conj k))
           1)))

      (let [tag-name (hiccup/tag-name headers)
            ns (or (hiccup/html-ns headers)
                   (when (= "svg" tag-name)
                     "http://www.w3.org/2000/svg"))
            attrs (r/get-attrs headers)
            _ (when-let [event-listeners (:on attrs)]
                (r/add-event-listeners renderer el event-listeners))
            children-ns (if (= "foreignObject" tag-name) nil ns)
            [children ks n-children]
            (->> (r/get-children headers children-ns)
                 (reduce (fn [[children ks n] child-headers]
                           (if child-headers
                             (let [vdom (hydrate-node impl (replicant/get-child renderer el n) child-headers)
                                   k (vdom/rkey vdom)]
                               [(conj! children vdom) (cond-> ks k (conj! k)) (unchecked-inc-int n)])
                             [(conj! children nil) ks n]))
                         [(transient []) (transient #{}) 0]))]
        (vdom/from-hiccup
         headers
         attrs
         (persistent! children)
         (persistent! ks)
         n-children))))

(defn hydrate [renderer el hiccup {:keys [aliases alias-data on-alias-exception]}]
  (let [impl {:renderer renderer
              :aliases aliases
              :alias-data alias-data
              :on-alias-exception on-alias-exception}]
    {:vdom
     (->> (if (r/proper-seq? hiccup)
            hiccup
            [hiccup])
          (mapv
           (fn [idx hiccup]
             (hydrate-node impl (replicant/get-child renderer el idx) (r/get-hiccup-headers nil hiccup)))
           (range)))}))
