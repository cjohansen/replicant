(ns replicant.dom
  (:require [replicant.alias :as alias]
            [replicant.assert :as assert]
            [replicant.core :as r]
            [replicant.env :as env]
            [replicant.protocols :as replicant]
            [replicant.transition :as transition]))

(defn ^:no-doc remove-listener [^js/EventTarget el event opt]
  (when-let [old-handler (some-> el .-replicantHandlers (aget event))]
    (.removeEventListener el event old-handler (clj->js opt))))

(defn ^:no-doc on-next-frame [f]
  (js/requestAnimationFrame
   #(js/requestAnimationFrame f)))

(defn ^:no-doc -on-transition-end [el f]
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

(defn ^:no-doc create-renderer []
  (reify
    replicant/IRender
    (create-text-node [_this text]
      (js/document.createTextNode text))

    (create-element [_this tag-name options]
      (if-let [ns (:ns options)]
        (js/document.createElementNS ns tag-name)
        (js/document.createElement tag-name)))

    (set-style [this ^js el style v]
      (.setProperty (.-style el) (name style) v)
      this)

    (remove-style [this ^js el style]
      (.removeProperty (.-style el) (name style))
      this)

    (add-class [this ^js el cn]
      (.add (.-classList el) cn)
      this)

    (remove-class [this ^js el cn]
      (.remove (.-classList el) cn)
      this)

    (set-attribute [this ^js el attr v opt]
      (try
        (cond
          (= "innerHTML" attr)
          (set! (.-innerHTML el) v)

          (= "value" attr)
          (set! (.-value el) v)

          (= "default-value" attr)
          (.setAttribute el "value" v)

          (= "selected" attr)
          (set! (.-selected el) v)

          (= "default-selected" attr)
          (.setAttribute el "selected" v)

          (= "checked" attr)
          (set! (.-checked el) v)

          (= "default-checked" attr)
          (.setAttribute el "checked" v)

          (= "disabled" attr)
          (set! (.-disabled el) v)

          (= "readonly" attr)
          (set! (.-readonly el) v)

          (= "required" attr)
          (set! (.-required el) v)

          (:ns opt)
          (.setAttributeNS el (:ns opt) attr v)

          :else
          (.setAttribute el attr v))
        (catch :default e
          (assert/log-error
           (str "Replicant caught an error during rendering: "
                (.-message e)))))
      this)

    (remove-attribute [this ^js el attr]
      (cond
        (= "innerHTML" attr)
        (set! (.-innerHTML el) "")

        (= "value" attr)
        (set! (.-value el) nil)

        (= "default-value" attr)
        (.removeAttribute el "value")

        (= "selected" attr)
        (set! (.-selected el) nil)

        (= "default-selected" attr)
        (.removeAttribute el "selected")

        (= "checked" attr)
        (set! (.-checked el) nil)

        (= "default-checked" attr)
        (.removeAttribute el "checked")

        (= "disabled" attr)
        (set! (.-disabled el) nil)

        (= "readonly" attr)
        (set! (.-readonly el) nil)

        (= "required" attr)
        (set! (.-required el) nil)

        :else
        (.removeAttribute el attr))
      this)

    (set-event-handler [this ^js/EventTarget el event handler opt]
      (when-not (.-replicantHandlers el)
        (set! (.-replicantHandlers el) #js {}))
      (let [event (name event)]
        (remove-listener el event opt)
        (aset (.-replicantHandlers el) event handler)
        (.addEventListener el event handler (clj->js opt)))
      this)

    (remove-event-handler [this ^js/EventTarget el event opt]
      (let [event (name event)]
        (remove-listener el event opt)
        (aset (.-replicantHandlers el) event nil))
      this)

    (append-child [this ^js el child-node]
      (.appendChild el child-node)
      this)

    (insert-before [this ^js el child-node reference-node]
      (.insertBefore el child-node reference-node)
      this)

    (remove-child [this ^js el child-node]
      (.removeChild el child-node)
      this)

    (on-transition-end [this ^js el f]
      (-on-transition-end el f)
      this)

    (replace-child [this ^js el insert-child replace-child]
      (.replaceChild el insert-child replace-child)
      this)

    (remove-all-children [this ^js el]
      (set! (.-textContent el) "")
      this)

    (get-child [_this ^js el idx]
      (aget (.-childNodes el) idx))

    (next-frame [_this f]
      (on-next-frame f))))

(defonce ^:no-doc state (volatile! {}))

(defn ^:export render
  "Render `hiccup` in DOM element `el`. Replaces any pre-existing content not
  created by this function. Subsequent calls with the same `el` will update the
  rendered DOM by comparing `hiccup` to the previous `hiccup`."
  [^js el hiccup & [{:keys [aliases alias-data]}]]
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
              aliases (or aliases (alias/get-registered-aliases))
              hiccup (env/with-dev-keys hiccup [aliases alias-data])
              {:keys [vdom]} (r/reconcile renderer el hiccup current {:unmounts unmounts
                                                                      :aliases aliases
                                                                      :alias-data alias-data})]
          (vswap! state update el merge {:current vdom
                                         :rendering? false})
          (when-let [pending (first (:queue (get @state el)))]
            (js/requestAnimationFrame #(render el pending))
            (vswap! state update-in [el :queue] #(vec (rest %))))))))
  el)

(defn ^:export unmount
  "Unmounts elements in `el`, and clears internal state."
  [^js el]
  (if (get-in @state [el :rendering?])
    (js/requestAnimationFrame #(unmount el))
    (do
      (render el nil)
      (vswap! state dissoc el)
      nil)))

(defn ^:export set-dispatch!
  "Register a global dispatch function for event handlers and life-cycle hooks
  that are not functions. See data-driven event handlers and life-cycle hooks in
  the user guide for details."
  [f]
  (set! r/*dispatch* f))
