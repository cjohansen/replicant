(ns replicant.css-test
  (:require [clojure.test :refer [deftest is testing]]
            [replicant.css :as css]))

(deftest stylesheet-test
  (testing "Creates string"
    (is (= (css/stylesheet
            .foo {:color "red"}
            .bar {:color "blue"}

            [div > span:last-child .haha]
            {:color "green"
             :background "blue"})
           ".foo{color:red}.bar{color:blue}div > span:last-child .haha{color:green;background:blue}")))

  (testing "Pixelizes values"
    (is (= (css/stylesheet .foo {:width 2}) ".foo{width:2px}"))))
