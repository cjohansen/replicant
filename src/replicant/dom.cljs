(ns replicant.dom
  (:require [replicant.alias :as alias]
            [replicant.core :as r]
            [replicant.protocols :as replicant]
            [replicant.transition :as transition]))

(defn remove-listener [^js/EventTarget el event]
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

(defn create-renderer []
  (reify
    replicant/IRender
    (create-text-node [_this text]
      (js/document.createTextNode text))

    (create-element [_this tag-name options]
      (if-let [ns (:ns options)]
        (js/document.createElementNS ns tag-name)
        (js/document.createElement tag-name)))

    (set-style [this el style v]
      (.setProperty (.-style el) (name style) v)
      this)

    (remove-style [this el style]
      (.removeProperty (.-style el) (name style))
      this)

    (add-class [this el cn]
      (.add (.-classList el) cn)
      this)

    (remove-class [this el cn]
      (.remove (.-classList el) cn)
      this)

    (set-attribute [this el attr v opt]
      (cond
        (= "innerHTML" attr)
        (set! (.-innerHTML el) v)

        (= "value" attr)
        (set! (.-value el) v)

        (:ns opt)
        (.setAttributeNS el (:ns opt) attr v)

        :else
        (.setAttribute el attr v))
      this)

    (remove-attribute [this el attr]
      (if (= "innerHTML" attr)
        (set! (.-innerHTML el) "")
        (.removeAttribute el attr))
      this)

    (set-event-handler [this ^js/EventTarget el event handler]
      (when-not (.-replicantHandlers el)
        (set! (.-replicantHandlers el) #js {}))
      (let [event (name event)]
        (remove-listener el event)
        (aset (.-replicantHandlers el) event handler)
        (.addEventListener el event handler))
      this)

    (remove-event-handler [this ^js/EventTarget el event]
      (let [event (name event)]
        (remove-listener el event)
        (aset (.-replicantHandlers el) event nil))
      this)

    (append-child [this el child-node]
      (.appendChild el child-node)
      this)

    (insert-before [this el child-node reference-node]
      (.insertBefore el child-node reference-node)
      this)

    (remove-child [this el child-node]
      (.removeChild el child-node)
      this)

    (on-transition-end [this el f]
      (-on-transition-end el f)
      this)

    (replace-child [this el insert-child replace-child]
      (.replaceChild el insert-child replace-child)
      this)

    (remove-all-children [this el]
      (set! (.-textContent el) "")
      this)

    (get-child [_this el idx]
      (aget (.-childNodes el) idx))

    (next-frame [_this f]
      (on-next-frame f))))

(defonce state (volatile! {}))

(defn ^:export render
  "Render `hiccup` in DOM element `el`. Replaces any pre-existing content not
  created by this function. Subsequent calls with the same `el` will update the
  rendered DOM by comparing `hiccup` to the previous `hiccup`."
  [el hiccup & [{:keys [aliases]}]]
  (let [rendering? (get-in @state [el :rendering?])]
    (when-not (contains? @state el)
      (set! (.-innerHTML el) "")
      (vswap! state assoc el {:renderer (create-renderer)
                              :unmounts (volatile! #{})
                              :rendering? true
                              :queue []}))
    (if rendering?
      (vswap! state update-in [el :queue] #(conj % hiccup))
      (do
        (vswap! state assoc-in [el :rendering?] true)
        (let [{:keys [renderer current unmounts]} (get @state el)
              aliases (or aliases (alias/get-aliases))
              hiccup (alias/key-hiccup hiccup aliases)
              {:keys [vdom]} (r/reconcile renderer el hiccup current {:unmounts unmounts
                                                                      :aliases aliases})]
          (vswap! state update el merge {:current vdom
                                         :rendering? false})
          (when-let [pending (first (:queue (get @state el)))]
            (js/requestAnimationFrame #(render el pending))
            (vswap! state update-in [el :queue] #(vec (rest %))))))))
  el)

(defn ^:export unmount
  "Unmounts elements in `el`, and clears internal state."
  [el]
  (if (get-in @state [el :rendering?])
    (js/requestAnimationFrame #(unmount el))
    (do
      (render el nil)
      (vswap! state dissoc el)
      nil)))

(defn ^:export set-dispatch! [f]
  (set! r/*dispatch* f))
