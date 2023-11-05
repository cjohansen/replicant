(ns replicant.hiccup-test
  (:require [clojure.test :refer [deftest is testing]]
            [replicant.hiccup :as sut]))

(deftest inflate-hiccup-test
  (testing "Normalizes hiccup structure"
    (is (= (sut/inflate [:h1 "Hello world"])
           {:tag-name "h1"
            :attrs {}
            :children ["Hello world"]})))

  (testing "Flattens children"
    (is (= (sut/inflate [:h1 (list (list "Hello world"))])
           {:tag-name "h1"
            :attrs {}
            :children ["Hello world"]})))

  (testing "Ignores tag name casing"
    (is (= (sut/inflate [:SVG {:viewBox "0 0 100 100"}])
           {:tag-name "svg"
            :attrs {:viewBox "0 0 100 100"}
            :children nil})))

  (testing "Pixelizes styles"
    (is (= (-> (sut/inflate [:div {:style {:height 450}}])
               :attrs
               :style)
           {:height "450px"})))

  (testing "Doesn't pixelize all styles"
    (is (= (-> (sut/inflate [:div {:style {:z-index 999}}])
               :attrs
               :style)
           {:z-index "999"}))))
