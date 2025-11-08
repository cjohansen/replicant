(ns replicant.dev
  (:require [clojure.walk :as walk]
            [replicant.alias-example]
            [replicant.animate-styles]
            [replicant.assert-example]
            [replicant.contenteditable-bug]
            [replicant.dom :as d]
            [replicant.duplicate-key-bug]
            [replicant.indexed-seq]
            [replicant.input]
            [replicant.life-cycle-bug]
            [replicant.memory-example]
            [replicant.nested-rendering-bug]
            [replicant.ohm]
            [replicant.on-mount-bug]
            [replicant.range]
            [replicant.svg-foreign-object]))

(defonce store (atom {}))

(def examples
  [replicant.alias-example/example
   replicant.animate-styles/example
   replicant.assert-example/example
   replicant.contenteditable-bug/example
   replicant.duplicate-key-bug/example
   replicant.indexed-seq/example
   replicant.input/example
   replicant.life-cycle-bug/example
   replicant.memory-example/example
   replicant.nested-rendering-bug/example
   replicant.ohm/example
   replicant.on-mount-bug/example
   replicant.range/example-1
   replicant.range/example-2
   replicant.svg-foreign-object/example])

(defn get-example [k]
  (first (filter (comp #{k} :k) examples)))

(defn toc []
  [:main
   [:h1 "Replicant examples"]
   [:ul.list
    (for [{:keys [title k]} examples]
      [:li
       [:button.link
        {:type "button"
         :on {:click [[:actions/go-to k]]}}
        title]])]])

(defn render [state]
  [:main
   (when-let [{:keys [f k]} (get-example (:example state))]
     [:div (f state k)])
   (toc)])

(defn interpolate [event args]
  (walk/postwalk
   (fn [x]
     (if (= x :event.target/value)
       (.. ^js event -target -value)
       x))
   args))

(defn dispatch-actions [{:replicant/keys [dom-event]} actions]
  (doseq [[action & args] actions]
    (let [args (cond->> args
                 dom-event (interpolate dom-event))]
      (apply prn action args)
      (case action
        :actions/go-to
        (swap! store (fn [state]
                       (let [k (first args)
                             example (get-example k)]
                         (cond-> (assoc state :example k)
                           (:initial-data example)
                           (assoc k (:initial-data example))))))

        :actions/assoc-in
        (apply swap! store assoc-in args)

        :actions/conj-in
        (let [[path v] args]
          (swap! store update-in path conj v))

        :actions/log
        nil

        (let [k (:example @store)]
          (if-let [impl (get-in (get-example k) [:actions action])]
            (dispatch-actions nil (apply impl (get @store k) args))
            (println "Unknown action" action)))))))

(defn start []
  (set! (.-innerHTML js/document.body) "<div id=\"app\"></div>")
  (d/set-dispatch! dispatch-actions)
  (let [el (js/document.getElementById "app")]
    (add-watch store ::render (fn [_ _ _ state]
                                (d/render el (render state))))
    (swap! store assoc ::booted-at (.getTime (js/Date.)))))

(defn ^:export main []
  (start))

(comment
  (enable-console-print!)
  (set! *print-namespace-maps* false)
)
