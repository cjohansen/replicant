(ns replicant.dom
  (:require [replicant.core :as r]
            [replicant.protocols :as replicant]))

(defprotocol IDOMWrapper
  (get-element [this]))

(defn remove-listener [el event]
  (when-let [old-handler (some-> el .-replicantHandlers (aget event))]
    (.removeEventListener el event old-handler)))

(defn create-renderer [el]
  (reify
    replicant/IRender
    (create-text-node [_this text]
      (create-renderer (js/document.createTextNode text)))

    (create-element [_this tag-name options]
      (if-let [ns (:ns options)]
        (create-renderer (js/document.createElementNS ns tag-name))
        (create-renderer (js/document.createElement tag-name))))

    (set-style [this style v]
      (aset (.-style el) (name style) v)
      this)

    (remove-style [this style]
      (aset (.-style el) (name style) nil)
      this)

    (add-class [this cn]
      (.add (.-classList el) cn)
      this)

    (remove-class [this cn]
      (.remove (.-classList el) cn)
      this)

    (set-attribute [this attr v opt]
      (cond
        (= "innerHTML" attr)
        (set! (.-innerHTML el) v)

        (:ns opt)
        (.setAttributeNS el (:ns opt) attr v)

        :else
        (.setAttribute el attr v))
      this)

    (remove-attribute [this attr]
      (if (= :innerHTML attr)
        (set! (.-innerHTML el) "")
        (.removeAttribute el attr))
      this)

    (set-event-handler [this event handler]
      (when-not (.-replicantHandlers el)
        (set! (.-replicantHandlers el) #js {}))
      (let [event (name event)]
        (remove-listener el event)
        (aset (.-replicantHandlers el) event handler)
        (.addEventListener el event handler))
      this)

    (remove-event-handler [this event]
      (let [event (name event)]
        (remove-listener el event)
        (aset (.-replicantHandlers el) event nil))
      this)

    (append-child [this child-node]
      (.appendChild el (get-element child-node))
      this)

    (insert-before [this child-node reference-node]
      (.insertBefore el (get-element child-node) (get-element reference-node))
      this)

    (remove-child [this child-node]
      (.removeChild el (get-element child-node))
      this)

    (replace-child [this insert-child replace-child]
      (.replaceChild el (get-element insert-child) (get-element replace-child))
      this)

    (get-child [_this idx]
      (create-renderer (aget (.-childNodes el) (or idx 0))))

    (get-parent-node [_this]
      (create-renderer (.-parentNode el)))

    IDOMWrapper
    (get-element [_this]
      el)))

(defonce state (atom {}))

(defn ^:export render [el hiccup]
  (when-not (contains? @state el)
    (swap! state assoc el {:renderer (create-renderer el)}))
  (let [{:keys [renderer current]} (get @state el)]
    (r/reconcile renderer hiccup current))
  (swap! state assoc-in [el :current] hiccup)
  el)

(defn ^:export set-dispatch! [f]
  (set! r/*dispatch* f))
