(ns replicant.scenarios
  "Functions that demonstrate various scenarios that replicant needs to handle.
  These can be used in tests to verify that Replicant does the right thing, and
  for performance tuning/diagnostics."
  (:require [replicant.test-helper :as h]))

(def vdom
  (h/render
   [:ul
    [:li {:replicant/key "1"} "#1"]
    [:li {:replicant/key "2"} "#2"]
    [:li {:replicant/key "3"} "#3"]]))

(defn append-node [vdom]
  (h/render vdom [:ul
                  [:li {:replicant/key "1"} "#1"]
                  [:li {:replicant/key "2"} "#2"]
                  [:li {:replicant/key "3"} "#3"]
                  [:li {:replicant/key "4"} "#4"]]))

(defn append-two-nodes [vdom]
  (h/render vdom [:ul
                  [:li {:replicant/key "1"} "#1"]
                  [:li {:replicant/key "2"} "#2"]
                  [:li {:replicant/key "3"} "#3"]
                  [:li {:replicant/key "4"} "#4"]
                  [:li {:replicant/key "5"} "#5"]]))

(defn prepend-node [vdom]
  (h/render vdom [:ul
                  [:li {:replicant/key "4"} "#4"]
                  [:li {:replicant/key "1"} "#1"]
                  [:li {:replicant/key "2"} "#2"]
                  [:li {:replicant/key "3"} "#3"]]))

(defn prepend-two-nodes [vdom]
  (h/render vdom [:ul
                  [:li {:replicant/key "4"} "#4"]
                  [:li {:replicant/key "5"} "#5"]
                  [:li {:replicant/key "1"} "#1"]
                  [:li {:replicant/key "2"} "#2"]
                  [:li {:replicant/key "3"} "#3"]]))

(defn insert-node [vdom]
  (h/render vdom [:ul
                  [:li {:replicant/key "1"} "#1"]
                  [:li {:replicant/key "4"} "#4"]
                  [:li {:replicant/key "2"} "#2"]
                  [:li {:replicant/key "3"} "#3"]]))

(defn insert-two-consecutive-nodes [vdom]
  (h/render vdom [:ul
                  [:li {:replicant/key "1"} "#1"]
                  [:li {:replicant/key "4"} "#4"]
                  [:li {:replicant/key "5"} "#5"]
                  [:li {:replicant/key "2"} "#2"]
                  [:li {:replicant/key "3"} "#3"]]))

(defn insert-two-nodes [vdom]
  (h/render vdom [:ul
                  [:li {:replicant/key "1"} "#1"]
                  [:li {:replicant/key "4"} "#4"]
                  [:li {:replicant/key "2"} "#2"]
                  [:li {:replicant/key "5"} "#5"]
                  [:li {:replicant/key "3"} "#3"]]))

(defn remove-last-node [vdom]
  (h/render vdom [:ul
                  [:li {:replicant/key "1"} "#1"]
                  [:li {:replicant/key "2"} "#2"]]))

(defn remove-first-node [vdom]
  (h/render vdom [:ul
                  [:li {:replicant/key "2"} "#2"]
                  [:li {:replicant/key "3"} "#3"]]))

(defn remove-middle-node [vdom]
  (h/render vdom [:ul
                  [:li {:replicant/key "1"} "#1"]
                  [:li {:replicant/key "3"} "#3"]]))

(defn swap-nodes [vdom]
  (h/render vdom [:ul
                  [:li {:replicant/key "3"} "#3"]
                  [:li {:replicant/key "2"} "#2"]
                  [:li {:replicant/key "1"} "#1"]]))
