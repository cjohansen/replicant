(ns replicant.hydration-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.walk :as walk]
            [replicant.test-helper :as h]))

(defn verify-same-vdom
  ([hiccup]
   (verify-same-vdom nil hiccup))
  ([opt hiccup]
   (let [rendered (h/render opt hiccup)]
     (is (= (:vdom (h/hydrate rendered hiccup))
            (:vdom rendered))))))

(deftest hydrate-vdom-test
  (testing "Creates vdom for text node"
    (verify-same-vdom "Hello"))

  (testing "Creates vdom for simple DOM node"
    (verify-same-vdom [:h1 "Hello"]))

  (testing "Creates vdom for simple DOM node"
    (verify-same-vdom
     [:div.flex
      {:style {:color "red"}
       :replicant/key [:key "main"]}
      [:h1 "Hello"]]))

  (testing "Create vdom for alias"
    (verify-same-vdom
     {:aliases {:custom/title (fn [_attrs [title]]
                                [:h1.alias title])}}
     [:custom/title "Hello world"]))

  (testing "Creates vdom from nested seqs"
    (verify-same-vdom [:h1 (list (list "Hello"))]))

  (testing "Creates vdom from top-level seqs"
    (verify-same-vdom (list [:h1 "Hello"]))))

(def f1 (fn []))

(defn hydrate
  ([hiccup]
   (hydrate nil hiccup))
  ([opt hiccup]
   (h/hydrate
    (->> hiccup
         (walk/postwalk
          (fn [x]
            (cond-> x
              (:on x) (dissoc :on))))
         (h/render opt))
    hiccup)))

(deftest hydrate-event-listener-test
  (testing "Adds event listener to root node"
    (is (= (-> (hydrate [:h1 {:on {:click f1}} "Hi!"])
               h/get-mutation-log-events
               h/summarize)
           [[:set-event-handler [:h1 "Hi!"] :click f1]])))

  (testing "Adds event handler with options"
    (is (= (-> (hydrate
                [:h1 {:on
                      {:click {:replicant.event/handler f1
                               :replicant.event/capture true}}}
                 "Hi!"])
               h/get-mutation-log-events
               h/summarize)
           [[:set-event-handler [:h1 "Hi!"] :click f1 {"capture" true}]])))

  (testing "Adds event handler on the right nested element"
    (is (= (-> (hydrate
                [:div
                 [:h1 "Hello"]
                 [:div.flex
                  nil
                  "Here it comes"
                  [:button {:on {:click f1}} "Click!"]]])
               h/get-mutation-log-events
               h/summarize)
           [[:set-event-handler [:button "Click!"] :click f1]]))))
