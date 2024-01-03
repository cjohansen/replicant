(ns replicant.test-helper
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [replicant.mutation-log :as mutation-log]))

(defn get-mutation-log-events [renderer]
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

(defn format-element [el]
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
        [e (format-element child) :from (:tag-name from)])

      :remove-all-children
      (let [[e from] event]
        [e :from (:tag-name from)])

      :append-child
      (let [[e to child] event]
        [e (format-element child) :to (or (:tag-name to) "Document")])

      :insert-before
      (let [[e in child reference] event]
        [e (format-element child) (format-element reference)
         :in (or (:tag-name in) "Document")])

      :set-attribute
      (let [[e element attr value ns] event]
        (if ns
          [e (format-element element) attr ns (get element attr) :to value]
          [e (format-element element) attr (get element attr) :to value]))

      :set-event-handler
      (let [[e element event handler] event]
        [e (format-element element) event handler])

      event)))

(defn render
  ([vdom] (mutation-log/render nil vdom))
  ([{:keys [vdom el]} new-vdom]
   (mutation-log/render (:element el) new-vdom vdom)))

(defn text-node-event? [event]
  (or (= :create-text-node (first event))
      (and (= :append-child (first event))
           (string? (second event)))))

(defn remove-text-node-events [events]
  (remove text-node-event? events))
