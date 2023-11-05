(ns replicant.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [replicant.core :as sut]))

(deftest swaps?-test
  (testing "No position swaps"
    (is (false? (-> (sut/get-position-swaps
                     sut/same?
                     [[:h1 {} "Title"]
                      [:p {} "Text 1"]]
                     [[:h1 {} "Title"]
                      [:p {} "Text 1"]])
                    sut/swaps?))))

  (testing "Elements switched position"
    (is (true? (-> (sut/get-position-swaps
                    sut/same?
                    [[:p {} "Text 1"]
                     [:h1 {} "Title"]]
                    [[:h1 {} "Title"]
                     [:p {} "Text 1"]])
                   sut/swaps?))))

  (testing "Adding at the end is not a swap"
    (is (false? (-> (sut/get-position-swaps
                     sut/same?
                     [[:h1 "Title"]
                      [:p "Text 1"]
                      [:p "New"]]
                     [[:h1 {} "Title"]
                      [:p {} "Text 1"]])
                    sut/swaps?))))

  (testing "Removing from the end is not a swap"
    (is (false? (-> (sut/get-position-swaps
                     sut/same?
                     [[:h1 "Title"]
                      [:p "Text 1"]]
                     [[:h1 {} "Title"]
                      [:p {} "Text 1"]
                      [:p "New"]])
                    sut/swaps?)))))
