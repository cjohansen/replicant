(ns replicant.mutation-log
  (:require [replicant.core :as d]
            [replicant.protocols :as replicant]))

(declare create-renderer)

(defn ->hiccup [element]
  (when-let [el (some-> element deref)]
    (if-let [tag-name (:tag-name el)]
      (into [(keyword tag-name)]
            (if-let [inner-html (get el "innerHTML")]
              [inner-html]
              (map ->hiccup (:children el))))
      (:text el))))

(defn -insert-before [children child reference]
  (let [idx (.indexOf children reference)]
    (concat (remove #{child} (take idx children))
            [child]
            (remove #{child} (drop idx children)))))

(defn replace-by [xs f new replace]
  (let [replace-v (f replace)]
    (for [x xs]
      (if (= (f x) replace-v)
        new
        x))))

(defn -replace-child [element insert-child replace-child]
  (if (::id @replace-child)
    (update element :children replace-by #(::id @%) insert-child replace-child)
    (update element :children replace-by deref insert-child replace-child)))

(defn -get-child [el idx]
  (-> @el :children (nth idx)))

(defn -remove-child [el child-node]
  (let [id #{(::id @child-node)}]
    (update el :children #(vec (remove (comp id ::id deref) %)))))

(def id (atom 0))

(defn get-snapshot [element]
  (let [el (deref element)]
    (cond-> el
      (:children el) (update :children #(map get-snapshot %)))))

(defn atom? [x]
  (instance? #?(:clj clojure.lang.Atom
                :cljs cljs.core/Atom) x))

(defn log [this event]
  (swap! (:log this) conj (mapv #(if (atom? %) (get-snapshot %) %) event)))

(defn set-parent [node new-parent]
  (when-let [parent (:parent (meta @node))]
    (when-not (= parent new-parent)
      (swap! parent -remove-child node)))
  (swap! node with-meta {:parent new-parent}))

(defn -append-child [el child-node]
  (set-parent child-node el)
  (swap! el update :children #(conj (vec %) child-node)))

(def mutation-log-impl
  {`replicant/create-text-node
   (fn [this text]
     (log this [:create-text-node text])
     (atom {:text text
            ::id (swap! id inc)}))

   `replicant/create-element
   (fn [this tag-name options]
     (log this (cond-> [:create-element tag-name]
                 (:ns options) (conj (:ns options))))
     (atom {:tag-name tag-name
            ::id (swap! id inc)}))

   `replicant/set-style
   (fn [this el style v]
     (log this [:set-style el style v])
     (swap! el assoc-in [:style style] v)
     this)

   `replicant/remove-style
   (fn [this el style]
     (log this [:remove-style el style])
     (swap! el update :style dissoc style)
     this)

   `replicant/add-class
   (fn [this el cn]
     (log this [:add-class el cn])
     (swap! el update :classes #(set (conj % cn)))
     this)

   `replicant/remove-class
   (fn [this el cn]
     (log this [:remove-class el cn])
     (swap! el update :classes disj cn)
     this)

   `replicant/set-attribute
   (fn [this el attr v opt]
     (log this (cond-> [:set-attribute (get-snapshot el) attr v]
                 (:ns opt) (conj (:ns opt))))
     (swap! el assoc attr v)
     this)

   `replicant/remove-attribute
   (fn [this el attr]
     (log this [:remove-attribute attr])
     (swap! el dissoc attr)
     this)

   `replicant/set-event-handler
   (fn [this el event handler]
     (log this [:set-event-handler el event handler])
     (swap! el assoc-in [:on event] handler)
     this)

   `replicant/remove-event-handler
   (fn [this el event]
     (log this [:remove-event-handler el event])
     (swap! el update :on dissoc event)
     this)

   `replicant/append-child
   (fn [this el child-node]
     (log this [:append-child el child-node])
     (-append-child el child-node)
     this)

   `replicant/insert-before
   (fn [this el child-node reference-node]
     (log this [:insert-before el child-node reference-node])
     (swap! el update :children -insert-before child-node reference-node)
     (set-parent child-node el)
     this)

   `replicant/remove-child
   (fn [this el child-node]
     (log this [:remove-child el child-node])
     (swap! el -remove-child child-node)
     this)

   `replicant/on-transition-end
   (fn [this el f]
     (log this [:on-transition-end el])
     (swap! (:callbacks this) conj f)
     this)

   `replicant/replace-child
   (fn [this el insert-child replace-child]
     (log this [:replace-child insert-child replace-child])
     (swap! el -replace-child insert-child replace-child)
     this)

   `replicant/remove-all-children
   (fn [this el]
     (log this [:remove-all-children el])
     (swap! el assoc :children [])
     this)

   `replicant/get-child
   (fn [this el idx]
     (log this [:get-child idx])
     (-get-child el idx))

   `replicant/next-frame
   (fn [this f]
     (log this [:next-frame])
     (f))})

(defn create-renderer [{:keys [log element]}]
  (with-meta
    {:log (or log (atom []))
     :element (or element (atom {}))
     :callbacks (atom [])}
    mutation-log-impl))

(defn render [element new-hiccup & [old-vdom {:keys [unmounts aliases]}]]
  (let [el (atom (or element {}))
        renderer (create-renderer {:log (atom []) :element el})]
    (-> (d/reconcile renderer el new-hiccup old-vdom {:unmounts unmounts
                                                      :aliases aliases})
        (assoc :el (-> renderer
                       (update :log deref)
                       (update :element deref)))
        (assoc :aliases aliases))))

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
