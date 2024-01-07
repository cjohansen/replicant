(ns replicant.transition-test
  (:require [clojure.test :refer [deftest is testing]]
            [replicant.transition :as sut]))

(deftest get-transition-stats-test
  (testing "Reports on no transitions"
    (is (= (sut/get-transition-stats nil) [0 0])))

  (testing "Reports on no transitions from blank string"
    (is (= (sut/get-transition-stats "") [0 0])))

  (testing "Finds single second transition"
    (is (= (sut/get-transition-stats "0.25s") [1 250])))

  (testing "Finds single ms transition"
    (is (= (sut/get-transition-stats "250ms") [1 250])))

  (testing "Finds multiple transitions"
    (is (= (sut/get-transition-stats "250ms, 0.1s") [2 350]))))
