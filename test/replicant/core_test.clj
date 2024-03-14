(ns replicant.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [replicant.core :as sut]
            [replicant.hiccup :as hiccup]
            [replicant.scenarios :as scenarios]
            [replicant.test-helper :as h]))

(deftest hiccup-test
  (testing "Normalizes hiccup structure"
    (is (= (sut/get-hiccup-headers [:h1 "Hello world"] nil)
           ["h1" nil nil nil {} ["Hello world"] nil [:h1 "Hello world"] nil])))

  (testing "Flattens children"
    (is (= (-> (sut/get-hiccup-headers [:h1 (list (list "Hello world"))] nil)
               (sut/get-children nil)
               first
               hiccup/text)
           "Hello world")))

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

(deftest render-test
  (testing "Builds nodes"
    (is (= (-> (h/render [:h1 "Hello world"])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "h1"]
            [:create-text-node "Hello world"]
            [:append-child "Hello world" :to "h1"]
            [:append-child [:h1 "Hello world"] :to "body"]])))

  (testing "Adds id from hiccup symbol"
    (is (= (->> (h/render [:h1#heading "Hello world"])
                h/get-mutation-log-events
                h/summarize
                (filter (comp #{:set-attribute} first)))
           [[:set-attribute [:h1 ""] "id" nil :to "heading"]])))

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

  (testing "Ignores nil attributes"
    (is (= (-> (h/render [:h1 {:title nil} "Hello world"])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "h1"]
            [:create-text-node "Hello world"]
            [:append-child "Hello world" :to "h1"]
            [:append-child [:h1 "Hello world"] :to "body"]])))

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
            [:insert-before [:div "Text"] [:div "Footer"] :in "div"]]))))

(def f1 (fn []))
(def f2 (fn []))

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

  (testing "Adds event handler"
    (is (= (-> (h/render [:h1 "Hi!"])
               (h/render [:h1 {:on {:click f1}} "Hi!"])
               h/get-mutation-log-events
               h/summarize)
           [[:set-event-handler [:h1 "Hi!"] :click f1]])))

  (testing "Ignores nil event handler"
    (is (= (-> (h/render [:h1 "Hi!"])
               (h/render [:h1 {:on {:click nil}} "Hi!"])
               h/get-mutation-log-events
               h/summarize)
           [])))

  (testing "Remove event handler when value is nil"
    (is (= (-> (h/render [:h1 {:on {:click f1}} "Hi!"])
               (h/render [:h1 {:on {:click nil}} "Hi!"])
               h/get-mutation-log-events
               h/summarize)
           [[:remove-event-handler [:h1 "Hi!"] :click]])))

  (testing "Dispatches data handler globally"
    (is (= (binding [sut/*dispatch* (fn [& args] args)]
             (let [f (->> (h/render [:h1 {:on {:click [:h1 "Data"]}} "Hi!"])
                          h/get-mutation-log-events
                          (filter (comp #{:set-event-handler} first))
                          first
                          last)]
               (f {:dom :event})))
           [{:replicant/trigger :replicant.trigger/dom-event
             :replicant/js-event {:dom :event}} 
            [:h1 "Data"]])))

  (testing "Does not re-add current event handler"
    (is (= (-> (h/render [:h1 "Hi!"])
               (h/render [:h1 {:on {:click f1}} "Hi!"])
               (h/render [:h1 {:on {:click f1}} "Hi!"])
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
                  @res))))))

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
            [:insert-before [:h2 "Hello"] [:div "Transitioning square"] :in "article"]])))

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
            [:h2 "Hello"]
            [:div]
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
            [:append-child [:p "Text"] :to "div"]])))

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
            [:append-child [:h1 "Hello"] :to "body"]]))))
