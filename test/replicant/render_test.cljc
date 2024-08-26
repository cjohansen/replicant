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

(def incremental-renders-are-as-good-as-initial-renders
  (prop/for-all [a gen-hiccup
                 b gen-hiccup]
   ;; Rendering b directly, or rendering b after first rendering a
   ;; should both produce the same final DOM structure.
   (= (-> (h/render a)
          (h/render b)
          h/->dom)
      (-> (h/render b)
          h/->dom))))

(defspec compare-incremental-renders-to-initial-renders 10000
  incremental-renders-are-as-good-as-initial-renders)

(comment

   (tc/quick-check 100000 incremental-renders-are-as-good-as-initial-renders)

   (do
     (def a [:p {} "0" "0"])
     (def b [:p {} "1"])

     [(-> (h/render a)
          (h/render b)
          h/->dom)
      (-> (h/render b)
          h/->dom)])

  (-> (h/render a)
      (h/render b)
      h/get-mutation-log-events
      h/summarize)

  )
