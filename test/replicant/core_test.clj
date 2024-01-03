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
            [:append-child [:h1 "Hello world"] :to "Document"]])))

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
            [:append-child [:h1 "Hello world"] :to "Document"]])))

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
           [[:set-style :color "red"]])))

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
           [[:remove-style :color]])))

  (testing "Replaces text node"
    (is (= (-> (h/render [:h1 {} "Hello world"])
               (h/render [:h1 {} "Hello world!"])
               h/get-mutation-log-events)
           [[:create-text-node "Hello world!"]
            [:replace-child "Hello world!" "Hello world"]])))

  (testing "Sets innerHTML at the expense of any children"
    (is (= (-> (h/render [:h1 {:innerHTML "Whoa!"} "Hello world"])
               h/get-mutation-log-events
               h/summarize)
           [[:create-element "h1"]
            [:set-attribute [:h1 ""] "innerHTML" nil :to "Whoa!"]
            [:append-child [:h1 ""] :to "Document"]])))

  (testing "Removes innerHTML from node"
    (is (= (-> (h/render [:h1 {:innerHTML "Whoa!"} "Hello world"])
               (h/render [:h1 {} "Hello world"])
               h/get-mutation-log-events
               h/summarize)
           [[:remove-attribute "innerHTML"]
            [:create-text-node "Hello world"]
            [:append-child "Hello world" :to "h1"]])))

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
            [:append-child [:svg ""] :to "Document"]])))

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

  (testing "Clears out child nodes"
    (is (= (-> (h/render [:ul
                          [:li {:replicant/key 1} "Item #1"]
                          [:li {:replicant/key 2} "Item #2"]
                          [:li {:replicant/key 3} "Item #3"]])
               (h/render [:ul])
               h/get-mutation-log-events
               h/summarize)
           [[:remove-all-children :from "ul"]])))

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
            [:append-child [:p "Paragraph 3"] :to "div"]]))))

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
            [:append-child [:h1 "Hi!"] :to "Document"]])))

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
           [[:remove-event-handler :click]])))

  (testing "Dispatches data handler globally"
    (is (= (binding [sut/*dispatch* (fn [& args] args)]
             (let [f (->> (h/render [:h1 {:on {:click [:h1 "Data"]}} "Hi!"])
                          h/get-mutation-log-events
                          (filter (comp #{:set-event-handler} first))
                          first
                          last)]
               (f {:dom :event})))
           [{:replicant/event :replicant.event/dom-event} {:dom :event} [:h1 "Data"]])))

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
           [[:remove-event-handler :click]]))))

(deftest lifecycle-test
  (testing "Triggers on-update on first mount"
    (is (= (-> (let [res (atom nil)]
                 (binding [sut/*dispatch* (fn [e data] (reset! res {:e e :data data}))]
                   (h/render [:h1 {:replicant/on-update ["Update data"]} "Hi!"])
                   @res))
               (update-in [:e :replicant/node] deref)
               (update-in [:e :replicant/node] dissoc :replicant.mutation-log/id))
           {:e
            {:replicant/event :replicant.event/life-cycle
             :replicant/life-cycle :replicant/mount
             :replicant/node {:tag-name "h1"
                              :children [{:text "Hi!"}]}}
            :data ["Update data"]})))

  (testing "Triggers on-update function on first mount"
    (is (= (-> (let [res (atom nil)]
                 (h/render [:h1 {:replicant/on-update #(reset! res %)} "Hi!"])
                 @res)
               (update :replicant/node deref)
               (update :replicant/node dissoc :replicant.mutation-log/id))
           {:replicant/event :replicant.event/life-cycle
            :replicant/life-cycle :replicant/mount
            :replicant/node {:tag-name "h1"
                             :children [{:text "Hi!"}]}})))

  (testing "Does not set on-update as attribute"
    (is (empty? (->> (h/render [:h1 {:replicant/on-update (fn [& _args])} "Hi!"])
                     h/get-mutation-log-events
                     h/summarize
                     (filter (comp #{:set-attribute} first))))))

  (testing "Does not trigger on-update when there are no updates"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (h/render [:h1 {:replicant/on-update f} "Hi!"])
                 (h/render [:h1 {:replicant/on-update f} "Hi!"]))
             (count @res))
           1)))

  (testing "Triggers on-update when adding hook"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (h/render [:h1 {} "Hi!"])
                 (h/render [:h1 {:replicant/on-update f} "Hi!"]))
             (h/summarize-events @res))
           [[:replicant/update [:replicant/updated-attrs] "h1"]])))

  (testing "Triggers on-update when attributes change"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (h/render [:h1 {:replicant/on-update f} "Hi!"])
                 (h/render [:h1 {:title "Heading"
                                 :replicant/on-update f} "Hi!"]))
             (h/summarize-events @res))
           [[:replicant/mount "h1"]
            [:replicant/update [:replicant/updated-attrs] "h1"]])))

  (testing "Triggers on-update when unmounting element"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (h/render [:h1 {:title "Heading"
                                 :replicant/on-update f} "Hi!"])
                 (h/render nil))
             (map :replicant/life-cycle @res))
           [:replicant/mount
            :replicant/unmount])))

  (testing "Does not trigger on-update when removing hook"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (h/render [:h1 {:replicant/on-update f} "Hi!"])
                 (h/render [:h1 {} "Hi!"]))
             (map :replicant/life-cycle @res))
           [:replicant/mount])))

  (testing "Triggers on-update on mounting child"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (h/render [:div [:h1 "Hi!"]])
                 (h/render [:div
                            [:h1 "Hi!"]
                            [:p {:replicant/on-update f} "New paragraph!"]]))
             (map :replicant/life-cycle @res))
           [:replicant/mount])))

  (testing "Triggers on-update on mounting child and parent"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (h/render [:div [:h1 "Hi!"]])
                 (h/render [:div {:replicant/on-update f}
                            [:h1 "Hi!"]
                            [:p {:replicant/on-update f} "New paragraph!"]]))
             (h/summarize-events @res))
           [[:replicant/mount "p"]
            [:replicant/update [:replicant/updated-attrs
                                :replicant/updated-children] "div"]])))

  (testing "Triggers on-update on updating child and parent"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (h/render [:div {:replicant/on-update f} [:h1 "Hi!"]])
                 (h/render [:div {:lang "en"
                                  :replicant/on-update f} [:h1 "Hi!"]])
                 (h/render [:div {:lang "en"
                                  :replicant/on-update f}
                            [:h1 "Hi!"]
                            [:p {:replicant/on-update f} "New paragraph!"]]))
             (h/summarize-events @res))
           [[:replicant/mount "div"]
            [:replicant/update [:replicant/updated-attrs] "div"]
            [:replicant/mount "p"]
            [:replicant/update [:replicant/updated-children] "div"]])))

  (testing "Triggers on-update on co-mounting child"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (h/render [:div [:h1 {:replicant/on-update f} "One"]])
             (h/summarize-events @res))
           [[:replicant/mount "h1"]])))

  (testing "Triggers on-update on moving children"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (h/render [:div
                            [:h1 {:replicant/on-update f} "One"]
                            [:p.p1 {:replicant/key :p1 :replicant/on-update f} "Two"]
                            [:p.p2 {:replicant/key :p2 :replicant/on-update f} "Three"]
                            [:p.p3 {:replicant/key :p3 :replicant/on-update f} "Four"]
                            [:p.p4 {:replicant/key :p4 :replicant/on-update f} "Five"]])
                 (h/render [:div
                            [:h1 {:replicant/on-update f} "One"]
                            [:p.p2 {:replicant/key :p2 :replicant/on-update f} "Three"]
                            [:p.p3 {:replicant/key :p3 :replicant/on-update f} "Four"]
                            [:p.p1 {:replicant/key :p1 :replicant/on-update f} "Two"]
                            [:p.p4 {:replicant/key :p4 :replicant/on-update f} "Five"]]))
             (h/summarize-events @res))
           [[:replicant/mount "h1"]
            [:replicant/mount "p.p1"]
            [:replicant/mount "p.p2"]
            [:replicant/mount "p.p3"]
            [:replicant/mount "p.p4"]
            [:replicant/update [:replicant/move-node] "p.p1"]
            [:replicant/update [:replicant/move-node] "p.p2"]
            [:replicant/update [:replicant/move-node] "p.p3"]])))

  (testing "Triggers on-update on swapping children"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (h/render [:div
                            [:h1 {:replicant/key "h1"
                                  :replicant/on-update f} "One"]
                            [:p {:replicant/key "p"
                                 :replicant/on-update f} "Two"]])
                 (h/render [:div
                            [:p {:replicant/key "p"
                                 :replicant/on-update f} "Two"]
                            [:h1 {:replicant/key "h1"
                                  :replicant/on-update f} "One"]]))
             (h/summarize-events @res))
           [[:replicant/mount "h1"]
            [:replicant/mount "p"]
            [:replicant/update [:replicant/move-node] "p"]
            [:replicant/update [:replicant/move-node] "h1"]])))

  (testing "Triggers on-update on deeply nested change"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (h/render [:div.mmm-container.mmm-section
                            [:div.mmm-media.mmm-media-at
                             [:article.mmm-vert-layout-spread
                              [:div
                               [:h1.mmm-h1 {:replicant/on-update f} "Banana"]
                               [:p.mmm-p "03.456"]]
                              [:div.mmm-vert-layout-s.mmm-mtm
                               [:h2.mmm-p.mmm-desktop "Energy in 100 g"]
                               [:h2.mmm-p.mmm-mobile.mmm-mbs "Energy"]
                               [:p.mmm-h3.mmm-mbs.mmm-desktop "455 kJ"]]]]])
                 (h/render [:div.mmm-container.mmm-section
                            [:div.mmm-media.mmm-media-at
                             [:article.mmm-vert-layout-spread
                              [:div
                               [:h1.mmm-h1 {:replicant/on-update f} "Banana!"]
                               [:p.mmm-p "03.456"]]
                              [:div.mmm-vert-layout-s.mmm-mtm
                               [:h2.mmm-p.mmm-desktop "Energy in 100 g"]
                               [:h2.mmm-p.mmm-mobile.mmm-mbs "Energy"]
                               [:p.mmm-h3.mmm-mbs.mmm-desktop "455 kJ"]]]]]))
             (h/summarize-events @res))
           [[:replicant/mount "h1.mmm-h1"]
            [:replicant/update [:replicant/updated-children] "h1.mmm-h1"]]))))

(deftest update-children-test
  (testing "Append node"
    (is (= (-> (scenarios/append-node scenarios/vdom)
               h/get-mutation-log-events
               h/summarize
               h/remove-text-node-events)
           [[:create-element "li"]
            [:append-child [:li "#4"] :to "ul"]])))

  (testing "Append two nodes"
    (is (= (-> (scenarios/append-two-nodes scenarios/vdom)
               h/get-mutation-log-events
               h/summarize
               h/remove-text-node-events)
           [[:create-element "li"]
            [:append-child [:li "#4"] :to "ul"]
            [:create-element "li"]
            [:append-child [:li "#5"] :to "ul"]])))

  (testing "Prepend node"
    (is (= (-> (scenarios/prepend-node scenarios/vdom)
               h/get-mutation-log-events
               h/summarize
               h/remove-text-node-events)
           [[:create-element "li"]
            [:insert-before [:li "#4"] [:li "#1"] :in "ul"]])))

  (testing "Prepend two nodes"
    (is (= (-> (scenarios/prepend-two-nodes scenarios/vdom)
               h/get-mutation-log-events
               h/summarize
               h/remove-text-node-events)
           [[:create-element "li"]
            [:insert-before [:li "#4"] [:li "#1"] :in "ul"]
            [:create-element "li"]
            [:insert-before [:li "#5"] [:li "#1"] :in "ul"]])))

  (testing "Insert node"
    (is (= (-> (scenarios/insert-node scenarios/vdom)
               h/get-mutation-log-events
               h/summarize
               h/remove-text-node-events)
           [[:create-element "li"]
            [:insert-before [:li "#4"] [:li "#2"] :in "ul"]])))

  (testing "Insert two consecutive nodes"
    (is (= (-> (scenarios/insert-two-consecutive-nodes scenarios/vdom)
               h/get-mutation-log-events
               h/summarize
               h/remove-text-node-events)
           [[:create-element "li"]
            [:insert-before [:li "#4"] [:li "#2"] :in "ul"]
            [:create-element "li"]
            [:insert-before [:li "#5"] [:li "#2"] :in "ul"]])))

  (testing "Insert two nodes"
    (is (= (-> (scenarios/insert-two-nodes scenarios/vdom)
               h/get-mutation-log-events
               h/summarize
               h/remove-text-node-events)
           [[:create-element "li"]
            [:insert-before [:li "#4"] [:li "#2"] :in "ul"]
            [:create-element "li"]
            [:insert-before [:li "#5"] [:li "#3"] :in "ul"]])))

  (testing "Remove last node"
    (is (= (-> (scenarios/remove-last-node scenarios/vdom)
               h/get-mutation-log-events
               h/summarize
               h/remove-text-node-events)
           [[:remove-child [:li "#3"] :from "ul"]])))

  (testing "Remove first node"
    (is (= (-> (scenarios/remove-first-node scenarios/vdom)
               h/get-mutation-log-events
               h/summarize
               h/remove-text-node-events)
           [[:remove-child [:li "#1"] :from "ul"]])))

  (testing "Remove middle node"
    (is (= (-> (scenarios/remove-middle-node scenarios/vdom)
               h/get-mutation-log-events
               h/summarize
               h/remove-text-node-events)
           [[:remove-child [:li "#2"] :from "ul"]])))

  (testing "Swap nodes"
    (is (= (-> (scenarios/swap-nodes scenarios/vdom)
               h/get-mutation-log-events
               h/summarize
               h/remove-text-node-events)
           [[:insert-before [:li "#3"] [:li "#1"] :in "ul"]
            [:insert-before [:li "#2"] [:li "#1"] :in "ul"]]))))
