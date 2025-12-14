(ns replicant.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [replicant.core :as sut]
            [replicant.hiccup-headers :as hiccup]
            [replicant.scenarios :as scenarios]
            [replicant.test-helper :as h]))

(deftest hiccup-test
  (testing "Normalizes hiccup structure"
    (is (= (into [] (sut/get-hiccup-headers nil [:h1 "Hello world"]))
           ["h1" nil nil nil {} ["Hello world"] nil [:h1 "Hello world"] nil nil])))

  (testing "Flattens children"
    (is (= (-> (sut/get-hiccup-headers nil [:h1 (list (list "Hello world"))])
               (sut/get-children nil)
               first
               hiccup/text)
           "Hello world")))

  (testing "Pixelizes styles"
    (is (= (->> (sut/get-hiccup-headers nil [:div {:style {:height 450}}])
                sut/get-attrs
                :style
                :height
                (sut/get-style-val :height))
           "450px")))

  (testing "Doesn't pixelize all styles"
    (is (= (->> (sut/get-hiccup-headers nil [:div {:style {:z-index 999}}])
                sut/get-attrs
                :style
                :z-index
                (sut/get-style-val :z-index))
           "999"))))

(deftest render-test
  (testing "Builds nodes"
    (is (= (-> (h/render [:h1 "Hello world"])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "h1"]
            [:create-text-node "Hello world"]
            [:append-child "Hello world" :to "h1"]
            [:append-child [:h1 "Hello world"] :to "body"]])))

  (testing "Renders top-level collection"
    (is (= (-> (h/render (list [:h1 "Hello world"] [:p "Text"]))
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "h1"]
            [:create-text-node "Hello world"]
            [:append-child "Hello world" :to "h1"]
            [:append-child [:h1 "Hello world"] :to "body"]
            [:create-element "p"]
            [:create-text-node "Text"]
            [:append-child "Text" :to "p"]
            [:append-child [:p "Text"] :to "body"]])))

  (testing "Renders top-level seq"
    (is (= (-> (h/render (seq (list [:h1 "Hello world"] [:p "Text"])))
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "h1"]
            [:create-text-node "Hello world"]
            [:append-child "Hello world" :to "h1"]
            [:append-child [:h1 "Hello world"] :to "body"]
            [:create-element "p"]
            [:create-text-node "Text"]
            [:append-child "Text" :to "p"]
            [:append-child [:p "Text"] :to "body"]])))

  (testing "Re-renders top-level collection"
    (is (= (-> (h/render (list [:h1 {:replicant/key :h1} "Hello world"]
                               [:p {:replicant/key :p} "Text"]))
               (h/render (list [:p {:replicant/key :p} "Text"]
                               [:h1 {:replicant/key :h1} "Hello world"]))
               h/get-mutation-log-events
               h/summarize)
           [[:insert-before [:p "Text"] [:h1 "Hello world"] :in "body"]])))

  (testing "Adds id from hiccup symbol"
    (is (= (->> (h/render [:h1#heading "Hello world"])
                h/get-mutation-log-events
                h/summarize
                (filter (comp #{:set-attribute} first)))
           [[:set-attribute [:h1 ""] "id" nil :to "heading"]])))

  (testing "Adds class from hiccup symbol"
    (is (= (->> (h/render [:h1.heading "Hello world"])
                h/get-mutation-log-events
                h/summarize
                (filter (comp #{:add-class} first)))
           [[:add-class [:h1 ""] "heading"]])))

  (testing "Adds class with a string"
    (is (= (->> (h/render [:h1.heading {:class "mt-2"} "Hello world"])
                h/get-mutation-log-events
                h/summarize
                (filter (comp #{:add-class} first)))
           [[:add-class [:h1 ""] "mt-2"]
            [:add-class [:h1 ""] "heading"]])))

  (testing "Adds class with a keyword"
    (is (= (->> (h/render [:h1.heading {:class :mt-2} "Hello world"])
                h/get-mutation-log-events
                h/summarize
                (filter (comp #{:add-class} first)))
           [[:add-class [:h1 ""] "mt-2"]
            [:add-class [:h1 ""] "heading"]])))

  (testing "Adds class with a symbol"
    (is (= (->> (h/render [:h1.heading {:class 'mt-2} "Hello world"])
                h/get-mutation-log-events
                h/summarize
                (filter (comp #{:add-class} first)))
           [[:add-class [:h1 ""] "mt-2"]
            [:add-class [:h1 ""] "heading"]])))

  (testing "Adds class with a collection of strings"
    (is (= (->> (h/render [:h1.heading {:class #{"mt-2"}} "Hello world"])
                h/get-mutation-log-events
                h/summarize
                (filter (comp #{:add-class} first)))
           [[:add-class [:h1 ""] "mt-2"]
            [:add-class [:h1 ""] "heading"]])))

  (testing "Adds class with a collection of keywords"
    (is (= (->> (h/render [:h1.heading {:class #{:mt-2}} "Hello world"])
                h/get-mutation-log-events
                h/summarize
                (filter (comp #{:add-class} first)))
           [[:add-class [:h1 ""] "mt-2"]
            [:add-class [:h1 ""] "heading"]])))

  (testing "Adds class with a collection of symbols"
    (is (= (->> (h/render [:h1.heading {:class '#{mt-2}} "Hello world"])
                h/get-mutation-log-events
                h/summarize
                (filter (comp #{:add-class} first)))
           [[:add-class [:h1 ""] "mt-2"]
            [:add-class [:h1 ""] "heading"]])))

  (testing "Changes attribute"
    (is (= (-> (h/render [:h1 {:lang "en"} "Hello world"])
               (h/render [:h1 {:lang "nb"} "Hello world"])
               h/get-mutation-log-events
               h/summarize)
           [[:set-attribute [:h1 "Hello world"] "lang" "en" :to "nb"]])))

  (testing "Ignores unchanged lang attribute"
    (is (= (-> (h/render [:h1 {:title "Hello" :lang "en"} "Hello world"])
               (h/render [:h1 {:title "Hello!" :lang "en"} "Hello world"])
               h/get-mutation-log-events
               h/summarize)
           [[:set-attribute [:h1 "Hello world"] "title" "Hello" :to "Hello!"]])))

  (testing "Allows keyword attribute values"
    (is (= (->> (h/render [:h1 {:lang :en} "Hello world"])
                h/get-mutation-log-events
                h/summarize
                (filter (comp #{:set-attribute} first)))
           [[:set-attribute [:h1 ""] "lang" nil :to "en"]])))

  (testing "Allows namespaced keyword attribute values"
    (is (= (->> (h/render [:input {:name :person/given-name}])
                h/get-mutation-log-events
                h/summarize
                (filter (comp #{:set-attribute} first)))
           [[:set-attribute [:input ""] "name" nil :to "person/given-name"]])))

  (testing "Ignores nil attributes"
    (is (= (-> (h/render [:h1 {:title nil} "Hello world"])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "h1"]
            [:create-text-node "Hello world"]
            [:append-child "Hello world" :to "h1"]
            [:append-child [:h1 "Hello world"] :to "body"]])))

  (testing "Sets blank string attributes"
    (is (= (-> (h/render [:option {:value ""} "Choose"])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "option"]
            [:set-attribute [:option ""] "value" nil :to ""]
            [:create-text-node "Choose"]
            [:append-child "Choose" :to "option"]
            [:append-child [:option "Choose"] :to "body"]])))

  (testing "Removes previously set attribute when value is nil"
    (is (= (-> (h/render [:h1 {:title "Hello"} "Hello world"])
               (h/render [:h1 {:title nil} "Hello world"])
               h/get-mutation-log-events
               h/summarize)
           [[:remove-attribute "title"]])))

  (testing "Does not trip on numbers as children"
    (is (= (-> (h/render [:h1 "Hello"])
               (h/render [:h1 2 "Hello"])
               h/get-mutation-log-events
               h/summarize)
           [[:create-text-node "2"]
            [:replace-child "2" "Hello"]
            [:create-text-node "Hello"]
            [:append-child "Hello" :to "h1"]])))

  (testing "Sets style"
    (is (= (->> (h/render [:h1 {:style {:color "red"}} "Hello world"])
                h/get-mutation-log-events
                h/summarize
                (filter (comp #{:set-style} first)))
           [[:set-style [:h1 ""] :color "red"]])))

  (testing "Ignores nil style"
    (is (= (->> (h/render [:h1 {:style {:color nil}} "Hello world"])
                h/get-mutation-log-events
                h/summarize
                (filter (comp #{:set-style} first)))
           [])))

  (testing "Removes previously set style when value is nil"
    (is (= (-> (h/render [:h1 {:style {:color "red"}} "Hello world"])
               (h/render [:h1 {:style {:color nil}} "Hello world"])
               h/get-mutation-log-events
               h/summarize)
           [[:remove-style [:h1 "Hello world"] :color]])))

  (testing "Replaces text node"
    (is (= (-> (h/render [:h1 {} "Hello world"])
               (h/render [:h1 {} "Hello world!"])
               h/get-mutation-log-events)
           [[:create-text-node "Hello world!"]
            [:replace-child "Hello world!" "Hello world"]])))

  (testing "Replaces element with text node"
    (is (= (-> (h/render [:h1 {} [:span "Hello world"] [:span "Opa"]])
               (h/render [:h1 {} "Hello world!" [:span "Opa"]])
               h/get-mutation-log-events
               h/summarize)
           [[:create-text-node "Hello world!"]
            [:insert-before "Hello world!" [:span "Hello world"] :in "h1"]
            [:create-text-node "Opa"]
            [:replace-child "Opa" "Hello world"]
            [:remove-child [:span "Opa"] :from "h1"]])))

  (testing "Inserts new text node"
    (is (= (-> (h/render [:h1 {} [:span "Hello world"] [:span {:replicant/key "o"} "Opa"]])
               (h/render [:h1 {} "Hello world!" [:span {:replicant/key "o"} "Opa"]])
               h/get-mutation-log-events
               h/summarize)
           [[:create-text-node "Hello world!"]
            [:insert-before "Hello world!" [:span "Hello world"] :in "h1"]
            [:remove-child [:span "Hello world"] :from "h1"]])))

  (testing "Sets innerHTML at the expense of any children"
    (is (= (-> (h/render [:h1 {:innerHTML "Whoa!"} "Hello world"])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "h1"]
            [:set-attribute [:h1 ""] "innerHTML" nil :to "Whoa!"]
            [:append-child [:h1 ""] :to "body"]])))

  (testing "Removes innerHTML from node"
    (is (= (-> (h/render [:h1 {:innerHTML "Whoa!"} "Hello world"])
               (h/render [:h1 {} "Hello world"])
               h/get-mutation-log-events
               h/summarize)
           [[:remove-attribute "innerHTML"]
            [:create-text-node "Hello world"]
            [:append-child "Hello world" :to "h1"]])))

  (testing "Does not reuse children of contenteditable elements"
    (is (= (-> (h/render [:h1 {:contenteditable true} "Hello world"])
               (h/render [:h1 {:contenteditable true} "Hello?"])
               h/get-mutation-log-events
               h/summarize)
           [[:remove-all-children :from "h1"]
            [:create-text-node "Hello?"]
            [:append-child "Hello?" :to "h1"]])))

  (testing "Does not remove children of contenteditable element that does not change"
    (is (= (-> (h/render [:h1 {:contenteditable true} "Hello world"])
               (h/render [:h1 {:contenteditable true} "Hello world"])
               h/get-mutation-log-events
               h/summarize)
           [])))

  (testing "Builds svg nodes"
    (is (= (-> (h/render [:svg {:viewBox "0 0 100 100"}
                          [:g [:use {:xlink:href "#icon"}]]])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "svg" "http://www.w3.org/2000/svg"]
            [:set-attribute [:svg ""] "viewBox" nil :to "0 0 100 100"]
            [:create-element "g" "http://www.w3.org/2000/svg"]
            [:create-element "use" "http://www.w3.org/2000/svg"]
            [:set-attribute [:use ""] "xlink:href" "http://www.w3.org/1999/xlink" nil :to "#icon"]
            [:append-child [:use ""] :to "g"]
            [:append-child [:g ""] :to "svg"]
            [:append-child [:svg ""] :to "body"]])))

  (testing "Properly adds svg to existing nodes"
    (is (= (-> (h/render [:div [:h1 "Hello"]])
               (h/render [:div
                          [:h1 "Hello"]
                          [:svg {:viewBox "0 0 100 100"}
                           [:g [:use {:xlink:href "#icon"}]]]])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "svg" "http://www.w3.org/2000/svg"]
            [:set-attribute [:svg ""] "viewBox" nil :to "0 0 100 100"]
            [:create-element "g" "http://www.w3.org/2000/svg"]
            [:create-element "use" "http://www.w3.org/2000/svg"]
            [:set-attribute [:use ""] "xlink:href" "http://www.w3.org/1999/xlink" nil :to "#icon"]
            [:append-child [:use ""] :to "g"]
            [:append-child [:g ""] :to "svg"]
            [:append-child [:svg ""] :to "div"]])))

  (testing "Properly namespaces new svg children"
    (is (= (-> (h/render [:svg {:viewBox "0 0 100 100"}
                          [:g [:use {:xlink:href "#icon"}]]])
               (h/render [:svg {:viewBox "0 0 100 100"}
                          [:g [:use {:xlink:href "#icon"}]]
                          [:g]])
               h/get-mutation-log-events
               h/summarize
               first)
           [:create-element "g" "http://www.w3.org/2000/svg"])))

  (testing "Does not force SVG namespace on foreignObject child nodes"
    (is (= (-> (h/render [:svg [:foreignObject [:div "Hello World"]]])
               h/get-mutation-log-events
               h/summarize
               (nth 2))
           [:create-element "div"])))

  (testing "Re-creates unkeyed moved nodes"
    (is (= (-> (h/render [:div
                          [:h1 {} "Title"]
                          [:p "Paragraph 1"]
                          [:ul [:li "List"]]])
               (h/render [:div
                          [:ul [:li "List"]]
                          [:h1 {} "Title"]
                          [:p "Paragraph 1"]])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "ul"]
            [:create-element "li"]
            [:create-text-node "List"]
            [:append-child "List" :to "li"]
            [:append-child [:li "List"] :to "ul"]
            [:insert-before [:ul "List"] [:h1 "Title"] :in "div"]
            [:remove-child [:ul "List"] :from "div"]])))

  (testing "Moves existing nodes when keyed"
    (is (= (-> (h/render [:div
                          [:h1 {:replicant/key "h1"} "Title"]
                          [:p {:replicant/key "p"} "Paragraph 1"]
                          [:ul {:replicant/key "ul"} [:li "List"]]])
               (h/render [:div
                          [:ul {:replicant/key "ul"} [:li "List"]]
                          [:h1 {:replicant/key "h1"} "Title"]
                          [:p {:replicant/key "p"} "Paragraph 1"]])
               h/get-mutation-log-events
               h/summarize)
           [[:insert-before [:ul "List"] [:h1 "Title"] :in "div"]])))

  (testing "Does not move initial nodes in the desired position"
    (is (= (-> (h/render [:div
                          [:h1 {:replicant/key "1"} "Item #1"]
                          [:h2 {:replicant/key "2"} "Item #2"]
                          [:h3 {:replicant/key "3"} "Item #3"]
                          [:h4 {:replicant/key "4"} "Item #4"]
                          [:h5 {:replicant/key "5"} "Item #5"]])
               (h/render [:div
                          [:h1 {:replicant/key "1"} "Item #1"]
                          [:h2 {:replicant/key "2"} "Item #2"]
                          [:h5 {:replicant/key "5"} "Item #5"]
                          [:h3 {:replicant/key "3"} "Item #3"]
                          [:h4 {:replicant/key "4"} "Item #4"]])
               h/get-mutation-log-events
               h/summarize)
           [[:insert-before [:h5 "Item #5"] [:h3 "Item #3"] :in "div"]])))

  (testing "Moves keyed nodes"
    (is (= (-> (h/render [:ul
                          [:li {:replicant/key "0"} "Item #1"]
                          [:li {:replicant/key "1"} "Item #2"]
                          [:li {:replicant/key "2"} "Item #3"]])
               (h/render [:ul
                          [:li {:replicant/key "2"} "Item #3"]
                          [:li {:replicant/key "0"} "Item #1"]
                          [:li {:replicant/key "1"} "Item #2"]])
               h/get-mutation-log-events
               h/summarize)
           [[:insert-before [:li "Item #3"] [:li "Item #1"] :in "ul"]])))

  (testing "Only moves \"disorganized\" nodes in the middle"
    (is (= (-> (h/render [:ul
                          [:li {:replicant/key "0"} "Item #1"]
                          [:li {:replicant/key "1"} "Item #2"]
                          [:li {:replicant/key "2"} "Item #3"]
                          [:li {:replicant/key "3"} "Item #4"]
                          [:li {:replicant/key "4"} "Item #5"]
                          [:li {:replicant/key "5"} "Item #6"]])
               (h/render [:ul
                          [:li {:replicant/key "0"} "Item #1"]
                          [:li {:replicant/key "1"} "Item #2"]
                          [:li {:replicant/key "3"} "Item #4"]
                          [:li {:replicant/key "4"} "Item #5"]
                          [:li {:replicant/key "2"} "Item #3"]
                          [:li {:replicant/key "5"} "Item #6"]])
               h/get-mutation-log-events
               h/summarize)
           [[:insert-before [:li "Item #3"] [:li "Item #6"] :in "ul"]])))

  (testing "Moves nodes beyond end of original children"
    (is (= (-> (h/render [:ul
                          [:li {:replicant/key "0"} "Item #1"]
                          [:li {:replicant/key "1"} "Item #2"]])
               (h/render [:ul
                          [:li {:replicant/key "0"} "Item #1"]
                          [:li {:replicant/key "2"} "Item #3"]
                          [:li {:replicant/key "1"} "Item #2"]])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "li"]
            [:create-text-node "Item #3"]
            [:append-child "Item #3" :to "li"]
            [:insert-before [:li "Item #3"] [:li "Item #2"] :in "ul"]])))

  (testing "Does not re-add child nodes that did not move"
    (is (= (-> (h/render [:ul
                          [:li {:replicant/key "0"} "Item #1"]
                          [:li {:replicant/key "1"} "Item #2"]
                          [:li {:replicant/key "2"} "Item #3"]
                          [:li {:replicant/key "3"} "Item #4"]
                          [:li {:replicant/key "4"} "Item #5"]
                          [:li {:replicant/key "5"} "Item #6"]])
               (h/render [:ul
                          [:li {:replicant/key "0"} "Item #1"] ;; Same pos
                          [:li {:replicant/key "4"} "Item #5"]
                          [:li {:replicant/key "2"} "Item #3"] ;; Same pos
                          [:li {:replicant/key "1"} "Item #2"]
                          [:li {:replicant/key "3"} "Item #4"]
                          [:li {:replicant/key "5"} "Item #6"] ;; Same pos
                          ])
               h/get-mutation-log-events
               h/summarize)
           [[:insert-before [:li "Item #5"] [:li "Item #2"] :in "ul"]
            [:insert-before [:li "Item #3"] [:li "Item #2"] :in "ul"]])))

  (testing "Swaps adjacent nodes"
    (is (= (-> (h/render [:ul
                          [:li {:replicant/key "0"} "Item #1"]
                          [:li {:replicant/key "1"} "Item #2"]
                          [:li {:replicant/key "2"} "Item #3"]
                          [:li {:replicant/key "3"} "Item #4"]
                          [:li {:replicant/key "4"} "Item #5"]])
               (h/render [:ul
                          [:li {:replicant/key "0"} "Item #1"]
                          [:li {:replicant/key "2"} "Item #3"]
                          [:li {:replicant/key "1"} "Item #2"]
                          [:li {:replicant/key "3"} "Item #4"]
                          [:li {:replicant/key "4"} "Item #5"]])
               h/get-mutation-log-events
               h/summarize)
           [[:insert-before [:li "Item #3"] [:li "Item #2"] :in "ul"]])))

  (testing "Swaps second and next-to-last nodes"
    (is (= (-> (h/render [:ul
                          [:li {:replicant/key "0"} "Item #1"]
                          [:li {:replicant/key "1"} "Item #2"]
                          [:li {:replicant/key "2"} "Item #3"]
                          [:li {:replicant/key "3"} "Item #4"]
                          [:li {:replicant/key "4"} "Item #5"]])
               (h/render [:ul
                          [:li {:replicant/key "0"} "Item #1"]
                          [:li {:replicant/key "3"} "Item #4"]
                          [:li {:replicant/key "2"} "Item #3"]
                          [:li {:replicant/key "1"} "Item #2"]
                          [:li {:replicant/key "4"} "Item #5"]])
               h/get-mutation-log-events
               h/summarize)
           [[:insert-before [:li "Item #4"] [:li "Item #2"] :in "ul"]
            [:insert-before [:li "Item #3"] [:li "Item #2"] :in "ul"]])))

  (testing "Swaps nodes and adjusts attributes"
    (is (= (-> (h/render [:ul
                          [:li {:replicant/key "0" :title "Numero uno"} "Item #1"]
                          [:li {:replicant/key "1" :title "Numero dos"} "Item #2"]])
               (h/render [:ul
                          [:li {:replicant/key "1" :title "Number two"} "Item #2"]
                          [:li {:replicant/key "0" :title "Number one"} "Item #1"]])
               h/get-mutation-log-events
               h/summarize)
           [[:insert-before [:li "Item #2"] [:li "Item #1"] :in "ul"]
            [:set-attribute [:li "Item #2"] "title" "Numero dos" :to "Number two"]
            [:set-attribute [:li "Item #1"] "title" "Numero uno" :to "Number one"]])))

  (testing "Surgically swaps nodes"
    (is (= (-> (h/render [:ul
                          [:li {:replicant/key "0"} "Item #1"]
                          [:li {:replicant/key "1"} "Item #2"]
                          [:li {:replicant/key "2"} "Item #3"]
                          [:li {:replicant/key "3"} "Item #4"]
                          [:li {:replicant/key "4"} "Item #5"]
                          [:li {:replicant/key "5"} "Item #6"]])
               (h/render [:ul
                          [:li {:replicant/key "0"} "Item #1"]
                          [:li {:replicant/key "4"} "Item #5"]
                          [:li {:replicant/key "2"} "Item #3"]
                          [:li {:replicant/key "3"} "Item #4"]
                          [:li {:replicant/key "1"} "Item #2"]
                          [:li {:replicant/key "5"} "Item #6"]])
               h/get-mutation-log-events
               h/summarize)
           [[:insert-before [:li "Item #5"] [:li "Item #2"] :in "ul"]
            [:insert-before [:li "Item #2"] [:li "Item #6"] :in "ul"]])))

  (testing "Surgically swaps nodes at the end"
    (is (= (-> (h/render [:ul
                          [:li {:replicant/key "0"} "Item #1"]
                          [:li {:replicant/key "1"} "Item #2"]
                          [:li {:replicant/key "2"} "Item #3"]])
               (h/render [:ul
                          [:li {:replicant/key "2"} "Item #3"]
                          [:li {:replicant/key "1"} "Item #2"]
                          [:li {:replicant/key "0"} "Item #1"]])
               h/get-mutation-log-events
               h/summarize)
           [[:insert-before [:li "Item #3"] [:li "Item #1"] :in "ul"]
            [:insert-before [:li "Item #2"] [:li "Item #1"] :in "ul"]])))

  (testing "Replaces text content when elements are not keyed"
    (is (= (-> (h/render [:ul
                          [:li "Item #1"]
                          [:li "Item #2"]
                          [:li "Item #3"]])
               (h/render [:ul
                          [:li "Item #1"]
                          [:li "Item #3"]
                          [:li "Item #2"]])
               h/get-mutation-log-events
               h/summarize)
           [[:create-text-node "Item #3"]
            [:replace-child "Item #3" "Item #2"]
            [:create-text-node "Item #2"]
            [:replace-child "Item #2" "Item #3"]])))

  (testing "Moves and removes nodes"
    (is (= (-> (h/render [:ul
                          [:li {:replicant/key 1} "Item #1"]
                          [:li {:replicant/key 2} "Item #2"]
                          [:li {:replicant/key 3} "Item #3"]])
               (h/render [:ul
                          [:li {:replicant/key 2} "Item #2"]
                          [:li {:replicant/key 1} "Item #1"]])
               h/get-mutation-log-events
               h/summarize)
           [[:insert-before [:li "Item #2"] [:li "Item #1"] :in "ul"]
            [:remove-child [:li "Item #3"] :from "ul"]])))

  (testing "Deletes single child node"
    (is (= (-> (h/render [:ul
                          [:li {:replicant/key 1} "Item #1"]
                          [:li {:replicant/key 2} "Item #2"]
                          [:li {:replicant/key 3} "Item #3"]])
               (h/render [:ul
                          [:li {:replicant/key 2} "Item #2"]
                          [:li {:replicant/key 3} "Item #3"]])
               h/get-mutation-log-events
               h/summarize)
           [[:remove-child [:li "Item #1"] :from "ul"]])))

  (testing "Adds node in the middle of existing nodes"
    (is (= (-> (h/render [:div
                          [:h1 {} "Title"]
                          [:p {:replicant/key :p1} "Paragraph 1"]
                          [:p {:replicant/key :p2} "Paragraph 2"]])
               (h/render [:div
                          [:h1 {} "Title"]
                          [:p {:replicant/key :p0} "Paragraph 0"]
                          [:p {:replicant/key :p1} "Paragraph 1"]
                          [:p {:replicant/key :p2} "Paragraph 2"]])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "p"]
            [:create-text-node "Paragraph 0"]
            [:append-child "Paragraph 0" :to "p"]
            [:insert-before [:p "Paragraph 0"] [:p "Paragraph 1"] :in "div"]])))

  (testing "Adds more nodes than there previously were children"
    (is (= (-> (h/render [:div
                          [:h1 {} "Title"]
                          [:p {:replicant/key :p2} "Paragraph 2"]])
               (h/render [:div
                          [:h1 {} "Title"]
                          [:p {:replicant/key :p0} "Paragraph 0"]
                          [:p {:replicant/key :p1} "Paragraph 1"]
                          [:p {:replicant/key :p2} "Paragraph 2"]])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "p"]
            [:create-text-node "Paragraph 0"]
            [:append-child "Paragraph 0" :to "p"]
            [:insert-before [:p "Paragraph 0"] [:p "Paragraph 2"] :in "div"]
            [:create-element "p"]
            [:create-text-node "Paragraph 1"]
            [:append-child "Paragraph 1" :to "p"]
            [:insert-before [:p "Paragraph 1"] [:p "Paragraph 2"] :in "div"]])))

  (testing "Adds node at the end of existing nodes"
    (is (= (-> (h/render [:div
                          [:h1 {} "Title"]
                          [:p {:replicant/key :p1} "Paragraph 1"]
                          [:p {:replicant/key :p2} "Paragraph 2"]])
               (h/render [:div
                          [:h1 {} "Title"]
                          [:p {:replicant/key :p1} "Paragraph 1"]
                          [:p {:replicant/key :p2} "Paragraph 2"]
                          [:p {:replicant/key :p0} "Paragraph 3"]])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "p"]
            [:create-text-node "Paragraph 3"]
            [:append-child "Paragraph 3" :to "p"]
            [:append-child [:p "Paragraph 3"] :to "div"]])))

  (testing "Uses \"significant nils\" to remove nodes"
    ;; Hiccup is generated by code. Things like `when` will often leave
    ;; strategic nils in the resulting hiccup. When the old vdom has a node in
    ;; the same position where the new vdom has a nil, it likely means the node
    ;; should be removed (unless it's keyed and has moved).
    (is (= (-> (h/render
                [:div
                 [:h1 "Hello"]
                 [:p "Text"]
                 [:ul [:li "Item"]]])
               (h/render
                [:div
                 [:h1 "Hello"]
                 nil
                 [:ul [:li "Item"]]])
               h/get-mutation-log-events
               h/summarize)
           [[:remove-child [:p "Text"] :from "div"]])))

  (testing "Uses \"significant nils\" to create nodes"
    ;; Hiccup is generated by code. Things like `when` will often leave
    ;; strategic nils in the resulting hiccup. When the new vdom has a node in
    ;; the same position where the old vdom has a nil, it likely means the node
    ;; should be added (unless it's keyed and has moved).
    (is (= (-> (h/render
                [:div
                 [:h1 "Hello"]
                 nil
                 [:ul [:li "Item"]]])
               (h/render
                [:div
                 [:h1 "Hello"]
                 [:p "Text"]
                 [:ul [:li "Item"]]])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "p"]
            [:create-text-node "Text"]
            [:append-child "Text" :to "p"]
            [:insert-before [:p "Text"] [:ul "Item"] :in "div"]])))

  (testing "Uses significant nils in a sea of divs"
    (is (= (-> (h/render
                [:div
                 [:div "Hello"]
                 nil
                 [:div "Footer"]])
               (h/render
                [:div
                 [:div "Hello"]
                 [:div "Text"]
                 [:div "Footer"]])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "div"]
            [:create-text-node "Text"]
            [:append-child "Text" :to "div"]
            [:insert-before [:div "Text"] [:div "Footer"] :in "div"]])))

  (testing "Moves nodes across significant nils in varying number of children"
    (is (= (-> (h/render
                [:div
                 [:div {:replicant/key "1"} "Hello"]
                 nil
                 [:div {:replicant/key "2"} "Footer"]])
               (h/render
                [:div
                 [:div {:replicant/key "2"} "Footer"]
                 [:div {:replicant/key "1"} "Hello"]])
               h/get-mutation-log-events
               h/summarize)
           [[:insert-before [:div "Footer"] [:div "Hello"] :in "div"]])))

  (testing "Moves nodes across significant nils"
    (is (= (-> (h/render
                [:div
                 [:div {:replicant/key "1"} "Hello"]
                 nil
                 [:div {:replicant/key "2"} "Footer"]])
               (h/render
                [:div
                 [:div {:replicant/key "2"} "Footer"]
                 [:div "Text"]
                 [:div {:replicant/key "1"} "Hello"]])
               h/get-mutation-log-events
               h/summarize)
           [[:insert-before [:div "Footer"] [:div "Hello"] :in "div"]
            [:create-element "div"]
            [:create-text-node "Text"]
            [:append-child "Text" :to "div"]
            [:insert-before [:div "Text"] [:div "Hello"] :in "div"]])))

  (testing "Ignores nil on nil"
    (is (= (-> (h/render
                [:div
                 [:div "Hello"]
                 nil
                 [:div "Footer"]])
               (h/render
                [:div
                 [:div "Hello"]
                 nil
                 [:div "Footer"]])
               h/get-mutation-log-events
               h/summarize)
           [])))

  (testing "Increases the number of nils"
    (is (= (-> (h/render
                [:div
                 [:div "Hello"]])
               (h/render
                [:div
                 [:div "Hello"]
                 nil
                 nil])
               h/get-mutation-log-events
               h/summarize)
           [])))

  (testing "Deals with multiple nils"
    (is (= (-> (h/render
                [:div
                 [:div "A"]
                 nil
                 nil
                 [:div "C"]])
               (h/render
                [:div
                 [:div "A"]
                 [:div "B"]
                 nil
                 [:div "C"]])
               (h/render
                [:div
                 [:div "A"]
                 nil
                 nil
                 [:div "C"]])
               h/get-mutation-log-events
               h/summarize)
           [[:remove-child [:div "B"] :from "div"]])))

  (testing "Ignores namespaced attributes"
    (is (= (-> (h/render [:div {:my.custom/data 42} "Hello"])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "div"]
            [:create-text-node "Hello"]
            [:append-child "Hello" :to "div"]
            [:append-child [:div "Hello"] :to "body"]]))))

(def f1 (fn []))
(def f2 (fn []))
(def dispatch-fn (fn [& args] args))

(deftest event-handler-test
  (testing "Creates node with event handler"
    (is (= (-> (h/render [:h1 {:on {:click f1}} "Hi!"])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "h1"]
            [:set-event-handler [:h1 ""] :click f1]
            [:create-text-node "Hi!"]
            [:append-child "Hi!" :to "h1"]
            [:append-child [:h1 "Hi!"] :to "body"]])))

  (testing "Creates node with var for event handler"
    (is (= (-> (h/render [:h1 {:on {:click #'f1}} "Hi!"])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "h1"]
            [:set-event-handler [:h1 ""] :click #'f1]
            [:create-text-node "Hi!"]
            [:append-child "Hi!" :to "h1"]
            [:append-child [:h1 "Hi!"] :to "body"]])))

  (testing "Adds event handler"
    (is (= (-> (h/render [:h1 "Hi!"])
               (h/render [:h1 {:on {:click f1}} "Hi!"])
               h/get-mutation-log-events
               h/summarize)
           [[:set-event-handler [:h1 "Hi!"] :click f1]])))

  (testing "Adds event handler with options"
    (is (= (-> (h/render [:h1 "Hi!"])
               (h/render
                [:h1 {:on
                      {:click {:replicant.event/handler f1
                               :replicant.event/capture true}}}
                 "Hi!"])
               h/get-mutation-log-events
               h/summarize)
           [[:set-event-handler [:h1 "Hi!"] :click f1 {"capture" true}]])))

    (testing "Ignores nil event handler"
    (is (= (-> (h/render [:h1 "Hi!"])
               (h/render [:h1 {:on {:click nil}} "Hi!"])
               h/get-mutation-log-events
               h/summarize)
           [])))

  (testing "Removes event handler when value is nil"
    (is (= (-> (h/render [:h1 {:on {:click f1}} "Hi!"])
               (h/render [:h1 {:on {:click nil}} "Hi!"])
               h/get-mutation-log-events
               h/summarize)
           [[:remove-event-handler [:h1 "Hi!"] :click]])))

  (testing "Removes event handler with capture option"
    (is (= (-> (h/render [:h1 {:on {:click {:replicant.event/handler f1
                                            :replicant.event/capture true}}} "Hi!"])
               (h/render [:h1 {:on {:click nil}} "Hi!"])
               h/get-mutation-log-events
               h/summarize)
           [[:remove-event-handler [:h1 "Hi!"] :click {"capture" true}]])))

  (testing "Dispatches data handler globally, with backwards compatible name for event"
    (is (= (binding [sut/*dispatch* dispatch-fn]
             (let [f (->> (h/render [:h1 {:on {:click [:do-stuff "Data"]}} "Hi!"])
                          h/get-mutation-log-events
                          (filter (comp #{:set-event-handler} first))
                          first
                          last)]
               (f {:dom :event})))
           [{:replicant/trigger :replicant.trigger/dom-event
             :replicant/dom-event {:dom :event}
             :replicant/js-event {:dom :event}
             :replicant/dispatch dispatch-fn}
            [:do-stuff "Data"]])))

  (testing "Wraps event listener in a function when updating it"
    (is (true? (binding [sut/*dispatch* (fn [& args] args)]
                 (-> (h/render [:h1 {:on {:click [:do-stuff "Data"]}} "Hi!"])
                     (h/render [:h1 {:on {:click [:do-stuff "Other data"]}} "Hi!"])
                     h/get-mutation-log-events
                     first
                     last
                     fn?)))))

  (testing "Wraps optioned event listener in a function when updating it"
    (is (true? (binding [sut/*dispatch* (fn [& args] args)]
                 (-> (h/render [:h1 {:on {:click {:replicant.event/handler [:do-stuff "Data"]}}} "Hi!"])
                     (h/render [:h1 {:on {:click {:replicant.event/handler [:do-stuff "Other data"]}}} "Hi!"])
                     h/get-mutation-log-events
                     first
                     last
                     fn?)))))

  (testing "Passes dispatch function to event handler"
    (is (= (let [calls (atom [])]
             (binding [sut/*dispatch* (fn [& args] (swap! calls conj args))]
               (let [f (->> (h/render [:h1 {:on
                                            {:click
                                             {:replicant.event/wrap-handler? true
                                              :replicant.event/handler
                                              (fn [{:replicant/keys [dom-event
                                                                     node
                                                                     dispatch]}]
                                                (dispatch dom-event node "Hello"))}}}
                                       "Hi!"])
                            h/get-mutation-log-events
                            (filter (comp #{:set-event-handler} first))
                            first
                            last)]
                 (f {:dom :event})))
             @calls)
           [[{:dom :event} nil "Hello"]])))

  (testing "Does not re-add current event handler"
    (is (= (-> (h/render [:h1 "Hi!"])
               (h/render [:h1 {:on {:click f1}} "Hi!"])
               (h/render [:h1 {:on {:click f1}} "Hi!"])
               h/get-mutation-log-events
               h/summarize)
           [])))

  (testing "Re-adds event handler when options change"
    (is (= (-> (h/render [:h1 {:on {:click {:replicant.event/handler f1
                                            :replicant.event/capture true}}} "Hi!"])
               (h/render [:h1 {:on {:click {:replicant.event/handler f1
                                            :replicant.event/capture false}}} "Hi!"])
               h/get-mutation-log-events
               h/summarize)
           [[:remove-event-handler [:h1 "Hi!"] :click {"capture" true}]
            [:set-event-handler [:h1 "Hi!"] :click f1 {"capture" false}]])))

  (testing "Does not re-add event handler when options don't change"
    (is (= (-> (h/render [:h1 {:on {:click {:replicant.event/handler f1
                                            :replicant.event/capture true}}} "Hi!"])
               (h/render [:h1 {:on {:click {:replicant.event/handler f1
                                            :replicant.event/capture true}}} "Hi!"])
               h/get-mutation-log-events
               h/summarize)
           [])))

  (testing "Changes handler"
    (is (= (-> (h/render [:h1 "Hi!"])
               (h/render [:h1 {:on {:click f1}} "Hi!"])
               (h/render [:h1 {:on {:click f2}} "Hi!"])
               h/get-mutation-log-events
               h/summarize)
           [[:set-event-handler [:h1 "Hi!"] :click f2]])))

  (testing "Accepts string event handler (but you should probably not do it)"
    (is (= (->> (h/render [:h1 {:on {:click "alert('lol')"}} "Hi!"])
                h/get-mutation-log-events
                (filter (comp #{:set-event-handler} first))
                h/summarize)
           [[:set-event-handler [:h1 ""] :click "alert('lol')"]])))

  (testing "Removes event handler"
    (is (= (-> (h/render [:h1 {:on {:click f1}} "Hi!"])
               (h/render [:h1 "Hi!"])
               h/get-mutation-log-events
               h/summarize)
           [[:remove-event-handler [:h1 "Hi!"] :click]]))))

(deftest lifecycle-test
  (testing "Triggers on-render on first mount"
    (is (= (-> (let [res (atom nil)]
                 (binding [sut/*dispatch* (fn [e data] (reset! res {:e e :data data}))]
                   (h/render [:h1 {:replicant/on-render ["Update data"]} "Hi!"])
                   @res))
               (update :e h/summarize-event))
           {:e
            {:replicant/trigger :replicant.trigger/life-cycle
             :replicant/life-cycle :replicant.life-cycle/mount
             :replicant/node {:tag-name "h1"
                              :children [{:text "Hi!"}]}}
            :data ["Update data"]})))

  (testing "Triggers on-render function on first mount"
    (is (= (-> (let [res (atom nil)]
                 (h/render [:h1 {:replicant/on-render #(reset! res %)} "Hi!"])
                 @res)
               h/summarize-event)
           {:replicant/trigger :replicant.trigger/life-cycle
            :replicant/life-cycle :replicant.life-cycle/mount
            :replicant/node {:tag-name "h1"
                             :children [{:text "Hi!"}]}})))

  (testing "Does not set on-render as attribute"
    (is (empty? (->> (h/render [:h1 {:replicant/on-render (fn [& _args])} "Hi!"])
                     h/get-mutation-log-events
                     h/summarize
                     (filter (comp #{:set-attribute} first))))))

  (testing "Does not trigger on-render when there are no updates"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (h/render [:h1 {:replicant/on-render f} "Hi!"])
                 (h/render [:h1 {:replicant/on-render f} "Hi!"]))
             (count @res))
           1)))

  (testing "Triggers on-render when adding hook"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (h/render [:h1 {} "Hi!"])
                 (h/render [:h1 {:replicant/on-render f} "Hi!"]))
             (h/summarize-events @res))
           [[:replicant.life-cycle/update [:replicant/updated-attrs] "h1"]])))

  (testing "Triggers on-render when attributes change"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (h/render [:h1 {:replicant/on-render f} "Hi!"])
                 (h/render [:h1 {:title "Heading"
                                 :replicant/on-render f} "Hi!"]))
             (h/summarize-events @res))
           [[:replicant.life-cycle/mount "h1"]
            [:replicant.life-cycle/update [:replicant/updated-attrs] "h1"]])))

  (testing "Triggers on-render when unmounting element"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (h/render [:h1 {:title "Heading"
                                 :replicant/on-render f} "Hi!"])
                 (h/render nil))
             (map :replicant/life-cycle @res))
           [:replicant.life-cycle/mount
            :replicant.life-cycle/unmount])))

  (testing "Does not trigger on-render when removing hook"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (h/render [:h1 {:replicant/on-render f} "Hi!"])
                 (h/render [:h1 {} "Hi!"]))
             (map :replicant/life-cycle @res))
           [:replicant.life-cycle/mount])))

  (testing "Triggers on-render on mounting child"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (h/render [:div [:h1 "Hi!"]])
                 (h/render [:div
                            [:h1 "Hi!"]
                            [:p {:replicant/on-render f} "New paragraph!"]]))
             (map :replicant/life-cycle @res))
           [:replicant.life-cycle/mount])))

  (testing "Triggers on-render on mounting child and parent"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (h/render [:div [:h1 "Hi!"]])
                 (h/render [:div {:replicant/on-render f}
                            [:h1 "Hi!"]
                            [:p {:replicant/on-render f} "New paragraph!"]]))
             (h/summarize-events @res))
           [[:replicant.life-cycle/mount "p"]
            [:replicant.life-cycle/update [:replicant/updated-attrs
                                           :replicant/updated-children] "div"]])))

  (testing "Triggers on-render on updating child and parent"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (h/render [:div {:replicant/on-render f} [:h1 "Hi!"]])
                 (h/render [:div {:lang "en"
                                  :replicant/on-render f} [:h1 "Hi!"]])
                 (h/render [:div {:lang "en"
                                  :replicant/on-render f}
                            [:h1 "Hi!"]
                            [:p {:replicant/on-render f} "New paragraph!"]]))
             (h/summarize-events @res))
           [[:replicant.life-cycle/mount "div"]
            [:replicant.life-cycle/update [:replicant/updated-attrs] "div"]
            [:replicant.life-cycle/mount "p"]
            [:replicant.life-cycle/update [:replicant/updated-children] "div"]])))

  (testing "Triggers on-render on co-mounting child"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (h/render [:div [:h1 {:replicant/on-render f} "One"]])
             (h/summarize-events @res))
           [[:replicant.life-cycle/mount "h1"]])))

  (testing "Triggers on-render on moving children"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (h/render [:div
                            [:h1 {:replicant/on-render f} "One"]
                            [:p.p1 {:replicant/key :p1 :replicant/on-render f} "Two"]
                            [:p.p2 {:replicant/key :p2 :replicant/on-render f} "Three"]
                            [:p.p3 {:replicant/key :p3 :replicant/on-render f} "Four"]
                            [:p.p4 {:replicant/key :p4 :replicant/on-render f} "Five"]])
                 (h/render [:div
                            [:h1 {:replicant/on-render f} "One"]
                            [:p.p2 {:replicant/key :p2 :replicant/on-render f} "Three"]
                            [:p.p3 {:replicant/key :p3 :replicant/on-render f} "Four"]
                            [:p.p1 {:replicant/key :p1 :replicant/on-render f} "Two"]
                            [:p.p4 {:replicant/key :p4 :replicant/on-render f} "Five"]]))
             (h/summarize-events @res))
           [[:replicant.life-cycle/mount "h1"]
            [:replicant.life-cycle/mount "p.p1"]
            [:replicant.life-cycle/mount "p.p2"]
            [:replicant.life-cycle/mount "p.p3"]
            [:replicant.life-cycle/mount "p.p4"]
            [:replicant.life-cycle/update [:replicant/move-node] "p.p1"]
            [:replicant.life-cycle/update [:replicant/move-node] "p.p2"]
            [:replicant.life-cycle/update [:replicant/move-node] "p.p3"]])))

  (testing "Triggers on-render on swapping children"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (h/render [:div
                            [:h1 {:replicant/key "h1"
                                  :replicant/on-render f} "One"]
                            [:p {:replicant/key "p"
                                 :replicant/on-render f} "Two"]])
                 (h/render [:div
                            [:p {:replicant/key "p"
                                 :replicant/on-render f} "Two"]
                            [:h1 {:replicant/key "h1"
                                  :replicant/on-render f} "One"]]))
             (h/summarize-events @res))
           [[:replicant.life-cycle/mount "h1"]
            [:replicant.life-cycle/mount "p"]
            [:replicant.life-cycle/update [:replicant/move-node] "p"]
            [:replicant.life-cycle/update [:replicant/move-node] "h1"]])))

  (testing "Triggers on-render on deeply nested change"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (h/render [:div.mmm-container.mmm-section
                            [:div.mmm-media.mmm-media-at
                             [:article.mmm-vert-layout-spread
                              [:div
                               [:h1.mmm-h1 {:replicant/on-render f} "Banana"]
                               [:p.mmm-p "03.456"]]
                              [:div.mmm-vert-layout-s.mmm-mtm
                               [:h2.mmm-p.mmm-desktop "Energy in 100 g"]
                               [:h2.mmm-p.mmm-mobile.mmm-mbs "Energy"]
                               [:p.mmm-h3.mmm-mbs.mmm-desktop "455 kJ"]]]]])
                 (h/render [:div.mmm-container.mmm-section
                            [:div.mmm-media.mmm-media-at
                             [:article.mmm-vert-layout-spread
                              [:div
                               [:h1.mmm-h1 {:replicant/on-render f} "Banana!"]
                               [:p.mmm-p "03.456"]]
                              [:div.mmm-vert-layout-s.mmm-mtm
                               [:h2.mmm-p.mmm-desktop "Energy in 100 g"]
                               [:h2.mmm-p.mmm-mobile.mmm-mbs "Energy"]
                               [:p.mmm-h3.mmm-mbs.mmm-desktop "455 kJ"]]]]]))
             (h/summarize-events @res))
           [[:replicant.life-cycle/mount "h1.mmm-h1"]
            [:replicant.life-cycle/update [:replicant/updated-children] "h1.mmm-h1"]]))))

(deftest lifecycle-on-mount-test
  (testing "Triggers on-mount on first mount"
    (is (= (-> (let [res (atom nil)]
                 (binding [sut/*dispatch* (fn [e data] (reset! res {:e e :data data}))]
                   (h/render [:h1 {:replicant/on-mount ["Mount data"]} "Hi!"])
                   @res))
               (update :e h/summarize-event))
           {:e
            {:replicant/trigger :replicant.trigger/life-cycle
             :replicant/life-cycle :replicant.life-cycle/mount
             :replicant/node {:tag-name "h1"
                              :children [{:text "Hi!"}]}}
            :data ["Mount data"]})))

  (testing "Does not set on-mount as attribute"
    (is (empty? (->> (h/render [:h1 {:replicant/on-mount (fn [& _args])} "Hi!"])
                     h/get-mutation-log-events
                     h/summarize
                     (filter (comp #{:set-attribute} first))))))

  (testing "Does not trigger on-mount on later updates"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (h/render [:h1 {:replicant/on-mount f} "Hi!"])
                 (h/render [:h1 {:replicant/on-mount f} "Hello!"]))
             (count @res))
           1)))

  (testing "Does not trigger on-mount when there are no updates"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (h/render [:h1 {:replicant/on-mount f} "Hi!"])
                 (h/render [:h1 {:replicant/on-mount f} "Hi!"]))
             (count @res))
           1)))

  (testing "Does not trigger on-mount when adding hook after mount"
    (is (empty? (let [res (atom [])
                      f (fn [e] (swap! res conj e))]
                  (-> (h/render [:h1 {} "Hi!"])
                      (h/render [:h1 {:replicant/on-mount f} "Hi!"]))
                  (h/summarize-events @res)))))

  (testing "Does not trigger on-mount when unmounting element"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (h/render [:h1 {:title "Heading"
                                 :replicant/on-mount f} "Hi!"])
                 (h/render nil))
             (map :replicant/life-cycle @res))
           [:replicant.life-cycle/mount])))

  (testing "Triggers on-mount on mounting child"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (h/render [:div [:h1 "Hi!"]])
                 (h/render [:div
                            [:h1 "Hi!"]
                            [:p {:replicant/on-mount f} "New paragraph!"]]))
             (map :replicant/life-cycle @res))
           [:replicant.life-cycle/mount]))))

(deftest lifecycle-on-unmount-test
  (testing "Does not trigger on-unmount on mount"
    (is (nil? (let [res (atom nil)]
                (binding [sut/*dispatch* (fn [e data] (reset! res {:e e :data data}))]
                  (h/render [:h1 {:replicant/on-unmount ["Mount data"]} "Hi!"])
                  @res)))))

  (testing "Does not set on-unmount as attribute"
    (is (empty? (->> (h/render [:h1 {:replicant/on-unmount (fn [& _args])} "Hi!"])
                     h/get-mutation-log-events
                     h/summarize
                     (filter (comp #{:set-attribute} first))))))

  (testing "Does not trigger on-unmount on updates"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (h/render [:h1 {:replicant/on-unmount f} "Hi!"])
                 (h/render [:h1 {:replicant/on-unmount f} "Hello!"]))
             (count @res))
           0)))

  (testing "Does not trigger on-unmount when there are no updates"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (h/render [:h1 {:replicant/on-unmount f} "Hi!"])
                 (h/render [:h1 {:replicant/on-unmount f} "Hi!"]))
             (count @res))
           0)))

  (testing "Does not trigger on-unmount when adding hook"
    (is (empty? (let [res (atom [])
                      f (fn [e] (swap! res conj e))]
                  (-> (h/render [:h1 {} "Hi!"])
                      (h/render [:h1 {:replicant/on-unmount f} "Hi!"]))
                  (h/summarize-events @res)))))

  (testing "Triggers on-unmount when unmounting element"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (h/render [:h1 {:title "Heading"
                                 :replicant/on-unmount f} "Hi!"])
                 (h/render nil))
             (map :replicant/life-cycle @res))
           [:replicant.life-cycle/unmount])))

  (testing "Triggers on-unmount on unmounting child"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (h/render [:div
                            [:h1 "Hi!"]
                            [:p {:replicant/on-unmount f} "New paragraph!"]])
                 (h/render [:div [:h1 "Hi!"]]))
             (map :replicant/life-cycle @res))
           [:replicant.life-cycle/unmount]))))

(deftest lifecycle-on-update-test
  (testing "Does not trigger on-update on mount"
    (is (nil? (let [res (atom nil)]
                (binding [sut/*dispatch* (fn [e data] (reset! res {:e e :data data}))]
                  (h/render [:h1 {:replicant/on-update ["Update data"]} "Hi!"])
                  @res)))))

  (testing "Triggers on-update on second render"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (h/render [:p {:replicant/on-update f} "New paragraph!"])
                 (h/render [:p {:replicant/on-update f} "New text!"]))
             (map :replicant/life-cycle @res))
           [:replicant.life-cycle/update])))

  (testing "Does not set on-update as attribute"
    (is (empty? (->> (h/render [:h1 {:replicant/on-update (fn [& _args])} "Hi!"])
                     h/get-mutation-log-events
                     h/summarize
                     (filter (comp #{:set-attribute} first))))))

  (testing "Does not trigger on-update on unmount"
    (is (nil? (let [res (atom nil)]
                (binding [sut/*dispatch* (fn [e data] (reset! res {:e e :data data}))]
                  (-> (h/render [:h1 {:replicant/on-update ["Update data"]} "Hi!"])
                      (h/render nil))
                  @res)))))

  (testing "Remembers return-value from on-mount to on-update"
    (is (= (let [res (atom nil)
                 attrs {:replicant/on-mount
                        (fn [{:keys [replicant/remember]}]
                          (remember {:remember "me"}))
                        :replicant/on-update ["Update data"]}]
             (binding [sut/*dispatch* (fn [e _]
                                        (reset! res (:replicant/memory e)))]
               (-> (h/render [:h1 attrs "Hi!"])
                   (h/render [:h1 attrs "Hello!"]))
               @res))
           {:remember "me"})))

  (testing "Updates memory in update hook"
    (is (= (let [res (atom [])
                 attrs {:replicant/on-mount
                        (fn [{:keys [replicant/remember]}]
                          (remember {:number 0}))

                        :replicant/on-update
                        (fn [{:keys [replicant/remember replicant/memory]}]
                          (swap! res conj memory)
                          (remember (update memory :number inc)))}]
             (-> (h/render [:h1 attrs "Hi 1"])
                 (h/render [:h1 attrs "Hi 2"])
                 (h/render [:h1 attrs "Hi 3"]))
             @res)
           [{:number 0}
            {:number 1}]))))

(deftest mounting-test
  (testing "Applies attribute overrides while mounting"
    (is (= (-> (h/render [:h1 {:class ["mounted"]
                               :replicant/mounting {:class ["mounting"]}} "Title"])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "h1"]
            [:add-class [:h1 ""] "mounting"]
            [:create-text-node "Title"]
            [:append-child "Title" :to "h1"]
            [:append-child [:h1 "Title"] :to "body"]
            [:next-frame]
            [:remove-class [:h1 "Title"] "mounting"]
            [:add-class [:h1 "Title"] "mounted"]])))

  (testing "Combines mounting classes with hiccup symbol classes"
    (is (= (-> (h/render [:h1.heading {:replicant/mounting {:class ["mounting"]}}
                          "Title"])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "h1"]
            [:add-class [:h1 ""] "mounting"]
            [:add-class [:h1 ""] "heading"]
            [:create-text-node "Title"]
            [:append-child "Title" :to "h1"]
            [:append-child [:h1 "Title"] :to "body"]
            [:next-frame]
            [:remove-class [:h1 "Title"] "mounting"]])))

  (testing "Does not use mounting class for update"
    (is (= (-> (h/render [:h1 {:class ["mounted"]
                               :replicant/mounting {:class ["mounting"]}} "Title"])
               (h/render [:h1 {:class ["different"]
                               :replicant/mounting {:class ["mounting"]}} "Title"])
               h/get-mutation-log-events
               h/summarize)
           [[:remove-class [:h1 "Title"] "mounted"]
            [:add-class [:h1 "Title"] "different"]])))

  (testing "Merges mounting styles into styles"
    (is (= (->> (h/render [:h1 {:style {:background "red"}
                                :replicant/mounting {:style {:color "green"}}}
                           "Title"])
                h/get-mutation-log-events
                h/summarize
                (filter (comp #{:set-style :remove-style :next-frame} first)))
           [[:set-style [:h1 ""] :background "red"]
            [:set-style [:h1 ""] :color "green"]
            [:next-frame]
            [:remove-style [:h1 "Title"] :color]])))

  (testing "Triggers on-mount hook after mounting is complete"
    (is (= (let [callbacks (atom [])]
             (h/render [:h1 {:class :mounted
                             :replicant/mounting {:class :mounting}
                             :replicant/on-render
                             (fn [e]
                               (swap! callbacks conj (h/get-snapshot (:replicant/node e))))}
                        "Title"])
             @callbacks)
           [{:tag-name "h1"
             :classes #{"mounted"}
             :children [{:text "Title"}]}]))))

(deftest unmounting-test
  (testing "Applies attribute overrides and waits for transitions while unmounting"
    (is (= (-> (h/render [:div
                          [:h1 "Title"]
                          [:p {:class ["mounted"]
                               :replicant/unmounting {:class ["unmounting"]}}
                           "Text"]])
               (h/render [:div [:h1 "Title"]])
               h/get-mutation-log-events
               h/summarize)
           [[:remove-class [:p "Text"] "mounted"]
            [:add-class [:p "Text"] "unmounting"]
            [:on-transition-end [:p "Text"]]])))

  (testing "Unmounts node after transition ends"
    (is (= (-> (h/render [:div
                          [:h1 "Title"]
                          [:p {:class ["mounted"]
                               :replicant/unmounting {:class ["unmounting"]}}
                           "Text"]])
               (h/render [:div [:h1 "Title"]])
               (h/get-callback-events 0)
               h/summarize)
           [[:remove-child [:p "Text"] :from "div"]])))

  (testing "Skips over still unmounting node"
    (is (= (-> (h/render [:div
                          [:h1 "Title"]
                          [:p {:class ["mounted"]
                               :replicant/unmounting {:class ["unmounting"]}}
                           "Text"]
                          [:footer {:replicant/key "footer"} "Footer"]])
               ;; Remove the p. Because it has unmounting attrs, it will just be
               ;; scheduled for removal with on-transition-end.
               (h/render [:div
                          [:h1 "Title"]
                          [:footer {:replicant/key "footer"} "Footer"]])
               ;; Because the on-transition-end callback has not yet been
               ;; called, the p is still there, so the h2 should be placed after
               ;; it. When the p is eventually unmounted, the resulting DOM will
               ;; be as prescribed.
               (h/render [:div
                          [:h1 "Title"]
                          [:h2 "A sub heading"]
                          [:footer {:replicant/key "footer"} "Footer"]])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "h2"]
            [:create-text-node "A sub heading"]
            [:append-child "A sub heading" :to "h2"]
            [:insert-before [:h2 "A sub heading"] [:p "Text"] :in "div"]])))

  (testing "Recreates replacement in place of nil after node has fully unmounted"
    (is (= (-> (h/render [:div
                          [:div "A"]
                          [:div {:class ["mounted"]
                                 :replicant/unmounting {:class ["unmounting"]}}
                           "B"]
                          [:div "C"]])
               ;; Remove B. Because it has unmounting attrs, it will just be
               ;; scheduled for removal with on-transition-end.
               (h/render [:div
                          [:div "A"]
                          nil
                          [:div "C"]])
               ;; Fully remove unmounting node
               (h/call-callback 0)
               ;; Re-render B. It should be created and inserted between A and C
               ;; without touching existing elements.
               (h/render [:div
                          [:div "A"]
                          [:div "B"]
                          [:div "C"]])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "div"]
            [:create-text-node "B"]
            [:append-child "B" :to "div"]
            [:insert-before [:div "B"] [:div "C"] :in "div"]])))

  (testing "Applies attribute overrides while unmounting first child"
    (is (= (-> (h/render [:div
                          [:h1 {:class ["mounted"]
                                :replicant/unmounting {:class ["unmounting"]}}
                           "Title"]
                          [:p {:replicant/key "p"} "Text"]])
               (h/render [:div [:p {:replicant/key "p"} "Text"]])
               h/get-mutation-log-events
               h/summarize)
           [[:remove-class [:h1 "Title"] "mounted"]
            [:add-class [:h1 "Title"] "unmounting"]
            [:on-transition-end [:h1 "Title"]]])))

  (testing "Keeps id attribute from hiccup symbol while unmounting"
    (is (= (-> (h/render [:div
                          [:h1 "Title"]
                          [:p#a {:class ["mounted"]
                                 :replicant/unmounting {:class ["unmounting"]}}
                           "Text"]])
               (h/render [:div [:h1 "Title"]])
               h/get-mutation-log-events
               h/summarize)
           [[:remove-class [:p#a "Text"] "mounted"]
            [:add-class [:p#a "Text"] "unmounting"]
            [:on-transition-end [:p#a "Text"]]])))

  (testing "Keeps id from hiccup attribute map while unmounting"
    (is (= (-> (h/render [:div
                          [:h1 "Title"]
                          [:p {:id "a"
                               :class ["mounted"]
                               :replicant/unmounting {:class ["unmounting"]}}
                           "Text"]])
               (h/render [:div [:h1 "Title"]])
               h/get-mutation-log-events
               h/summarize)
           [[:remove-class [:p#a "Text"] "mounted"]
            [:add-class [:p#a "Text"] "unmounting"]
            [:on-transition-end [:p#a "Text"]]])))

  (testing "Unmounts asynchronously and updates nodes"
    (is (= (-> (h/render
                [:article
                 [:h1 "Watch it go!"]
                 [:div
                  {:style {:transition "width 0.25s"
                           :width 100}
                   :replicant/unmounting {:style {:width 0}}}]
                 [:p "Square!"]])
               (h/render
                [:article
                 [:h1 "Watch it go!"]
                 nil
                 [:p "It's gone!"]])
               h/get-mutation-log-events
               h/summarize)
           [[:set-style [:div ""] :width "0px"]
            [:on-transition-end [:div ""]]
            [:create-text-node "It's gone!"]
            [:replace-child "It's gone!" "Square!"]])))

  (testing "Adds node after unmounting node"
    (is (= (-> (h/render
                [:article
                 [:h1 "Watch it go!"]
                 [:div
                  {:style {:transition "width 0.25s"
                           :width 100}
                   :replicant/unmounting {:style {:width 0}}}
                  "Transitioning square"]
                 [:p {:replicant/key "p"} "Square!"]])
               (h/render
                [:article
                 [:h1 "Watch it go!"]
                 nil
                 [:p {:replicant/key "p"} "It's gone!"]])
               (h/render
                [:article
                 [:h1 "Watch it go!"]
                 nil
                 [:h2 "Hello"]
                 [:p {:replicant/key "p"} "It's gone!"]])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "h2"]
            [:create-text-node "Hello"]
            [:append-child "Hello" :to "h2"]
            ;; The div in question is the still unmounting one
            [:insert-before [:h2 "Hello"] [:p "It's gone!"] :in "article"]])))

  (testing "Unmounting node is still present"
    (is (= (-> (h/render
                [:article
                 [:h1 "Watch it go!"]
                 [:div
                  {:style {:transition "width 0.25s"
                           :width 100}
                   :replicant/unmounting {:style {:width 0}}}]
                 [:p "Square!"]])
               (h/render
                [:article
                 [:h1 "Watch it go!"]
                 nil
                 [:p "It's gone!"]])
               (h/render
                [:article
                 [:h1 "Watch it go!"]
                 nil
                 [:h2 "Hello"]
                 [:p "It's gone!"]])
               h/->dom)
           [:article
            [:h1 "Watch it go!"]
            [:div]
            [:h2 "Hello"]
            [:p "It's gone!"]])))

  (testing "Calling the callback unmounts the node"
    (is (= (-> (h/render
                [:article
                 [:h1 "Watch it go!"]
                 [:div
                  {:style {:transition "width 0.25s"
                           :width 100}
                   :replicant/unmounting {:style {:width 0}}}]
                 [:p "Square!"]])
               (h/render
                [:article
                 [:h1 "Watch it go!"]
                 nil
                 [:p "It's gone!"]])
               (h/call-callback 0)
               h/->dom)
           [:article
            [:h1 "Watch it go!"]
            [:p "It's gone!"]])))

  (testing "Ignores async unmounting node after it fully unmounts"
    (is (= (-> (h/render
                [:article
                 [:h1 "Watch it go!"]
                 [:div
                  {:style {:transition "width 0.25s"
                           :width 100}
                   :replicant/unmounting {:style {:width 0}}}]
                 [:p "Square!"]])
               (h/render
                [:article
                 [:h1 "Watch it go!"]
                 nil
                 [:p "It's gone!"]])
               (h/call-callback 0)
               (h/render
                [:article
                 [:h1 "Watch it go!"]
                 nil
                 [:h2 "Hello"]
                 [:p "It's gone!"]])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "h2"]
            [:create-text-node "Hello"]
            [:append-child "Hello" :to "h2"]
            [:insert-before [:h2 "Hello"] [:p "It's gone!"] :in "article"]])))

  (testing "Applies attribute overrides when unmounting root node"
    (is (= (-> (h/render [:p {:class ["mounted"]
                              :replicant/unmounting {:class ["unmounting"]}}
                          "Text"])
               (h/render nil)
               h/get-mutation-log-events
               h/summarize)
           [[:remove-class [:p "Text"] "mounted"]
            [:add-class [:p "Text"] "unmounting"]
            [:on-transition-end [:p "Text"]]])))

  (testing "Unmounts root node after transition ends"
    (is (= (-> (h/render [:p {:class ["mounted"]
                              :replicant/unmounting {:class ["unmounting"]}}
                          "Text"])
               (h/render nil)
               (h/get-callback-events 0)
               h/summarize)
           [[:remove-child [:p "Text"] :from "body"]])))

  (testing "Renders new root node while the previous one is unmounting"
    (is (= (-> (h/render [:p {:class ["mounted"]
                              :replicant/unmounting {:class ["unmounting"]}}
                          "Text"])
               (h/render [:h1 "Allo allo"])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "h1"]
            [:create-text-node "Allo allo"]
            [:append-child "Allo allo" :to "h1"]
            [:insert-before [:h1 "Allo allo"] [:p "Text"] :in "body"]
            [:remove-class [:p "Text"] "mounted"]
            [:add-class [:p "Text"] "unmounting"]
            [:on-transition-end [:p "Text"]]])))

  (testing "Does not trigger on-render hook while unmounting"
    (is (= (let [callbacks (atom [])]
             (-> (h/render [:p {:class ["mounted"]
                                :replicant/unmounting {:class ["unmounting"]}
                                :replicant/on-render #(swap! callbacks conj [(:replicant/life-cycle %)
                                                                             (h/get-snapshot (:replicant/node %))])}
                            "Text"])
                 (h/render nil))
             @callbacks)
           [[:replicant.life-cycle/mount {:tag-name "p"
                                          :classes #{"mounted"}
                                          :children [{:text "Text"}]}]])))

  (testing "Triggers on-render hook after unmounting is complete"
    (is (= (let [callbacks (atom [])]
             (-> (h/render [:p {:class ["mounted"]
                                :replicant/unmounting {:class ["unmounting"]}
                                :replicant/on-render #(swap! callbacks conj [(:replicant/life-cycle %)
                                                                             (h/get-snapshot (:replicant/node %))])}
                            "Text"])
                 (h/render nil)
                 (h/call-callback 0))
             @callbacks)
           [[:replicant.life-cycle/mount {:tag-name "p"
                                          :classes #{"mounted"}
                                          :children [{:text "Text"}]}]
            [:replicant.life-cycle/unmount {:tag-name "p"
                                            :classes #{"unmounting"}
                                            :children [{:text "Text"}]}]])))

  (testing "Updates memory between on-render calls"
    (is (= (let [res (atom [])
                 attrs {:replicant/on-render
                        (fn [{:keys [replicant/memory replicant/remember]}]
                          (swap! res conj memory)
                          (remember (update (or memory {:number 0}) :number inc)))}]
             (-> (h/render [:h1 attrs "Hi 1"])
                 (h/render [:h1 attrs "Hi 2"])
                 (h/render [:h1 attrs "Hi 3"]))
             @res)
           [nil {:number 1} {:number 2}])))

  (testing "Transitions element when wiping all children"
    (is (= (-> (h/render [:div
                          [:h1 "Title"]
                          [:p {:class ["mounted"]
                               :replicant/unmounting {:class ["unmounting"]}}
                           "Text"]])
               (h/render [:div])
               h/get-mutation-log-events
               h/summarize)
           [[:remove-child [:h1 "Title"] :from "div"]
            [:remove-class [:p "Text"] "mounted"]
            [:add-class [:p "Text"] "unmounting"]
            [:on-transition-end [:p "Text"]]])))

  (testing "Renders new keyed node when previous node is unmounting"
    (is (= (-> (h/render [:div
                          [:p {:class ["mounted"]
                               :replicant/unmounting {:class ["unmounting"]}
                               :replicant/key "p"}
                           "Text"]])
               (h/render [:div])
               (h/render [:div
                          [:p {:class ["mounted"]
                               :replicant/unmounting {:class ["unmounting"]}
                               :replicant/key "p"}
                           "Text"]])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "p"]
            [:add-class [:p ""] "mounted"]
            [:create-text-node "Text"]
            [:append-child "Text" :to "p"]
            [:insert-before [:p "Text"] [:p "Text"] :in "div"]])))

  (testing "Does not treat nodes with the same key but different tag as the same"
    (is (= (-> (h/render [:h1 {:replicant/key "h1"} "Title"])
               (h/render [:p {:replicant/key "h1"} "Title"])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "p"]
            [:create-text-node "Title"]
            [:append-child "Title" :to "p"]
            [:insert-before [:p "Title"] [:h1 "Title"] :in "body"]
            [:remove-child [:h1 "Title"] :from "body"]]))))

(deftest update-children-test
  (testing "Append node"
    (is (= (-> (scenarios/append-node (scenarios/vdom))
               h/get-mutation-log-events
               h/summarize
               h/remove-text-node-events)
           [[:create-element "li"]
            [:append-child [:li "#4"] :to "ul"]])))

  (testing "Append two nodes"
    (is (= (-> (scenarios/append-two-nodes (scenarios/vdom))
               h/get-mutation-log-events
               h/summarize
               h/remove-text-node-events)
           [[:create-element "li"]
            [:append-child [:li "#4"] :to "ul"]
            [:create-element "li"]
            [:append-child [:li "#5"] :to "ul"]])))

  (testing "Prepend node"
    (is (= (-> (scenarios/prepend-node (scenarios/vdom))
               h/get-mutation-log-events
               h/summarize
               h/remove-text-node-events)
           [[:create-element "li"]
            [:insert-before [:li "#4"] [:li "#1"] :in "ul"]])))

  (testing "Prepend two nodes"
    (is (= (-> (scenarios/prepend-two-nodes (scenarios/vdom))
               h/get-mutation-log-events
               h/summarize
               h/remove-text-node-events)
           [[:create-element "li"]
            [:insert-before [:li "#4"] [:li "#1"] :in "ul"]
            [:create-element "li"]
            [:insert-before [:li "#5"] [:li "#1"] :in "ul"]])))

  (testing "Insert node"
    (is (= (-> (scenarios/insert-node (scenarios/vdom))
               h/get-mutation-log-events
               h/summarize
               h/remove-text-node-events)
           [[:create-element "li"]
            [:insert-before [:li "#4"] [:li "#2"] :in "ul"]])))

  (testing "Insert two consecutive nodes"
    (is (= (-> (scenarios/insert-two-consecutive-nodes (scenarios/vdom))
               h/get-mutation-log-events
               h/summarize
               h/remove-text-node-events)
           [[:create-element "li"]
            [:insert-before [:li "#4"] [:li "#2"] :in "ul"]
            [:create-element "li"]
            [:insert-before [:li "#5"] [:li "#2"] :in "ul"]])))

  (testing "Insert two nodes"
    (is (= (-> (scenarios/insert-two-nodes (scenarios/vdom))
               h/get-mutation-log-events
               h/summarize
               h/remove-text-node-events)
           [[:create-element "li"]
            [:insert-before [:li "#4"] [:li "#2"] :in "ul"]
            [:create-element "li"]
            [:insert-before [:li "#5"] [:li "#3"] :in "ul"]])))

  (testing "Remove last node"
    (is (= (-> (scenarios/remove-last-node (scenarios/vdom))
               h/get-mutation-log-events
               h/summarize
               h/remove-text-node-events)
           [[:remove-child [:li "#3"] :from "ul"]])))

  (testing "Remove first node"
    (is (= (-> (scenarios/remove-first-node (scenarios/vdom))
               h/get-mutation-log-events
               h/summarize
               h/remove-text-node-events)
           [[:remove-child [:li "#1"] :from "ul"]])))

  (testing "Remove middle node"
    (is (= (-> (scenarios/remove-middle-node (scenarios/vdom))
               h/get-mutation-log-events
               h/summarize
               h/remove-text-node-events)
           [[:remove-child [:li "#2"] :from "ul"]])))

  (testing "Swap nodes"
    (is (= (-> (scenarios/swap-nodes (scenarios/vdom))
               h/get-mutation-log-events
               h/summarize
               h/remove-text-node-events)
           [[:insert-before [:li "#3"] [:li "#1"] :in "ul"]
            [:insert-before [:li "#2"] [:li "#1"] :in "ul"]]))))

(deftest alias-test
  (testing "Renders alias"
    (is (= (-> (h/render
                {:aliases {:custom/title (fn [_attrs [title]]
                                           [:h1.alias title])}}
                [:custom/title "Hello world"])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "h1"]
            [:add-class [:h1 ""] "alias"]
            [:create-text-node "Hello world"]
            [:append-child "Hello world" :to "h1"]
            [:append-child [:h1 "Hello world"] :to "body"]])))

  (testing "Does nothing when alias hiccup hasn't changed"
    (is (= (-> (h/render
                {:aliases {:custom/title (fn [_attrs [title]]
                                           [:h1.alias title])}}
                [:custom/title "Hello world"])
               (h/render [:custom/title "Hello world"])
               h/get-mutation-log-events
               h/summarize)
           [])))

  (testing "Does not call alias function when alias hiccup hasn't changed"
    (is (= (let [calls (atom [])]
             (-> (h/render
                  {:aliases {:custom/title (fn [attrs [title]]
                                             (println "??" attrs)
                                             (swap! calls conj {:attrs attrs :title title})
                                             [:h1.alias title])}}
                  [:custom/title "Hello world"])
                 (h/render [:custom/title "Hello world"]))
             (count @calls))
           1)))

  (testing "Updates aliased hiccup attributes"
    (is (= (-> (h/render
                {:aliases {:custom/title (fn [{:keys [color]} [title]]
                                           [:h1.alias {:style {:color color}} title])}}
                [:custom/title {:color "red"} "Hello world"])
               (h/render [:custom/title {:color "blue"} "Hello world"])
               h/get-mutation-log-events
               h/summarize)
           [[:set-style [:h1 "Hello world"] :color "blue"]])))

  (testing "Does nothing when updated alias attributes does not produce different hiccup"
    (is (= (-> (h/render
                {:aliases {:custom/title (fn [_ [title]]
                                           [:h1.alias title])}}
                [:custom/title {:color "red"} "Hello world"])
               (h/render [:custom/title {:color "blue"} "Hello world"])
               h/get-mutation-log-events
               h/summarize)
           [])))

  (testing "Does nothing when updated alias attributes does not produce different hiccup"
    (is (= (-> (h/render
                {:aliases {:custom/container (fn [_ children]
                                               [:div.container children])
                           :custom/title (fn [_ [title]]
                                           [:h1.alias title])}}
                [:custom/container {:color "red"}
                 [:custom/title {:color "red"} "Hello world"]])
               (h/render
                [:custom/container {:color "blue"}
                 [:custom/title {:color "blue"} "Hello world"]])
               h/get-mutation-log-events
               h/summarize)
           [])))

  (testing "Updates alias"
    (is (= (-> (h/render
                {:aliases {:custom/title (fn [_attrs [title]]
                                           [:h1.alias title])}}
                [:custom/title "Hello world"])
               (h/render [:custom/title "Hi!"])
               h/get-mutation-log-events
               h/summarize)
           [[:create-text-node "Hi!"]
            [:replace-child "Hi!" "Hello world"]])))

  (testing "Adds children in alias"
    (is (= (-> (h/render
                {:aliases {:custom/title (fn [{:keys [ok?]} [title]]
                                           (if ok?
                                             [:div
                                              [:h1 title]
                                              [:p "Hi!"]]
                                             [:div [:h1.alias title]]))}}
                [:custom/title "Hello world"])
               (h/render [:custom/title {:ok? true} "Hi!"])
               h/get-mutation-log-events
               h/summarize)
           [[:remove-class [:h1 "Hello world"] "alias"]
            [:create-text-node "Hi!"]
            [:replace-child "Hi!" "Hello world"]
            [:create-element "p"]
            [:create-text-node "Hi!"]
            [:append-child "Hi!" :to "p"]
            [:append-child [:p "Hi!"] :to "div"]])))

  (testing "Moves alias by key"
    (is (= (-> (h/render
                {:aliases {:custom/title (fn [_attr [title]]
                                           [:h1.alias title])}}
                [:div
                 [:custom/title {:replicant/key "custom"} "Hello world"]
                 [:p {:replicant/key "p"} "Text"]])
               (h/render
                [:div
                 [:p {:replicant/key "p"} "Text"]
                 [:custom/title {:replicant/key "custom"} "Hello world"]])
               h/get-mutation-log-events
               h/summarize)
           [[:insert-before [:p "Text"] [:h1 "Hello world"] :in "div"]])))

  (testing "Replaces alias with element"
    (is (= (-> (h/render
                {:aliases {:custom/title (fn [_attr [title]]
                                           [:h1.alias title])}}
                [:custom/title "Hello world"])
               (h/render [:p  "Text"])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "p"]
            [:create-text-node "Text"]
            [:append-child "Text" :to "p"]
            [:insert-before [:p "Text"] [:h1 "Hello world"] :in "body"]
            [:remove-child [:h1 "Hello world"] :from "body"]])))

  (testing "Renders blank node when alias is not defined"
    (is (= (-> (h/render [:custom/title "Hello world"])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "div"]
            [:set-attribute [:div ""] "data-replicant-error" nil :to "Undefined alias :custom/title"]
            [:create-text-node "Hello world"]
            [:append-child "Hello world" :to "div"]
            [:append-child [:div "Hello world"] :to "body"]])))

  (testing "Renders blank node when alias function throws"
    #?(:clj (is (= (-> (h/render
                        {:aliases {:custom/title (fn [_attr _children]
                                                   (throw (ex-info "Oh no!" {})))}}
                        [:custom/title "Hello world"])
                       h/get-mutation-log-events
                       h/summarize)
                   [[:create-element "div"]
                    [:set-attribute [:div ""] "data-replicant-error" nil :to "Alias threw exception"]
                    [:set-attribute [:div ""] "data-replicant-exception" nil :to "Oh no!"]
                    [:set-attribute [:div ""] "data-replicant-sexp" nil :to "[:custom/title \"Hello world\"]"]
                    [:append-child [:div ""] :to "body"]]))))

  (testing "Calls custom error handler when alias function throws"
    #?(:clj (is (= (-> (h/render
                        {:alias-error-hiccup [:h1 "Oops!"]
                         :aliases {:custom/title (fn [_attr _children]
                                                   (throw (ex-info "Oh no!" {:message "OMG!"})))}
                         :on-alias-exception (fn [e hiccup]
                                               [:div (str (:message (ex-data e)) " " (pr-str (first hiccup)))])}
                        [:custom/title "Hello world"])
                       h/get-mutation-log-events
                       h/summarize)
                   [[:create-element "div"]
                    [:create-text-node "OMG! :custom/title"]
                    [:append-child "OMG! :custom/title" :to "div"]
                    [:append-child [:div "OMG! :custom/title"] :to "body"]]))))

  (testing "Supports short-hand id and classes on aliases"
    (is (= (-> (h/render
                {:aliases {:custom/title (fn [attrs [title]]
                                           [:h1.alias attrs title])}}
                [:custom/title#title.bold "Hello world"])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "h1"]
            [:set-attribute [:h1 ""] "id" nil :to "title"]
            [:add-class [:h1#title ""] "bold"]
            [:add-class [:h1#title ""] "alias"]
            [:create-text-node "Hello world"]
            [:append-child "Hello world" :to "h1"]
            [:append-child [:h1#title "Hello world"] :to "body"]])))

  (testing "Does not needlessly recreate children of nested aliases"
    (is (= (-> (h/render
                {:aliases {::inner (fn [_ children]
                                     [:div children])
                           ::outer (fn [_ _]
                                     [::inner [:input {:type "text"}]])}}
                [::outer {:now "2024-12-04T12:00:00Z"}])
               (h/render [::outer {:now "2024-12-04T13:00:00Z"}])
               h/get-mutation-log-events
               h/summarize)
           [])))

  (testing "Does not needlessly recreate children of nested aliases when passing arguments"
    (is (= (-> (h/render
                {:aliases {::inner (fn [{:keys [data/now]} children]
                                     [:div now ": " children])
                           ::outer (fn [args _]
                                     [::inner args [:input {:type "text"}]])}}
                [::outer {:data/now "2024-12-04T12:00:00Z"}])
               (h/render [::outer {:data/now "2024-12-04T13:00:00Z"}])
               h/get-mutation-log-events
               h/summarize)
           [[:create-text-node "2024-12-04T13:00:00Z"]
            [:replace-child "2024-12-04T13:00:00Z" "2024-12-04T12:00:00Z"]])))

  (testing "Moves alias node"
    (is (= (-> (h/render
                {:aliases {::inner (fn [{:keys [data/now]} children]
                                     [:div now ": " children])
                           ::outer (fn [args _]
                                     [::inner args [:input {:type "text"}]])}}
                [:div
                 [::outer {:data/now "2024-12-04T13:00:00Z"
                           :replicant/key "alias"}]
                 [:div {:replicant/key "div"} "Hello"]])
               (h/render [:div
                          [:div {:replicant/key "div"} "Hello"]
                          [::outer {:data/now "2024-12-04T13:00:00Z"
                                    :replicant/key "alias"}]])
               h/get-mutation-log-events
               h/summarize)
           [[:insert-before [:div "Hello"] [:div "2024-12-04T13:00:00Z : "] :in "div"]])))

  (testing "Replaces alias with div"
    (is (= (-> (h/render
                {:aliases {::inner (fn [{:keys [data/now]} children]
                                     [:div now ": " children])
                           ::outer (fn [args _]
                                     [::inner args [:input {:type "text"}]])}}
                [::outer {:data/now "2024-12-04T13:00:00Z"}])
               (h/render [:div "Hello"])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "div"]
            [:create-text-node "Hello"]
            [:append-child "Hello" :to "div"]
            [:insert-before [:div "Hello"] [:div "2024-12-04T13:00:00Z : "] :in "body"]
            [:remove-child [:div "2024-12-04T13:00:00Z : "] :from "body"]])))

  (testing "Can update aliases multiple times in a row (and not loose track of the aliases)"
    (is (= (-> (h/render
                {:aliases {:ui/a (fn [{:keys [ui/dest replicant/alias-data]} children]
                                   [:a {:href (str (:base-url alias-data) dest)} children])
                           :ui/menu (fn [{:keys [ui/dest]} children]
                                      [:ui/a {:ui/dest dest} children])}
                 :alias-data {:base-url "https://example.com"}}
                [:ui/menu {:ui/dest "/one"} "One"])
               (h/render [:ui/menu {:ui/dest "/two"} "Two"])
               (h/render [:ui/menu {:ui/dest "/one"} "One"])
               (h/render [:ui/menu {:ui/dest "/two"} "Two"])
               h/get-mutation-log-events
               h/summarize)
           [[:set-attribute [:a "One"] "href" "/one" :to "/two"]
            [:create-text-node "Two"]
            [:replace-child "Two" "One"]])))

  (testing "Can update aliases multiple times when they have siblings"
    (is (= (-> (h/render
                {:aliases {:ui/a (fn [{:keys [ui/dest replicant/alias-data]} children]
                                   [:a {:href (str (:base-url alias-data) dest)} children])
                           :ui/menu (fn [{:keys [ui/dest]} children]
                                      [:ui/a {:ui/dest dest} children])}
                 :alias-data {:base-url "https://example.com"}}
                [:div
                 [:h1 "Title"]
                 [:ui/menu {:ui/dest "/one"} "One"]])
               (h/render [:div
                          [:h1 "Title"]
                          [:ui/menu {:ui/dest "/two"} "Two"]])
               (h/render [:div
                          [:h1 "Title"]
                          [:ui/menu {:ui/dest "/one"} "One"]])
               h/get-mutation-log-events
               h/summarize)
           [[:set-attribute [:a "Two"] "href" "/two" :to "/one"]
            [:create-text-node "One"]
            [:replace-child "One" "Two"]])))

  (testing "Can handle that an alias alters the type of its result"
    ;; In this case :ui/menu first returns an alias, and in the next call it
    ;; returns a div. In this case, the node from the previous render must be
    ;; replaced.
    (is (= (-> (h/render
                {:aliases {:ui/a (fn [{:keys [ui/dest replicant/alias-data]} children]
                                   [:a {:href (str (:base-url alias-data) dest)} children])
                           :ui/menu (fn [{:keys [location]} children]
                                      (if (get-in location [:hash-params :open])
                                        [:div.relative
                                         [:ui/a {:ui/dest "/"}
                                          "Close"]
                                         [:div.absolute
                                          "Dialog"]]
                                        [:ui/a {:ui/dest "/#open=1"}
                                         "Open"]))}
                 :alias-data {:base-url "https://example.com"}}
                [:ui/menu {:location {}}])
               (h/render
                [:ui/menu {:location {:hash-params {:open "1"}}}])
               h/->dom)
           [:div [:a "Close"] [:div "Dialog"]])))

  (testing "Produces svg nodes with an alias"
    (is (= (->> (h/render
                 {:aliases {:ui/circle
                            (fn [_ _]
                              [:circle {:cx 75 :cy 25 :r 25}])}}
                 [:svg
                  [:ui/circle]])
                h/get-mutation-log-events
                h/summarize
                (filter (comp #{:create-element} first)))
           [[:create-element "svg" "http://www.w3.org/2000/svg"]
            [:create-element "circle" "http://www.w3.org/2000/svg"]]))))

(deftest regression-tests
  (testing "Replaces element when root node key changes"
    (is (= (-> (h/render
                [:div {:replicant/key ":virtuoso.elements.brain-scenes/brain-1709305790992"}
                 [:svg {:xmlns "http://www.w3.org/2000/svg"
                        :viewBox "60 30 840 500"
                        :class nil}]])
               (h/render [:div {:replicant/key "el1"}])
               (h/render [:div {:replicant/key "el2"}])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "div"]
            [:insert-before [:div ""] [:div ""] :in "body"]
            [:remove-child [:div ""] :from "body"]])))

  (testing "Text fragments shouldn't trip Replicant into removing the wrong span"
    (is (= (-> (h/render [:div
                          "pre"
                          [:span#one [:input]]
                          [:span#two {:innerHTML "post"}]])
               (h/render [:div
                          "pre"
                          "text"
                          [:span#two {:innerHTML "post"}]])
               h/get-mutation-log-events
               h/summarize)
           [[:create-text-node "text"]
            [:insert-before "text" [:span#one ""] :in "div"]
            [:set-attribute [:span#one ""] "innerHTML" nil :to "post"]
            [:set-attribute [:span#one ""] "id" "one" :to "two"]
            [:remove-child [:span#two ""] :from "div"]])))

  (testing "Does not trip on string CSS properties"
    (is (= (-> (h/render
                [:h1 {:style {"background" "var(--bg)"
                              "--bg" "red"}} "Hello"])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "h1"]
            [:set-style [:h1 ""] "background" "var(--bg)"]
            [:set-style [:h1 ""] "--bg" "red"]
            [:create-text-node "Hello"]
            [:append-child "Hello" :to "h1"]
            [:append-child [:h1 "Hello"] :to "body"]])))

  (testing "Remembers nils after the end of previous children"
    (is (= (-> (h/render
                [:div])
               (h/render
                [:div
                 [:div "A"]
                 nil
                 [:div "C"]])
               (h/render
                [:div
                 [:div "A"]
                 [:div "B"]
                 [:div "C"]])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "div"]
            [:create-text-node "B"]
            [:append-child "B" :to "div"]
            [:insert-before [:div "B"] [:div "C"] :in "div"]])))

  (testing "Leans on nils to update the right div"
    (-> (h/render
         [:div
          [:div
           {:style {:background-color "red", :transition "background-color 0.5s"},
            :replicant/mounting {:style {:background-color "blue"}},
            :replicant/unmounting {:style {:background-color "blue"}}}
           "A"]
          nil
          [:div
           {:style {:background-color "red", :transition "background-color 0.5s"},
            :replicant/mounting {:style {:background-color "blue"}},
            :replicant/unmounting {:style {:background-color "blue"}}}
           "C"]])
        (h/render
         [:div
          [:div
           {:style {:background-color "red", :transition "background-color 0.5s"},
            :replicant/mounting {:style {:background-color "blue"}},
            :replicant/unmounting {:style {:background-color "blue"}}}
           "A"]
          [:div
           {:style {:background-color "red", :transition "background-color 0.5s"},
            :replicant/mounting {:style {:background-color "blue"}},
            :replicant/unmounting {:style {:background-color "blue"}}}
           "B"]
          [:div
           {:style {:background-color "red", :transition "background-color 0.5s"},
            :replicant/mounting {:style {:background-color "blue"}},
            :replicant/unmounting {:style {:background-color "blue"}}}
           "C"]])
        (h/render
         [:div
          [:div
           {:style {:background-color "red", :transition "background-color 0.5s"},
            :replicant/mounting {:style {:background-color "blue"}},
            :replicant/unmounting {:style {:background-color "blue"}}}
           "A"]
          [:div
           {:style {:background-color "red", :transition "background-color 0.5s"},
            :replicant/mounting {:style {:background-color "blue"}},
            :replicant/unmounting {:style {:background-color "blue"}}}
           "B"]
          [:div
           {:style {:background-color "red", :transition "background-color 0.5s"},
            :replicant/mounting {:style {:background-color "blue"}},
            :replicant/unmounting {:style {:background-color "blue"}}}
           "C"]])))

  (testing "Does not remove margins when changing from margin to margin-bottom"
    (is (= (-> (h/render [:div {:style {:margin "1rem"}} "foo"])
               (h/render [:div {:style {:margin-bottom "1rem"}} "foo"])
               h/get-mutation-log-events
               h/summarize)
           [[:remove-style [:div "foo"] :margin]
            [:set-style [:div "foo"] :margin-bottom "1rem"]])))

  (testing "Does not destroy elements because of a newly introduced nil"
    (is (= (-> (h/render [:div
                          [:style ":root {}"]
                          nil
                          [:div {:id "main-bar" :replicant/key "main-bar-view"}]])
               (h/render [:div
                          [:style ":root {}"]
                          nil
                          nil
                          [:div {:id "main-bar" :replicant/key "main-bar-view"}]])
               h/get-mutation-log-events
               h/summarize)
           [])))

  (testing "Inserts new nil and element"
    (is (= (-> (h/render [:div
                          [:style ":root {}"]
                          nil
                          [:div {:id "main-bar" :replicant/key "main-bar-view"}]])
               (h/render [:div
                          [:style ":root {}"]
                          nil
                          nil
                          [:div "Hello"]
                          [:div {:id "main-bar" :replicant/key "main-bar-view"}]])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "div"]
            [:create-text-node "Hello"]
            [:append-child "Hello" :to "div"]
            [:insert-before [:div "Hello"] [:div#main-bar ""] :in "div"]])))

  (testing "Handles nils and varying number of children"
    (is (= (-> (h/render [:div
                          [:style ":root {}"]
                          [:style "* {}"]
                          nil
                          [:div {:id "main-bar" :replicant/key "main-bar-view"}]
                          [:div {:id "map" :replicant/key "map-view"}]
                          nil
                          nil
                          nil
                          nil])
               (h/render [:div
                          [:style ":root {}"]
                          [:style "* {}"]
                          nil
                          nil
                          [:div {:id "main-bar" :replicant/key "main-bar-view"}]
                          [:div {:id "map" :replicant/key "map-view"}]
                          nil
                          nil
                          nil
                          nil
                          nil])
               h/get-mutation-log-events
               h/summarize)
           [])))

  (testing "Handles a nil being replaced with text"
    (is (= (-> (h/render [:li nil])
               (h/render [:li ""])
               h/get-mutation-log-events
               h/summarize)
           [[:create-text-node ""]
            [:append-child "" :to "li"]])))

  (testing "Does not loose track of the DOM after transitioning out an element"
    ;; This is a bit of a doozy! It recreates a bug found in the wild where
    ;; after the banner had transitioned out, Replicant had the wrong internal
    ;; representation of the parent div's children. This caused several elements
    ;; to be removed from the DOM only to be rebuilt and re-added, causing a
    ;; loss of focus in the input field. See the relevant commit for the
    ;; associated change in the reconciliation algorithm.
    (let [events (atom [])]
      (binding [sut/*dispatch* (fn [e data] (swap! events conj {:e e :data data}))]
        (-> (h/render
             [:div {:style {:position "relative"}}
              [:div#banner {:style {:top 0, :transition "top 0.25s"},
                            :replicant/mounting {:style {:top "-100px"}},
                            :replicant/unmounting {:style {:top "-100px"}}}
               [:p "An annoying banner"]
               [:button {:on {:click [[:ui/ax-dismiss-banner]]}} "Dismiss"]]
              [:div]
              [:div
               [:form {:on {:submit [[:dom/ax-prevent-default]
                                     [:db/ax-assoc :something/saved [:db/get :something/draft]]]}}
                [:input#draft {:replicant/on-mount [[:db/ax-assoc :something/draft-input-element :dom/node]]
                               :on {:input [[:db/ax-assoc :something/draft :event/target.value]]}}]]]
              [:div
               [:ul
                [:li {:replicant/key "draft"} "Draft: " nil]
                nil]]])
            (h/render
             [:div {:style {:position "relative"}}
              nil
              [:div]
              [:div
               [:form {:on {:submit [[:dom/ax-prevent-default]
                                     [:db/ax-assoc :something/saved [:db/get :something/draft]]]}}
                [:input#draft {:replicant/on-mount [[:db/ax-assoc :something/draft-input-element :dom/node]]
                               :on {:input [[:db/ax-assoc :something/draft :event/target.value]]}}]]]
              [:div
               [:ul
                [:li {:replicant/key "draft"} "Draft: " ""]
                nil]]])
            (h/call-callback 0)
            (h/render
             [:div {:style {:position "relative"}}
              nil
              [:div]
              [:div
               [:form {:on {:submit [[:dom/ax-prevent-default] [:db/ax-assoc :something/saved [:db/get :something/draft]]]}}
                [:input#draft {:replicant/on-mount [[:db/ax-assoc :something/draft-input-element :dom/node]]
                               :on {:input [[:db/ax-assoc :something/draft :event/target.value]]}}]]]
              [:div
               [:ul
                [:li {:replicant/key "draft"} "Draft: " "l"]
                nil]]])
            (h/render
             [:div {:style {:position "relative"}}
              nil
              [:div]
              [:div
               [:form {:on {:submit [[:dom/ax-prevent-default]
                                     [:db/ax-assoc :something/saved [:db/get :something/draft]]]}}
                [:input#draft {:replicant/on-mount [[:db/ax-assoc :something/draft-input-element :dom/node]]
                               :on {:input [[:db/ax-assoc :something/draft :event/target.value]]}}]]]
              [:div
               [:ul
                [:li {:replicant/key "draft"} "Draft: " "lo"]
                nil]]])
            h/get-mutation-log-events
            h/summarize))))

  ;; The following three tests are best understood by looking at the relevant
  ;; code changes from the commit that introduced them. They reproduce a bug
  ;; found in the wild.
  (testing "Updates every other node correctly, case 1"
    (is (= (-> (h/render
                [:div
                 [:div {:replicant/key "A"} "A"]
                 [:div {:replicant/key "B1"} "B1"]
                 [:div {:replicant/key "C"} "C"]
                 [:div {:replicant/key "D1"} "D1"]])
               (h/render
                [:div
                 [:div {:replicant/key "A"} "A"]
                 [:div {:replicant/key "B2"} "B2"]
                 [:div {:replicant/key "C"} "C"]
                 [:div {:replicant/key "D2"} "D2"]
                 ])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "div"]
            [:create-text-node "B2"]
            [:append-child "B2" :to "div"]
            [:insert-before [:div "B2"] [:div "B1"] :in "div"]
            [:remove-child [:div "B1"] :from "div"]
            [:create-element "div"]
            [:create-text-node "D2"]
            [:append-child "D2" :to "div"]
            [:insert-before [:div "D2"] [:div "D1"] :in "div"]
            [:remove-child [:div "D1"] :from "div"]])))

  (testing "Updates every other node correctly, case 2"
    (is (= (-> (h/render
                [:div
                 [:div {:replicant/key "A"} "A"]
                 [:div {:replicant/key "B1"} "B1"]
                 [:div {:replicant/key "C"} "C"]
                 [:div {:replicant/key "D1"} "D1"]
                 [:div {:replicant/key "E"} "E"]])
               (h/render
                [:div
                 [:div {:replicant/key "A"} "A"]
                 [:div {:replicant/key "B2"} "B2"]
                 [:div {:replicant/key "C"} "C"]
                 [:div {:replicant/key "D2"} "D2"]
                 [:div {:replicant/key "E"} "E"]])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "div"]
            [:create-text-node "B2"]
            [:append-child "B2" :to "div"]
            [:insert-before [:div "B2"] [:div "B1"] :in "div"]
            [:remove-child [:div "B1"] :from "div"]
            [:create-element "div"]
            [:create-text-node "D2"]
            [:append-child "D2" :to "div"]
            [:insert-before [:div "D2"] [:div "D1"] :in "div"]
            [:remove-child [:div "D1"] :from "div"]])))

  (testing "Updates every other node correctly, case 3"
    (is (= (-> (h/render
                [:div
                 [:div {:replicant/key "B1"} "B1"]
                 [:div {:replicant/key "C"} "C"]
                 [:div {:replicant/key "D1"} "D1"]
                 [:div {:replicant/key "E"} "E"]])
               (h/render
                [:div
                 [:div {:replicant/key "B2"} "B2"]
                 [:div {:replicant/key "C"} "C"]
                 [:div {:replicant/key "D2"} "D2"]
                 [:div {:replicant/key "E"} "E"]])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "div"]
            [:create-text-node "B2"]
            [:append-child "B2" :to "div"]
            [:insert-before [:div "B2"] [:div "B1"] :in "div"]
            [:remove-child [:div "B1"] :from "div"]
            [:create-element "div"]
            [:create-text-node "D2"]
            [:append-child "D2" :to "div"]
            [:insert-before [:div "D2"] [:div "D1"] :in "div"]
            [:remove-child [:div "D1"] :from "div"]])))

  (testing "Updates text nodes properly"
    (is (= (-> (h/render [:span {} "0" "0"])
               (h/render [:span {} "1"])
               h/get-mutation-log-events
               h/summarize)
           [[:create-text-node "1"]
            [:replace-child "1" "0"]
            [:remove-child "0" :from "span"]])))

  (testing "Does not trip over itself when style keys are strings"
    (is (= (-> (h/render [:span {:style {"background" "red"}} "Hello"])
               (h/render [:span {:style {"background" "blue"}} "Hello"])
               h/get-mutation-log-events
               h/summarize)
           [[:set-style [:span "Hello"] "background" "blue"]])))

  (testing "Can render IndexedSeq"
    (is (= (-> (let [[_ & hiccup] [[:div "1"] [:div "2"]]]
                 (h/render hiccup))
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "div"]
            [:create-text-node "2"]
            [:append-child "2" :to "div"]
            [:append-child [:div "2"] :to "body"]])))

  (testing "Sets value attribute last on range inputs"
    ;; Range inputs have a default min/max of 0/100. Setting value to a number
    ;; outside of this range will truncate the value to 100. For this reason, it
    ;; is important to set other attributes (specifically type/min/max) before
    ;; value. So we set value last.
    (is (= (->> (h/render
                 [:input {:type "range"
                          :value 150
                          :min 100
                          :max 200}])
                h/get-mutation-log-events
                h/summarize
                (filter (comp #{:set-attribute} first))
                last)
           [:set-attribute [:input ""] "value" nil :to 150]))

    (is (= (->> (h/render
                 [:input {:value 150
                          :min 100
                          :max 200
                          :type "range"}])
                h/get-mutation-log-events
                h/summarize
                (filter (comp #{:set-attribute} first))
                last)
           [:set-attribute [:input ""] "value" nil :to 150])))

  (testing "Sets default value attribute last on range inputs"
    (is (= (->> (h/render
                 [:input {:type "range"
                          :default-value 150
                          :min 100
                          :max 200}])
                h/get-mutation-log-events
                h/summarize
                (filter (comp #{:set-attribute} first))
                last)
           [:set-attribute [:input ""] "default-value" nil :to 150]))))
