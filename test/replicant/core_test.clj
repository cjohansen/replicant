(ns replicant.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [replicant.core :as sut]))

(deftest hiccup-test
  (testing "Normalizes hiccup structure"
    (is (= (sut/get-hiccup-headers [:h1 "Hello world"] nil)
           ["h1" nil nil nil {} ["Hello world"] nil])))

  (testing "Flattens children"
    (is (= (->> (sut/get-hiccup-headers [:h1 (list (list "Hello world"))] nil)
                (sut/get-children nil))
           ["Hello world"])))

  (testing "Pixelizes styles"
    (is (= (->> (sut/get-hiccup-headers [:div {:style {:height 450}}] nil)
                sut/get-attrs
                :style
                :height
                (sut/get-style-val :height))
           "450px")))

  (testing "Doesn't pixelize all styles"
    (is (= (->> (sut/get-hiccup-headers [:div {:style {:z-index 999}}] nil)
                sut/get-attrs
                :style
                :z-index
                (sut/get-style-val :z-index))
           "999"))))
