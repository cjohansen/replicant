(ns replicant.dom2
  (:require [replicant.transition :as transition]))

(defn remove-listener [el event]
  (when-let [old-handler (some-> el .-replicantHandlers (aget event))]
    (.removeEventListener el event old-handler)))

(defn on-next-frame [f]
  (js/requestAnimationFrame
   #(js/requestAnimationFrame f)))

(defn -on-transition-end [el f]
  (let [[n dur] (-> (js/window.getComputedStyle el)
                    (.getPropertyValue "transition-duration")
                    transition/get-transition-stats)]
    (if (= n 0)
      (f)
      (let [complete (volatile! 0)
            timer (volatile! nil)
            started (js/Date.)
            callback (fn listener [& _args]
                       (let [cn (vswap! complete inc)]
                         (when (or (<= n cn)
                                   (< dur (- (js/Date.) started)))
                           (.removeEventListener el "transitionend" listener)
                           (js/clearTimeout @timer)
                           (f))))]
        (.addEventListener el "transitionend" callback)
        ;; The timer is a fail-safe. You could have set transition properties
        ;; that either don't change, or don't change in a way that triggers an
        ;; actual transition on unmount (e.g. changing height from auto to 0
        ;; causes no transition). When this happens, there will not be as many
        ;; transitionend events as there are transition durations. To avoid
        ;; getting stuck, the timer will come in and clean up.
        ;;
        ;; The timer is set with a hefty delay to avoid cutting a transition
        ;; short, in the case of a backed up browser working on overtime. Not
        ;; sure how realistic this is, but better safe than sorry, and the
        ;; important part is that the element doesn't get stuck forever.
        (vreset! timer (js/setTimeout callback (+ dur 200)))))))

(defn summarize [el]
  (if el
    (str (.toLowerCase (.-tagName el)) ": "(.-innerText el))
    "Nil! Will blow"))

(defn -create-text-node [text]
  (js/document.createTextNode text))

(defn -create-element [tag-name options]
  (if-let [ns (:ns options)]
    (js/document.createElementNS ns tag-name)
    (js/document.createElement tag-name)))

(defn -set-style [el style v]
  (aset (.-style el) (name style) v))

(defn -remove-style [el style]
  (aset (.-style el) (name style) nil))

(defn -add-class [el cn]
  (.add (.-classList el) cn))

(defn -remove-class [el cn]
  (.remove (.-classList el) cn))

(defn -set-attribute [el attr v opt]
  (cond
    (= "innerHTML" attr)
    (set! (.-innerHTML el) v)

    (:ns opt)
    (.setAttributeNS el (:ns opt) attr v)

    :else
    (.setAttribute el attr v)))

(defn -remove-attribute [el attr]
  (if (= :innerHTML attr)
    (set! (.-innerHTML el) "")
    (.removeAttribute el attr)))

(defn -set-event-handler [el event handler]
  (when-not (.-replicantHandlers el)
    (set! (.-replicantHandlers el) #js {}))
  (let [event (name event)]
    (remove-listener el event)
    (aset (.-replicantHandlers el) event handler)
    (.addEventListener el event handler)))

(defn -remove-event-handler [el event]
  (-remove-event-handler el event))

(defn -append-child [el child-node]
  (.appendChild el child-node))

(defn -insert-before [el child-node reference-node]
  (.insertBefore el child-node reference-node))

(defn -remove-child [el child-node]
  (.removeChild el child-node))

(defn -replace-child [el insert-child replace-child]
  (.replaceChild el insert-child replace-child))

(defn -remove-all-children [el]
  (set! (.-textContent el) ""))

(defn -get-child [el idx]
  (aget (.-childNodes el) idx))
