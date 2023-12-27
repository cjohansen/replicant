(ns replicant.dom
  (:require [replicant.core :as r]
            [replicant.protocols :as replicant]))

(defn remove-listener [el event]
  (when-let [old-handler (some-> el .-replicantHandlers (aget event))]
    (.removeEventListener el event old-handler)))

(defn create-renderer []
  (let [hooks #js []]
    (reify
      replicant/IRender
      (create-text-node [_this text]
        (js/document.createTextNode text))

      (create-element [_this tag-name options]
        (if-let [ns (:ns options)]
          (js/document.createElementNS ns tag-name)
          (js/document.createElement tag-name)))

      (set-style [this el style v]
        (aset (.-style el) (name style) v)
        this)

      (remove-style [this el style]
        (aset (.-style el) (name style) nil)
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

          (:ns opt)
          (.setAttributeNS el (:ns opt) attr v)

          :else
          (.setAttribute el attr v))
        this)

      (remove-attribute [this el attr]
        (if (= :innerHTML attr)
          (set! (.-innerHTML el) "")
          (.removeAttribute el attr))
        this)

      (set-event-handler [this el event handler]
        (when-not (.-replicantHandlers el)
          (set! (.-replicantHandlers el) #js {}))
        (let [event (name event)]
          (remove-listener el event)
          (aset (.-replicantHandlers el) event handler)
          (.addEventListener el event handler))
        this)

      (remove-event-handler [this el event]
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

      (replace-child [this el insert-child replace-child]
        (.replaceChild el insert-child replace-child)
        this)

      (get-child [_this el idx]
        (aget (.-childNodes el) (or idx 0))))))

(defonce state (atom {}))

(defn ^:export render [el hiccup]
  (when-not (contains? @state el)
    (swap! state assoc el {:renderer (create-renderer)}))
  (let [{:keys [renderer current]} (get @state el)]
    (r/reconcile renderer el hiccup current))
  (swap! state assoc-in [el :current] hiccup)
  el)

(defn ^:export set-dispatch! [f]
  (set! r/*dispatch* f))
