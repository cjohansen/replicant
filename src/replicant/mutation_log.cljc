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

(defn -get-child [el idx]
  (-> @el :children (nth idx)
      (with-meta {:parent el})))

(defn -remove-child [el child]
  (update el :children #(vec (remove #{child} %))))

(def id (atom 0))

(def mutation-log-impl
  {`replicant/create-text-node
   (fn [this text]
     (swap! (:log this) conj [:create-text-node text])
     (atom {:text text}))

   `replicant/create-element
   (fn [this tag-name options]
     (swap! (:log this) conj (cond-> [:create-element tag-name]
                               (:ns options) (conj (:ns options))))
     (atom {:tag-name tag-name
            ::id (swap! id inc)}))

   `replicant/set-style
   (fn [this el style v]
     (swap! (:log this) conj [:set-style @el style v])
     (swap! el assoc-in [:style style] v)
     this)

   `replicant/remove-style
   (fn [this el style]
     (swap! (:log this) conj [:remove-style @el style])
     (swap! el update :style dissoc style)
     this)

   `replicant/add-class
   (fn [this el cn]
     (swap! (:log this) conj [:add-class @el cn])
     (swap! el update :classes #(set (conj % cn)))
     this)

   `replicant/remove-class
   (fn [this el cn]
     (swap! (:log this) conj [:remove-class @el cn])
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
       (when-let [parent (:parent (meta child))]
         (swap! parent -remove-child child))
       (swap! el update :children #(conj (vec %) child)))
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
       (swap! el -remove-child child))
     this)

   `replicant/remove-all-children
   (fn [this el]
     (swap! (:log this) conj [:remove-all-children @el])
     (swap! el assoc :children [])
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
     (atom (-get-child el idx)))

   `replicant/next-frame
   (fn [this f]
     (swap! (:log this) conj [:next-frame])
     (f))})

(defn create-renderer [{:keys [log element]}]
  (with-meta
    {:log (or log (atom []))
     :element (or element (atom {}))}
    mutation-log-impl))

(defn render [element new-hiccup & [old-vdom]]
  (let [el (atom (or element {}))
        renderer (create-renderer {:log (atom []) :element el})
        {:keys [hooks vdom]} (d/reconcile renderer el new-hiccup old-vdom)]
    {:el (-> renderer
             (update :log deref)
             (update :element deref))
     :hooks hooks
     :vdom vdom}))

(comment
  (do
    (def el (atom {:tag-name "div"}))
    (def renderer (create-renderer {}))

    (def t1 (replicant/create-text-node renderer "P1"))
    (def e1 (replicant/create-element renderer "p" {}))
    (replicant/append-child renderer e1 t1)

    (def t2 (replicant/create-text-node renderer "P2"))
    (def e2 (replicant/create-element renderer "p" {}))
    (replicant/append-child renderer e2 t2)

    (def t3 (replicant/create-text-node renderer "P3"))
    (def e3 (replicant/create-element renderer "p" {}))
    (replicant/append-child renderer e3 t3)

    (replicant/append-child renderer el e1)
    (replicant/append-child renderer el e2)
    (replicant/insert-before renderer el e3 e2))


  (def child (replicant/get-child renderer el 0))
  (replicant/append-child renderer el child)

  (replicant/insert-before
   renderer
   el
   (replicant/get-child renderer el 0)
   (replicant/get-child renderer el 2))

  (= @el (:parent (meta @(replicant/get-child renderer el 1))))

  el
)
