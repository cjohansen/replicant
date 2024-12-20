(ns replicant.hiccup-test
  (:require [replicant.hiccup :as hiccup]
            [clojure.test :refer [deftest is testing]]))

(deftest update-attrs-test
  (testing "Assocs in a few attributes"
    (is (= (hiccup/update-attrs [:h1 "Hello"] assoc :title "Hi")
           [:h1 {:title "Hi"} "Hello"]))

    (is (= (hiccup/update-attrs [:h1 {:class "text-xl"} "Hello"] assoc :title "Hi")
           [:h1 {:class "text-xl"
                 :title "Hi"} "Hello"])))

  (testing "Removes an attribute"
    (is (= (hiccup/update-attrs [:h1 {:class "text-xl"} "Hello"] dissoc :class)
           [:h1 {} "Hello"]))

    (is (= (hiccup/update-attrs [:h1 "Hello"] dissoc :class)
           [:h1 {} "Hello"]))))

(deftest set-attr-test
  (testing "Sets a single attribute"
    (is (= (hiccup/set-attr [:h1 "Hello"] :title "Hi")
           [:h1 {:title "Hi"} "Hello"]))

    (is (= (hiccup/set-attr [:h1 {} "Hello"] :title "Hi")
           [:h1 {:title "Hi"} "Hello"]))))
