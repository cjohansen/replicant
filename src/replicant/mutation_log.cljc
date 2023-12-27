(ns replicant.mutation-log
  (:require [replicant.core :as d]
            [replicant.protocols :as replicant]))

(declare create-renderer)

(defn -insert-before [children child reference]
  (let [idx (.indexOf children reference)]
    (concat (remove #{child} (take idx children))
            [child]
            (remove #{child} (drop idx children)))))

(defn -replace-child [element insert-child replace-child]
  (let [to-remove #{replace-child}]
    (update element :children #(conj (vec (remove to-remove %)) insert-child))))

(defn -get-child [element idx]
  (-> element :children (nth idx)
      (with-meta {:parent element})))

(def mutation-log-impl
  {`replicant/create-text-node
   (fn [this text]
     (swap! (:log this) conj [:create-text-node text])
     (atom {:text text}))

   `replicant/create-element
   (fn [this tag-name options]
     (swap! (:log this) conj (cond-> [:create-element tag-name]
                               (:ns options) (conj (:ns options))))
     (atom {:tag-name tag-name}))

   `replicant/set-style
   (fn [this el style v]
     (swap! (:log this) conj [:set-style style v])
     (swap! el assoc-in [:style style] v)
     this)

   `replicant/remove-style
   (fn [this el style]
     (swap! (:log this) conj [:remove-style style])
     (swap! el update :style dissoc style)
     this)

   `replicant/add-class
   (fn [this el cn]
     (swap! (:log this) conj [:add-class cn])
     (swap! el update :classes #(set (conj % cn)))
     this)

   `replicant/remove-class
   (fn [this el cn]
     (swap! (:log this) conj [:remove-class cn])
     (swap! el update :classes disj cn)
     this)

   `replicant/set-attribute
   (fn [this el attr v opt]
     (swap! (:log this) conj (cond-> [:set-attribute @el attr v]
                               (:ns opt) (conj (:ns opt))))
     (swap! el assoc attr v)
     this)

   `replicant/remove-attribute
   (fn [this el attr]
     (swap! (:log this) conj [:remove-attribute attr])
     (swap! el dissoc attr)
     this)

   `replicant/set-event-handler
   (fn [this el event handler]
     (swap! (:log this) conj [:set-event-handler @el event handler])
     (swap! el assoc-in [:on event] handler)
     this)

   `replicant/remove-event-handler
   (fn [this el event]
     (swap! (:log this) conj [:remove-event-handler event])
     (swap! el update :on dissoc event)
     this)

   `replicant/append-child
   (fn [this el child-node]
     (let [child @child-node]
       (swap! (:log this) conj [:append-child @el child])
       (swap! el update :children #(vec (concat % [child]))))
     this)

   `replicant/insert-before
   (fn [this el child-node reference-node]
     (let [child @child-node
           reference @reference-node]
       (swap! (:log this) conj [:insert-before @el child reference])
       (swap! el update :children -insert-before child reference)
       this))

   `replicant/remove-child
   (fn [this el child-node]
     (let [child @child-node]
       (swap! (:log this) conj [:remove-child @el child])
       (swap! el update :children #(vec (remove #{child} %))))
     this)

   `replicant/replace-child
   (fn [this el insert-child replace-child]
     (let [insert @insert-child
           replacement @replace-child]
       (swap! (:log this) conj [:replace-child insert replacement])
       (swap! el -replace-child insert replacement))
     this)

   `replicant/get-child
   (fn [this el idx]
     (swap! (:log this) conj [:get-child idx])
     (atom (-get-child @el idx)))})

(defn create-renderer [{:keys [log element]}]
  (with-meta
    {:log (or log (atom []))
     :element (or element (atom {}))}
    mutation-log-impl))

(defn render [element new-hiccup & [old-hiccup]]
  (let [el (atom (or element {}))
        renderer (create-renderer {:log (atom []) :element el})
        {:keys [hooks]} (d/reconcile renderer el new-hiccup old-hiccup)]
    {:el (-> renderer
             (update :log deref)
             (update :element deref))
     :hooks hooks}))
