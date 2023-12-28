(ns replicant.mutation-log-test
  (:require [clojure.test :refer [deftest is testing]]
            [replicant.mutation-log :as sut]
            [replicant.core :as replicant]
            [clojure.walk :as walk]
            [clojure.string :as str]))

(defn get-events [renderer]
  (->> renderer :el :log
       (remove (comp #{:get-child :get-parent-node} first))
       (walk/postwalk
        (fn [x]
          (if (:text x)
            (:text x)
            x)))))

(defn get-text-nodes [el]
  (if (string? el)
    [el]
    (mapcat get-text-nodes (:children el))))

(defn get-text [el]
  (str/join " " (get-text-nodes el)))

(defn blank? [x]
  (or (nil? x)
      (and (coll? x)
           (empty? x))))

(defn format-hiccup [el]
  (if (:tag-name el)
    (vec (remove blank? [(keyword (:tag-name el)) (get-text el)]))
    el))

(defn hiccup-tag [{:keys [tag-name classes]}]
  (str tag-name
       (when (seq classes)
         (str "." (str/join "." classes)))))

(defn summarize-events [events]
  (->> events
       (map (juxt :replicant/life-cycle
                  :replicant/details
                  (comp hiccup-tag deref :replicant/node)))
       (map #(vec (remove blank? %)))))

(defn summarize [events]
  (for [event events]
    (case (first event)
      :remove-child
      (let [[e from child] event]
        [e (format-hiccup child) :from (:tag-name from)])

      :append-child
      (let [[e to child] event]
        [e (format-hiccup child) :to (or (:tag-name to) "Document")])

      :insert-before
      (let [[e in child reference] event]
        [e (format-hiccup child) (format-hiccup reference)
         :in (or (:tag-name in) "Document")])

      :set-attribute
      (let [[e element attr value ns] event]
        (if ns
          [e (format-hiccup element) attr ns (get element attr) :to value]
          [e (format-hiccup element) attr (get element attr) :to value]))

      :set-event-handler
      (let [[e element event handler] event]
        [e (format-hiccup element) event handler])

      event)))

(defn render
  ([vdom] (assoc (sut/render nil vdom) :current vdom))
  ([{:keys [current el]} vdom]
   (assoc (sut/render (:element el) vdom current) :current vdom)))

(deftest render-test
  (testing "Builds nodes"
    (is (= (-> (render [:h1 "Hello world"])
               get-events
               summarize)
           [[:create-text-node "Hello world"]
            [:create-element "h1"]
            [:append-child "Hello world" :to "h1"]
            [:append-child [:h1 "Hello world"] :to "Document"]])))

  (testing "Changes attribute"
    (is (= (-> (render [:h1 {:lang "en"} "Hello world"])
               (render [:h1 {:lang "nb"} "Hello world"])
               get-events
               summarize)
           [[:set-attribute [:h1 "Hello world"] "lang" "en" :to "nb"]])))

  (testing "Ignores unchanged lang attribute"
    (is (= (-> (render [:h1 {:title "Hello" :lang "en"} "Hello world"])
               (render [:h1 {:title "Hello!" :lang "en"} "Hello world"])
               get-events
               summarize)
           [[:set-attribute [:h1 "Hello world"] "title" "Hello" :to "Hello!"]])))

  (testing "Ignores nil attributes"
    (is (= (-> (render [:h1 {:title nil} "Hello world"])
               get-events
               summarize)
           [[:create-text-node "Hello world"]
            [:create-element "h1"]
            [:append-child "Hello world" :to "h1"]
            [:append-child [:h1 "Hello world"] :to "Document"]])))

  (testing "Removes previously set attribute when value is nil"
    (is (= (-> (render [:h1 {:title "Hello"} "Hello world"])
               (render [:h1 {:title nil} "Hello world"])
               get-events
               summarize)
           [[:remove-attribute "title"]])))

  (testing "Does not trip on numbers as children"
    (is (= (-> (render [:h1 "Hello"])
               (render [:h1 2 "Hello"])
               get-events
               summarize)
           [[:create-text-node "2"]
            [:replace-child "2" "Hello"]
            [:create-text-node "Hello"]
            [:append-child "Hello" :to "h1"]])))

  (testing "Sets style"
    (is (= (->> (render [:h1 {:style {:color "red"}} "Hello world"])
                get-events
                summarize
                (filter (comp #{:set-style} first)))
           [[:set-style :color "red"]])))

  (testing "Ignores nil style"
    (is (= (->> (render [:h1 {:style {:color nil}} "Hello world"])
                get-events
                summarize
                (filter (comp #{:set-style} first)))
           [])))

  (testing "Removes previously set style when value is nil"
    (is (= (-> (render [:h1 {:style {:color "red"}} "Hello world"])
               (render [:h1 {:style {:color nil}} "Hello world"])
               get-events
               summarize)
           [[:remove-style :color]])))

  (testing "Replaces text node"
    (is (= (-> (render [:h1 {} "Hello world"])
               (render [:h1 {} "Hello world!"])
               get-events)
           [[:create-text-node "Hello world!"]
            [:replace-child "Hello world!" "Hello world"]])))

  (testing "Sets innerHTML at the expense of any children"
    (is (= (-> (render [:h1 {:innerHTML "Whoa!"} "Hello world"])
               get-events)
           [[:create-element "h1"]
            [:set-attribute {:tag-name "h1"} "innerHTML" "Whoa!"]
            [:append-child {} {:tag-name "h1" "innerHTML" "Whoa!"}]])))

  (testing "Removes innerHTML from node"
    (is (= (-> (render [:h1 {:innerHTML "Whoa!"} "Hello world"])
               (render [:h1 {} "Hello world"])
               get-events)
           [[:remove-attribute "innerHTML"]
            [:create-text-node "Hello world"]
            [:append-child {:tag-name "h1"} "Hello world"]])))

  (testing "Builds svg nodes"
    (is (= (-> (render [:svg {:viewBox "0 0 100 100"}
                        [:g [:use {:xlink:href "#icon"}]]])
               get-events
               summarize)
           [[:create-element "use" "http://www.w3.org/2000/svg"]
            [:set-attribute [:use ""] "xlink:href" "http://www.w3.org/1999/xlink" nil :to "#icon"]
            [:create-element "g" "http://www.w3.org/2000/svg"]
            [:append-child [:use ""] :to "g"]
            [:create-element "svg" "http://www.w3.org/2000/svg"]
            [:set-attribute [:svg ""] "viewBox" nil :to "0 0 100 100"]
            [:append-child [:g ""] :to "svg"]
            [:append-child [:svg ""] :to "Document"]])))

  (testing "Properly adds svg to existing nodes"
    (is (= (-> (render [:div [:h1 "Hello"]])
               (render [:div
                        [:h1 "Hello"]
                        [:svg {:viewBox "0 0 100 100"}
                         [:g [:use {:xlink:href "#icon"}]]]])
               get-events
               summarize
               first)
           [:create-element "use" "http://www.w3.org/2000/svg"])))

  (testing "Properly namespaces new svg children"
    (is (= (-> (render [:svg {:viewBox "0 0 100 100"}
                        [:g [:use {:xlink:href "#icon"}]]])
               (render [:svg {:viewBox "0 0 100 100"}
                        [:g [:use {:xlink:href "#icon"}]]
                        [:g]])
               get-events
               summarize
               first)
           [:create-element "g" "http://www.w3.org/2000/svg"])))

  (testing "Moves existing nodes"
    (is (= (-> (render [:div
                        [:h1 {} "Title"]
                        [:p "Paragraph 1"]
                        [:ul [:li "List"]]])
               (render [:div
                        [:ul [:li "List"]]
                        [:h1 {} "Title"]
                        [:p "Paragraph 1"]])
               get-events
               summarize)
           [[:insert-before [:ul "List"] [:h1 "Title"] :in "div"]])))

  (testing "Does not move initial nodes in the desired position"
    (is (= (-> (render [:div
                        [:h1 "Item #1"]
                        [:h2 "Item #2"]
                        [:h3 "Item #3"]
                        [:h4 "Item #4"]
                        [:h5 "Item #5"]])
               (render [:div
                        [:h1 "Item #1"]
                        [:h2 "Item #2"]
                        [:h5 "Item #5"]
                        [:h3 "Item #3"]
                        [:h4 "Item #4"]])
               get-events
               summarize)
           [[:insert-before [:h5 "Item #5"] [:h3 "Item #3"] :in "div"]])))

  (testing "Moves keyed nodes"
    (is (= (-> (render [:ul
                        [:li {:key "0"} "Item #1"]
                        [:li {:key "1"} "Item #2"]
                        [:li {:key "2"} "Item #3"]])
               (render [:ul
                        [:li {:key "2"} "Item #3"]
                        [:li {:key "0"} "Item #1"]
                        [:li {:key "1"} "Item #2"]])
               get-events
               summarize)
           [[:insert-before [:li "Item #3"] [:li "Item #1"] :in "ul"]])))

  (testing "Only moves \"disorganized\" nodes in the middle"
    (is (= (-> (render [:ul
                        [:li {:key "0"} "Item #1"]
                        [:li {:key "1"} "Item #2"]
                        [:li {:key "2"} "Item #3"]
                        [:li {:key "3"} "Item #4"]
                        [:li {:key "4"} "Item #5"]
                        [:li {:key "5"} "Item #6"]])
               (render [:ul
                        [:li {:key "0"} "Item #1"]
                        [:li {:key "1"} "Item #2"]
                        [:li {:key "3"} "Item #4"]
                        [:li {:key "4"} "Item #5"]
                        [:li {:key "2"} "Item #3"]
                        [:li {:key "5"} "Item #6"]])
               get-events
               summarize)
           [[:insert-before [:li "Item #3"] [:li "Item #6"] :in "ul"]])))

  (testing "Moves nodes beyond end of original children"
    (is (= (-> (render [:ul
                        [:li {:key "0"} "Item #1"]
                        [:li {:key "1"} "Item #2"]])
               (render [:ul
                        [:li {:key "0"} "Item #1"]
                        [:li {:key "2"} "Item #3"]
                        [:li {:key "1"} "Item #2"]])
               get-events
               summarize)
           [[:create-text-node "Item #3"]
            [:create-element "li"]
            [:append-child "Item #3" :to "li"]
            [:insert-before [:li "Item #3"] [:li "Item #2"] :in "ul"]])))

  (testing "Does not re-add child nodes that did not move"
    (is (= (-> (render [:ul
                        [:li {:key "0"} "Item #1"]
                        [:li {:key "1"} "Item #2"]
                        [:li {:key "2"} "Item #3"]
                        [:li {:key "3"} "Item #4"]
                        [:li {:key "4"} "Item #5"]
                        [:li {:key "5"} "Item #6"]])
               (render [:ul
                        [:li {:key "0"} "Item #1"] ;; Same pos
                        [:li {:key "4"} "Item #5"]
                        [:li {:key "2"} "Item #3"] ;; Same pos
                        [:li {:key "1"} "Item #2"]
                        [:li {:key "3"} "Item #4"]
                        [:li {:key "5"} "Item #6"] ;; Same pos
                        ])
               get-events
               summarize)
           [[:insert-before [:li "Item #5"] [:li "Item #2"] :in "ul"]
            [:insert-before [:li "Item #3"] [:li "Item #2"] :in "ul"]])))

  (testing "Swaps adjacent nodes"
    (is (= (-> (render [:ul
                        [:li {:key "0"} "Item #1"]
                        [:li {:key "1"} "Item #2"]
                        [:li {:key "2"} "Item #3"]
                        [:li {:key "3"} "Item #4"]
                        [:li {:key "4"} "Item #5"]])
               (render [:ul
                        [:li {:key "0"} "Item #1"]
                        [:li {:key "2"} "Item #3"]
                        [:li {:key "1"} "Item #2"]
                        [:li {:key "3"} "Item #4"]
                        [:li {:key "4"} "Item #5"]])
               get-events
               summarize)
           [[:insert-before [:li "Item #3"] [:li "Item #2"] :in "ul"]])))

  (testing "Swaps nodes and adjusts attributes"
    (is (= (-> (render [:ul
                        [:li {:key "0" :title "Numero uno"} "Item #1"]
                        [:li {:key "1" :title "Numero dos"} "Item #2"]])
               (render [:ul
                        [:li {:key "1" :title "Number two"} "Item #2"]
                        [:li {:key "0" :title "Number one"} "Item #1"]])
               get-events
               summarize)
           [[:insert-before [:li "Item #2"] [:li "Item #1"] :in "ul"]
            [:set-attribute [:li "Item #2"] "title" "Numero dos" :to "Number two"]
            [:set-attribute [:li "Item #1"] "title" "Numero uno" :to "Number one"]])))

  (testing "Surgically swaps nodes"
    (is (= (-> (render [:ul
                        [:li {:key "0"} "Item #1"]
                        [:li {:key "1"} "Item #2"]
                        [:li {:key "2"} "Item #3"]
                        [:li {:key "3"} "Item #4"]
                        [:li {:key "4"} "Item #5"]
                        [:li {:key "5"} "Item #6"]])
               (render [:ul
                        [:li {:key "0"} "Item #1"]
                        [:li {:key "4"} "Item #5"]
                        [:li {:key "2"} "Item #3"]
                        [:li {:key "3"} "Item #4"]
                        [:li {:key "1"} "Item #2"]
                        [:li {:key "5"} "Item #6"]])
               get-events
               summarize)
           [[:insert-before [:li "Item #5"] [:li "Item #2"] :in "ul"]
            [:insert-before [:li "Item #2"] [:li "Item #6"] :in "ul"]])))

  (testing "Surgically swaps nodes at the end"
    (is (= (-> (render [:ul
                        [:li {:key "0"} "Item #1"]
                        [:li {:key "1"} "Item #2"]
                        [:li {:key "2"} "Item #3"]])
               (render [:ul
                        [:li {:key "2"} "Item #3"]
                        [:li {:key "1"} "Item #2"]
                        [:li {:key "0"} "Item #1"]])
               get-events
               summarize)
           [[:insert-before [:li "Item #3"] [:li "Item #1"] :in "ul"]
            [:insert-before [:li "Item #2"] [:li "Item #1"] :in "ul"]])))

  (testing "Replaces text content when elements are not keyed"
    (is (= (-> (render [:ul
                        [:li "Item #1"]
                        [:li "Item #2"]
                        [:li "Item #3"]])
               (render [:ul
                        [:li "Item #1"]
                        [:li "Item #3"]
                        [:li "Item #2"]])
               get-events
               summarize)
           [[:create-text-node "Item #3"]
            [:replace-child "Item #3" "Item #2"]
            [:create-text-node "Item #2"]
            [:replace-child "Item #2" "Item #3"]])))

  (testing "Moves and removes nodes"
    (is (= (-> (render [:ul
                        [:li {:key 1} "Item #1"]
                        [:li {:key 2} "Item #2"]
                        [:li {:key 3} "Item #3"]])
               (render [:ul
                        [:li {:key 2} "Item #2"]
                        [:li {:key 1} "Item #1"]])
               get-events
               summarize)
           [[:insert-before [:li "Item #2"] [:li "Item #1"] :in "ul"]
            [:remove-child [:li "Item #3"] :from "ul"]])))

  (testing "Clears out child nodes"
    (is (= (-> (render [:ul
                        [:li {:key 1} "Item #1"]
                        [:li {:key 2} "Item #2"]
                        [:li {:key 3} "Item #3"]])
               (render [:ul])
               get-events
               summarize)
           [[:remove-child [:li "Item #1"] :from "ul"]
            [:remove-child [:li "Item #2"] :from "ul"]
            [:remove-child [:li "Item #3"] :from "ul"]])))

  (testing "Adds node in the middle of existing nodes"
    (is (= (-> (render [:div
                        [:h1 {} "Title"]
                        [:p {:key :p1} "Paragraph 1"]
                        [:p {:key :p2} "Paragraph 2"]])
               (render [:div
                        [:h1 {} "Title"]
                        [:p {:key :p0} "Paragraph 0"]
                        [:p {:key :p1} "Paragraph 1"]
                        [:p {:key :p2} "Paragraph 2"]])
               get-events
               summarize)
           [[:create-text-node "Paragraph 0"]
            [:create-element "p"]
            [:append-child "Paragraph 0" :to "p"]
            [:insert-before [:p "Paragraph 0"] [:p "Paragraph 1"] :in "div"]])))

  (testing "Adds more nodes than there previously were children"
    (is (= (-> (render [:div
                        [:h1 {} "Title"]
                        [:p {:key :p2} "Paragraph 2"]])
               (render [:div
                        [:h1 {} "Title"]
                        [:p {:key :p0} "Paragraph 0"]
                        [:p {:key :p1} "Paragraph 1"]
                        [:p {:key :p2} "Paragraph 2"]])
               get-events
               summarize)
           [[:create-text-node "Paragraph 0"]
            [:create-element "p"]
            [:append-child "Paragraph 0" :to "p"]
            [:insert-before [:p "Paragraph 0"] [:p "Paragraph 2"] :in "div"]
            [:create-text-node "Paragraph 1"]
            [:create-element "p"]
            [:append-child "Paragraph 1" :to "p"]
            [:insert-before [:p "Paragraph 1"] [:p "Paragraph 2"] :in "div"]])))

  (testing "Adds node at the end of existing nodes"
    (is (= (-> (render [:div
                        [:h1 {} "Title"]
                        [:p {:key :p1} "Paragraph 1"]
                        [:p {:key :p2} "Paragraph 2"]])
               (render [:div
                        [:h1 {} "Title"]
                        [:p {:key :p1} "Paragraph 1"]
                        [:p {:key :p2} "Paragraph 2"]
                        [:p {:key :p0} "Paragraph 3"]])
               get-events
               summarize)
           [[:create-text-node "Paragraph 3"]
            [:create-element "p"]
            [:append-child "Paragraph 3" :to "p"]
            [:append-child [:p "Paragraph 3"] :to "div"]]))))

(def f1 (fn []))
(def f2 (fn []))

(deftest event-handler-test
  (testing "Creates node with event handler"
    (is (= (-> (render [:h1 {:on {:click f1}} "Hi!"])
               get-events
               summarize)
           [[:create-text-node "Hi!"]
            [:create-element "h1"]
            [:set-event-handler [:h1 ""] :click f1]
            [:append-child "Hi!" :to "h1"]
            [:append-child [:h1 "Hi!"] :to "Document"]])))

  (testing "Adds event handler"
    (is (= (-> (render [:h1 "Hi!"])
               (render [:h1 {:on {:click f1}} "Hi!"])
               get-events
               summarize)
           [[:set-event-handler [:h1 "Hi!"] :click f1]])))

  (testing "Ignores nil event handler"
    (is (= (-> (render [:h1 "Hi!"])
               (render [:h1 {:on {:click nil}} "Hi!"])
               get-events
               summarize)
           [])))

  (testing "Remove event handler when value is nil"
    (is (= (-> (render [:h1 {:on {:click f1}} "Hi!"])
               (render [:h1 {:on {:click nil}} "Hi!"])
               get-events
               summarize)
           [[:remove-event-handler :click]])))

  (testing "Dispatches data handler globally"
    (is (= (binding [replicant/*dispatch* (fn [& args] args)]
             (let [f (->> (render [:h1 {:on {:click [:h1 "Data"]}} "Hi!"])
                          get-events
                          (filter (comp #{:set-event-handler} first))
                          first
                          last)]
               (f {:dom :event})))
           [{:replicant/event :replicant.event/dom-event} {:dom :event} [:h1 "Data"]])))

  (testing "Does not re-add current event handler"
    (is (= (-> (render [:h1 "Hi!"])
               (render [:h1 {:on {:click f1}} "Hi!"])
               (render [:h1 {:on {:click f1}} "Hi!"])
               get-events
               summarize)
           [])))

  (testing "Changes handler"
    (is (= (-> (render [:h1 "Hi!"])
               (render [:h1 {:on {:click f1}} "Hi!"])
               (render [:h1 {:on {:click f2}} "Hi!"])
               get-events
               summarize)
           [[:set-event-handler [:h1 "Hi!"] :click f2]])))

  (testing "Accepts string event handler (but you should probably not do it)"
    (is (= (->> (render [:h1 {:on {:click "alert('lol')"}} "Hi!"])
                get-events
                (filter (comp #{:set-event-handler} first))
                summarize)
           [[:set-event-handler [:h1 ""] :click "alert('lol')"]])))

  (testing "Removes event handler"
    (is (= (-> (render [:h1 {:on {:click f1}} "Hi!"])
               (render [:h1 "Hi!"])
               get-events
               summarize)
           [[:remove-event-handler :click]]))))

(defn get-element [n]
  (select-keys (:element n) [:tag-name :children]))

(deftest lifecycle-test
  (testing "Triggers on-update on first mount"
    (is (= (-> (let [res (atom nil)]
                 (binding [replicant/*dispatch* (fn [e data] (reset! res {:e e :data data}))]
                   (render [:h1 {:replicant/on-update ["Update data"]} "Hi!"])
                   @res))
               (update-in [:e :replicant/node] deref))
           {:e
            {:replicant/event :replicant.event/life-cycle
             :replicant/life-cycle :replicant/mount
             :replicant/node {:tag-name "h1"
                              :children [{:text "Hi!"}]}}
            :data ["Update data"]})))

  (testing "Triggers on-update function on first mount"
    (is (= (-> (let [res (atom nil)]
                 (render [:h1 {:replicant/on-update #(reset! res %)} "Hi!"])
                 @res)
               (update :replicant/node deref))
           {:replicant/event :replicant.event/life-cycle
            :replicant/life-cycle :replicant/mount
            :replicant/node {:tag-name "h1"
                             :children [{:text "Hi!"}]}})))

  (testing "Does not set on-update as attribute"
    (is (empty? (->> (render [:h1 {:replicant/on-update (fn [& _args])} "Hi!"])
                     get-events
                     summarize
                     (filter (comp #{:set-attribute} first))))))

  (testing "Does not trigger on-update when there are no updates"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (render [:h1 {:replicant/on-update f} "Hi!"])
                 (render [:h1 {:replicant/on-update f} "Hi!"]))
             (count @res))
           1)))

  (testing "Triggers on-update when adding hook"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (render [:h1 {} "Hi!"])
                 (render [:h1 {:replicant/on-update f} "Hi!"]))
             (summarize-events @res))
           [[:replicant/update [:replicant/updated-attrs] "h1"]])))

  (testing "Triggers on-update when attributes change"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (render [:h1 {:replicant/on-update f} "Hi!"])
                 (render [:h1 {:title "Heading"
                               :replicant/on-update f} "Hi!"]))
             (summarize-events @res))
           [[:replicant/mount "h1"]
            [:replicant/update [:replicant/updated-attrs] "h1"]])))

  (testing "Triggers on-update when unmounting element"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (render [:h1 {:title "Heading"
                               :replicant/on-update f} "Hi!"])
                 (render nil))
             (map :replicant/life-cycle @res))
           [:replicant/mount
            :replicant/unmount])))

  (testing "Does not trigger on-update when removing hook"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (render [:h1 {:replicant/on-update f} "Hi!"])
                 (render [:h1 {} "Hi!"]))
             (map :replicant/life-cycle @res))
           [:replicant/mount])))

  (testing "Triggers on-update on mounting child"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (render [:div [:h1 "Hi!"]])
                 (render [:div
                          [:h1 "Hi!"]
                          [:p {:replicant/on-update f} "New paragraph!"]]))
             (map :replicant/life-cycle @res))
           [:replicant/mount])))

  (testing "Triggers on-update on mounting child and parent"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (render [:div [:h1 "Hi!"]])
                 (render [:div {:replicant/on-update f}
                          [:h1 "Hi!"]
                          [:p {:replicant/on-update f} "New paragraph!"]]))
             (summarize-events @res))
           [[:replicant/mount "p"]
            [:replicant/update [:replicant/updated-attrs
                                :replicant/updated-children] "div"]])))

  (testing "Triggers on-update on updating child and parent"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (render [:div {:replicant/on-update f} [:h1 "Hi!"]])
                 (render [:div {:lang "en"
                                :replicant/on-update f} [:h1 "Hi!"]])
                 (render [:div {:lang "en"
                                :replicant/on-update f}
                          [:h1 "Hi!"]
                          [:p {:replicant/on-update f} "New paragraph!"]]))
             (summarize-events @res))
           [[:replicant/mount "div"]
            [:replicant/update [:replicant/updated-attrs] "div"]
            [:replicant/mount "p"]
            [:replicant/update [:replicant/updated-children] "div"]])))

  (testing "Triggers on-update on co-mounting child"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (render [:div [:h1 {:replicant/on-update f} "One"]])
             (summarize-events @res))
           [[:replicant/mount "h1"]])))

  (testing "Triggers on-update on moving children"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (render [:div
                          [:h1 {:replicant/on-update f} "One"]
                          [:p.p1 {:key :p1 :replicant/on-update f} "Two"]
                          [:p.p2 {:key :p2 :replicant/on-update f} "Three"]
                          [:p.p3 {:key :p3 :replicant/on-update f} "Four"]
                          [:p.p4 {:key :p4 :replicant/on-update f} "Five"]])
                 (render [:div
                          [:h1 {:replicant/on-update f} "One"]
                          [:p.p2 {:key :p2 :replicant/on-update f} "Three"]
                          [:p.p3 {:key :p3 :replicant/on-update f} "Four"]
                          [:p.p1 {:key :p1 :replicant/on-update f} "Two"]
                          [:p.p4 {:key :p4 :replicant/on-update f} "Five"]]))
             (summarize-events @res))
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
             (-> (render [:div
                          [:h1 {:replicant/on-update f} "One"]
                          [:p {:replicant/on-update f} "Two"]])
                 (render [:div
                          [:p {:replicant/on-update f} "Two"]
                          [:h1 {:replicant/on-update f} "One"]]))
             (summarize-events @res))
           [[:replicant/mount "h1"]
            [:replicant/mount "p"]
            [:replicant/update [:replicant/move-node] "p"]
            [:replicant/update [:replicant/move-node] "h1"]])))

  (testing "Triggers on-update on deeply nested change"
    (is (= (let [res (atom [])
                 f (fn [e] (swap! res conj e))]
             (-> (render [:div.mmm-container.mmm-section
                          [:div.mmm-media.mmm-media-at
                           [:article.mmm-vert-layout-spread
                            [:div
                             [:h1.mmm-h1 {:replicant/on-update f} "Banana"]
                             [:p.mmm-p "03.456"]]
                            [:div.mmm-vert-layout-s.mmm-mtm
                             [:h2.mmm-p.mmm-desktop "Energy in 100 g"]
                             [:h2.mmm-p.mmm-mobile.mmm-mbs "Energy"]
                             [:p.mmm-h3.mmm-mbs.mmm-desktop "455 kJ"]]]]])
                 (render [:div.mmm-container.mmm-section
                          [:div.mmm-media.mmm-media-at
                           [:article.mmm-vert-layout-spread
                            [:div
                             [:h1.mmm-h1 {:replicant/on-update f} "Banana!"]
                             [:p.mmm-p "03.456"]]
                            [:div.mmm-vert-layout-s.mmm-mtm
                             [:h2.mmm-p.mmm-desktop "Energy in 100 g"]
                             [:h2.mmm-p.mmm-mobile.mmm-mbs "Energy"]
                             [:p.mmm-h3.mmm-mbs.mmm-desktop "455 kJ"]]]]]))
             (summarize-events @res))
           [[:replicant/mount "h1.mmm-h1"]
            [:replicant/update [:replicant/updated-children] "h1.mmm-h1"]]))))
