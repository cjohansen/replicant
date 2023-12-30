(ns replicant.perf
  (:require [clj-async-profiler.core :as prof]
            [replicant.core :as core]))

(comment
  (prof/profile (dotimes [_ 10000]
                  (core/get-hiccup-headers [:h1#lol.bla.boz {:style {:height 450}} "Hello world"] "ns")))
  (prof/profile (dotimes [_ 10000]
                  (core/flatten-seqs [:h1#lol.bla.boz {:style {:height 450}} [:div "Hello world"]])))
  (prof/serve-ui 9998))
