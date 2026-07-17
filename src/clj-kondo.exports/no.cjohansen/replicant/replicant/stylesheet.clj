(ns replicant.stylesheet
  (:require [clj-kondo.hooks-api :as api]))

(defn selector-node? [node]
  (or (symbol? (:value node))
      (and (api/vector-node? node)
           (every? (comp symbol? :value) (:children node)))))

(defn nb! [node message]
  (api/reg-finding!
   (assoc (meta node)
          :message message
          :type :replicant/stylesheet)))

(defn stylesheet [{:keys [node]}]
  (let [selector->styles (next (:children node))]
    (when (not= 0 (mod (count selector->styles) 2))
      (let [tail (last selector->styles)]
        (nb! node (str "Uneven number of forms in replicant.css/stylesheet"
                       (when (selector-node? tail)
                         (str ", possibly missing styles for selector "
                              (api/sexpr tail)))))))
    {:node
     (->> (partition 2 selector->styles)
          (mapcat
           (fn [[k v]]
             (when-not (selector-node? k)
               (nb! k "Not a selector: use a symbol or vector of symbols"))
             (if (api/map-node? v)
               (doseq [[k-node v-node] (partition 2 (:children v))]
                 (when-not (or (api/keyword-node? k-node)
                               (symbol? (:value k-node)))
                   (nb! k-node "Expected a keyword or symbol"))
                 (when-not (or (api/string-node? v-node)
                               (number? (:value v-node))
                               (keyword? (:value v-node))
                               (symbol? (:value v-node)))
                   (nb! v-node "Expected a string, number, keyword or symbol")))
               (nb! v "Expected a map of style rules"))
             [(api/string-node (str (api/sexpr k)))
              (api/string-node (str (api/sexpr v)))]))
          api/map-node)}))

(comment
  (require '[clj-kondo.core :as clj-kondo])

  (defn get-findings [code]
    (:findings
     (with-in-str
       (str
        '(require '[replicant.css :as css])
        code)
       (clj-kondo.core/run! {:lint ["-"]}))))

  (def code
    '(css/stylesheet
       .foo {:color "red"}
       .bar {:color "blue"}

       [div > span:last-child .haha]
       {color "green"
        :width 2
        :background "blue"}))

  (stylesheet {:node (api/parse-string (str code))})

  (get-findings
   '(css/stylesheet
     div {:color "red"}
     .bar {:color "blue"}

     [div > span:last-child .haha]
     {:color "green"
      :background "blue"}))

)
