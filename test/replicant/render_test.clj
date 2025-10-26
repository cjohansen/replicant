(ns replicant.render-test
  (:require [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [replicant.test-helper :as h]
            [clojure.test.check.clojure-test :refer [defspec]]))

(s/def :replicant/key any?)
(s/def :hiccup/border int?)
(s/def :hiccup/src string?)
(s/def :hiccup/alt string?)
(s/def :hiccup/title string?)
(s/def :hiccup/class (s/coll-of (s/or :keyword simple-keyword? :string string?)))
(s/def :hiccup/attrs (s/keys :opt-un [:hiccup/border
                                      :hiccup/alt
                                      :hiccup/src
                                      :hiccup/alt
                                      :hiccup/title
                                      :hiccup/class]
                             :opt [:replicant/key]))

(def tag-names
  #{:div :span :p :h1 :h2 :h3})

(def gen-tag (gen/one-of (map gen/return tag-names)))
(def gen-attrs (s/gen :hiccup/attrs))

(def string-alphanumeric-nonempty
  (gen/such-that (complement str/blank?) gen/string-alphanumeric))

(defn hiccup* [inner]
  (gen/let [children (gen/vector inner)
            attributes gen-attrs
            html-tag gen-tag]
    (into [html-tag attributes] children)))

(def gen-hiccup
  (gen/recursive-gen hiccup* string-alphanumeric-nonempty))

(defn verify-indenpendent-rendering-order
  "Rendering b directly, or rendering b after first rendering a should both
  produce the same final DOM structure. Returns true when that is the case."
  [a b]
  (= (-> (h/render a)
         (h/render b)
         h/->dom)
     (-> (h/render b)
         h/->dom)))

(def incremental-renders-are-as-good-as-initial-renders
  (prop/for-all [a gen-hiccup
                 b gen-hiccup]
   (verify-indenpendent-rendering-order a b)))

(defspec compare-incremental-renders-to-initial-renders 10000
  incremental-renders-are-as-good-as-initial-renders)

(def sample-nodes #{[:div "A"]
                    [:div {:replicant/key "A"} "A"]
                    [:div "B"]
                    [:div {:replicant/key "B"} "B"]
                    [:div "C"]
                    [:div {:replicant/key "C"} "C"]
                    [:div "D"]
                    [:div {:replicant/key "D"} "D"]
                    [:div "E"]
                    [:div {:replicant/key "E"} "E"]
                    "Text A"
                    "Text B"
                    "Text C"
                    "Text D"
                    "Text E"})

(def gen-similar-hiccup
  (gen/let [children (gen/fmap set (gen/vector-distinct (gen/elements (vec sample-nodes))))]
    (into [:div] children)))

(def incremental-renders-are-as-good-as-initial-renders-for-similar-hiccup
  ;; This property uses a generator that is less random, increasing the chance
  ;; of reuse across the two hiccups.
  (prop/for-all [a gen-similar-hiccup
                 b gen-similar-hiccup]
    (verify-indenpendent-rendering-order a b)))

(defspec compare-incremental-renders-to-initial-renders-for-similar-hiccup 10000
  incremental-renders-are-as-good-as-initial-renders-for-similar-hiccup)

(comment

  (tc/quick-check 100000 incremental-renders-are-as-good-as-initial-renders)
  (tc/quick-check 100000 incremental-renders-are-as-good-as-initial-renders-for-similar-hiccup)

   (do
     (def a [:span {} "0" "0"])
     (def b [:span {} "1"])

     (def res-a (-> (h/render a)
                    (h/render b)
                    h/->dom))

     (def res-b (-> (h/render b)
                    h/->dom))

     (prn res-a)
     (println "vs")
     (prn res-b))

  (-> (h/render a)
      (h/render b)
      h/get-mutation-log-events
      h/summarize)
)
