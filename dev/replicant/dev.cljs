(ns replicant.dev
  (:require [clojure.walk :as walk]
            [replicant.alias-example]
            [replicant.animate-styles]
            [replicant.assert-example]
            [replicant.contenteditable-bug]
            [replicant.dev-actions :as actions]
            [replicant.dom :as d]
            [replicant.duplicate-key-bug]
            [replicant.indexed-seq]
            [replicant.input]
            [replicant.life-cycle-bug]
            [replicant.memory-example]
            [replicant.multi-select]
            [replicant.nested-rendering-bug]
            [replicant.nested-unmount]
            [replicant.ohm]
            [replicant.on-mount-bug]
            [replicant.range]
            [replicant.shadow-dom]
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
   replicant.multi-select/example
   replicant.nested-rendering-bug/example
   replicant.nested-unmount/example
   replicant.ohm/example
   replicant.on-mount-bug/example
   replicant.range/example-1
   replicant.range/example-2
   replicant.shadow-dom/example
   replicant.svg-foreign-object/example])

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
   (when-let [{:keys [f k]} (actions/get-example examples (:example state))]
     [:div (f state k)])
   (toc)])

(defn interpolate [^js event args]
  (walk/postwalk
   (fn [x]
     (cond
       (= x :event.target/value)
       (.. event -target -value)

       (= x :event.target/selected-option-values)
       (map #(.-value %) (seq (.. event -target -selectedOptions)))

       :else
       x))
   args))

(defn dispatch-actions [{:replicant/keys [dom-event]} actions]
  (js/requestAnimationFrame
   #(doseq [[action & args] actions]
      (let [args (cond->> args
                   dom-event (interpolate dom-event))]
        (apply prn action args)
        (actions/process-actions {:store store
                                  :dispatch-actions dispatch-actions
                                  :examples examples} action args)))))

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
