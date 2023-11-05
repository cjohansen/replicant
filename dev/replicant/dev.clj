(ns replicant.dev
  (:require [replicant.memtree :as mt]
            [replicant.mutation-log :as mlog]))

(comment

  (set! *print-namespace-maps* false)

  (-> (mlog/render
       [:div
        [:main
         [:h1 "Title"]
         [:p "Hello"]]
        [:aside
         [:h2 "Aside"]]])
      count)

  (mlog/render [:div [:h1 "Hello"]] [:div [:h1 "Hello!"]])

  (let [vdom [:div
              [:main
               [:h1 "Title"]
               [:p "Hello"]]]]
    (-> {:path []}
        (mt/render vdom)
        ;;(mt/render (assoc-in vdom [1 1 1] "Lol!") vdom)
        ))

  (mt/render
   {:path []}
   [:div
    [:main
     [:h1 "Title"]
     [:p "Hello"]]
    [:aside
     [:h2 "Aside"]]])

  )
