(ns replicant.asserts-test
  (:require [clojure.test :refer [deftest is testing]]
            [replicant.asserts :as asserts]
            [replicant.core :as r]
            [replicant.hiccup-headers :as hiccup]
            [replicant.vdom :as vdom]))

(defn ->headers [hiccup]
  (r/get-hiccup-headers nil hiccup))

(defn ->vdom [hiccup]
  (let [headers (->headers hiccup)]
    (vdom/from-hiccup headers (hiccup/attrs headers) [] #{} 0)))

(deftest has-bad-conditional-attrs?-test
  (testing "Nodes without children can do whatever they like"
    (is (false? (asserts/has-bad-conditional-attrs?
                 (->vdom [:h1])
                 (->headers [:h1]))))

    (is (false? (asserts/has-bad-conditional-attrs?
                 (->vdom [:h1 {}])
                 (->headers [:h1]))))

    (is (false? (asserts/has-bad-conditional-attrs?
                 (->vdom [:h1])
                 (->headers [:h1 {}])))))

  (testing "No attrs => attr map, with text"
    (is (true? (asserts/has-bad-conditional-attrs?
                (->vdom [:h1 "Hello"])
                (->headers [:h1 {} "Hi"])))))

  (testing "No attr map => no attrs, with text"
    (is (true? (asserts/has-bad-conditional-attrs?
                (->vdom [:h1 {} "Hello"])
                (->headers [:h1 "Hi"])))))

  (testing "No attr map => nil, with text"
    (is (true? (asserts/has-bad-conditional-attrs?
                (->vdom [:h1 {} "Hello"])
                (->headers [:h1 nil "Hi"])))))

  (testing "Unchanged attrs, with text"
    (is (false? (asserts/has-bad-conditional-attrs?
                 (->vdom [:h1 "Hello"])
                 (->headers [:h1 "Hi"])))))

  (testing "Unchanged attr map, with text"
    (is (false? (asserts/has-bad-conditional-attrs?
                 (->vdom [:h1 {} "Hello"])
                 (->headers [:h1 {} "Hi"])))))

  (testing "No attrs => attr map, with children"
    (is (true? (asserts/has-bad-conditional-attrs?
                (->vdom [:h1 [:span "Hello"]])
                (->headers [:h1 {} [:span "Hi"]])))))

  (testing "No attr map => no attrs, with children"
    (is (true? (asserts/has-bad-conditional-attrs?
                (->vdom [:h1 {} [:span "Hello"]])
                (->headers [:h1 [:span "Hi"]])))))

  (testing "No attr map => nil, with children"
    (is (true? (asserts/has-bad-conditional-attrs?
                (->vdom [:h1 {} [:span "Hello"]])
                (->headers [:h1 nil [:span "Hi"]])))))

  (testing "Unchanged attrs, with children"
    (is (false? (asserts/has-bad-conditional-attrs?
                 (->vdom [:h1 [:span "Hello"]])
                 (->headers [:h1 [:span "Hi"]])))))

  (testing "Unchanged attr map, with children"
    (is (false? (asserts/has-bad-conditional-attrs?
                 (->vdom [:h1 {} [:span "Hello"]])
                 (->headers [:h1 {} [:span "Hi"]])))))

  (testing "No attrs => attr map, with list of children"
    (is (true? (asserts/has-bad-conditional-attrs?
                (->vdom [:h1 (list [:span "Hello"])])
                (->headers [:h1 {} (list [:span "Hi"])])))))

  (testing "No attr map => no attrs, with list of children"
    (is (true? (asserts/has-bad-conditional-attrs?
                (->vdom [:h1 {} (list [:span "Hello"])])
                (->headers [:h1 (list [:span "Hi"])])))))

  (testing "No attr map => nil, with list of children"
    (is (true? (asserts/has-bad-conditional-attrs?
                (->vdom [:h1 {} (list [:span "Hello"])])
                (->headers [:h1 nil (list [:span "Hi"])])))))

  (testing "Unchanged attrs, with list of children"
    (is (false? (asserts/has-bad-conditional-attrs?
                 (->vdom [:h1 (list [:span "Hello"])])
                 (->headers [:h1 (list [:span "Hi"])])))))

  (testing "Unchanged attr map, with list of children"
    (is (false? (asserts/has-bad-conditional-attrs?
                 (->vdom [:h1 {} (list [:span "Hello"])])
                 (->headers [:h1 {} (list [:span "Hi"])]))))))

(deftest convey-bad-conditional-attributes-test
  (testing "From nil to {}, empty attr map"
    (is (= (asserts/convey-bad-conditional-attributes
            (->vdom [:h1 "Hi"])
            (->headers [:h1 {} "Hi"]))
           (str "Replicant treats nils as hints of nodes that come and go. "
                "Wrapping the entire attribute map in a conditional such that what "
                "used to be \"Hi\" is now {} can impair how well Replicant can "
                "match up child nodes without keys, and may lead to undesirable "
                "behavior for life-cycle events and transitions.\n\n"
                "Instead of:\n"
                "[:h1 (when something? {}) ,,,]\n\n"
                "Consider:\n"
                "[:h1 {} ,,,]"))))

  (testing "From nil to {}, with attributes"
    (is (= (asserts/convey-bad-conditional-attributes
            (->vdom [:h1 "Hi"])
            (->headers [:h1 {:title "Greetings"} "Hi"]))
           (str "Replicant treats nils as hints of nodes that come and go. "
                "Wrapping the entire attribute map in a conditional such that what "
                "used to be \"Hi\" is now {:title \"Greetings\"} can impair how well Replicant can "
                "match up child nodes without keys, and may lead to undesirable "
                "behavior for life-cycle events and transitions.\n\n"
                "Instead of:\n"
                "[:h1 (when something? {:title \"Greetings\"}) ,,,]\n\n"
                "Consider:\n"
                "[:h1\n"
                "  (cond-> {}\n"
                "    something? (assoc :title \"Greetings\"))\n"
                " ,,,]"))))

  (testing "From {} to nil"
    (is (= (asserts/convey-bad-conditional-attributes
            (->vdom [:h1 {:title "Greetings"} "Hi"])
            (->headers [:h1 "Hi"]))
           (str "Replicant treats nils as hints of nodes that come and go. "
                "Wrapping the entire attribute map in a conditional such that what "
                "used to be {:title \"Greetings\"} is now \"Hi\" can impair how well Replicant can "
                "match up child nodes without keys, and may lead to undesirable "
                "behavior for life-cycle events and transitions.\n\n"
                "Instead of:\n"
                "[:h1 (when something? {:title \"Greetings\"}) ,,,]\n\n"
                "Consider:\n"
                "[:h1\n"
                "  (cond-> {}\n"
                "    something? (assoc :title \"Greetings\"))\n"
                " ,,,]"))))

  (testing "From {} to a node"
    (is (= (asserts/convey-bad-conditional-attributes
            (->vdom [:h1 {:title "Greetings"} "Hi"])
            (->headers [:h1 [:span {:background "green;"} "Hello"]]))
           (str "Replicant treats nils as hints of nodes that come and go. "
                "Wrapping the entire attribute map in a conditional such that what "
                "used to be {:title \"Greetings\"} is now [:span ,,,] can impair how well Replicant can "
                "match up child nodes without keys, and may lead to undesirable "
                "behavior for life-cycle events and transitions.\n\n"
                "Instead of:\n"
                "[:h1 (when something? {:title \"Greetings\"}) ,,,]\n\n"
                "Consider:\n"
                "[:h1\n"
                "  (cond-> {}\n"
                "    something? (assoc :title \"Greetings\"))\n"
                " ,,,]")))))

(deftest format-hiccup-part-test
  (is (= (asserts/format-hiccup-part
          {:title "Hello"})
         "{:title \"Hello\"}"))

  (is (= (asserts/format-hiccup-part
          {:title "Hello"
           :style {:color "black"}})
         "{:title \"Hello\", :style {:color \"black\"}}"))

  (is (= (asserts/format-hiccup-part
          {:title "Hello"
           :style {:color "black"}
           :on {:click {}}})
         "{:title \"Hello\", :style {:color \"black\"} ,,,}"))

  (is (= (asserts/format-hiccup-part
          {:title "Hello"
           :style {:color "black"
                   :background "white"
                   :font "Arial"}
           :on {:click {}}})
         "{:title \"Hello\", :style {:color \"black\", :background \"white\" ,,,} ,,,}"))

  (is (= (asserts/format-hiccup-part [:div "Hello"])
         "[:div \"Hello\"]"))

  (is (= (asserts/format-hiccup-part
          [:div {:style {:background "black"}}
           "Hello"
           [:div {:style {:background "green"}}
            "tudeloo"]])
         "[:div ,,,]"))

  (is (= (asserts/format-hiccup-part
          '([:div]))
         "([:div ,,,])"))

  (is (= (asserts/format-hiccup-part
          '(nil [:div] "Hello"))
         "(nil ,,,)")))
