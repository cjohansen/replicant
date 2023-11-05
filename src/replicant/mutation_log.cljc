(ns replicant.mutation-log
  (:require [replicant.core :as d]
            [replicant.protocols :as replicant]))

(declare create-renderer)

(defn update-parent [this this-prev]
  (let [parent (-> this-prev :element meta :parent (update :children vec))
        idx (.indexOf (:children parent) (:element this-prev))]
    (if (<= 0 idx)
      (update this :element with-meta {:parent (assoc-in parent [:children idx] (:element this))})
      this)))

(defn -insert-before [children child reference]
  (let [idx (.indexOf children reference)]
    (concat (remove #{child} (take idx children))
            [child]
            (remove #{child} (drop idx children)))))

(defn -replace-child [this insert-child replace-child]
  (let [to-remove #{(:element replace-child)}]
    (-> this
        (update-in [:element :children]
                   #(conj (vec (remove to-remove %)) (:element insert-child)))
        (update-parent this)
        create-renderer)))

(defn -get-child [this idx]
  (let [parent (:element this)]
    (-> this
        (assoc :element (-> this :element :children (nth idx)))
        (update :element with-meta {:parent parent})
        create-renderer)))

(def mutation-log-impl
  {`replicant/create-text-node
   (fn [this text]
     (swap! (:log this) conj [:create-text-node text])
     (create-renderer (assoc this :element {:text text})))

   `replicant/create-element
   (fn [this tag-name options]
     (swap! (:log this) conj (cond-> [:create-element tag-name]
                               (:ns options) (conj (:ns options))))
     (create-renderer (assoc this :element {:tag-name tag-name})))

   `replicant/set-style
   (fn [this style v]
     (swap! (:log this) conj [:set-style style v])
     (create-renderer (assoc-in this [:element :style style] v)))

   `replicant/remove-style
   (fn [this style]
     (swap! (:log this) conj [:remove-style style])
     (create-renderer (update-in this [:element :style] dissoc style)))

   `replicant/add-class
   (fn [this cn]
     (swap! (:log this) conj [:add-class cn])
     (create-renderer (update-in this [:element :classes] #(set (conj % cn)))))

   `replicant/remove-class
   (fn [this cn]
     (swap! (:log this) conj [:remove-class cn])
     (create-renderer (update-in this [:element :classes] disj cn)))

   `replicant/set-attribute
   (fn [this attr v opt]
     (swap! (:log this) conj (cond-> [:set-attribute (:element this) attr v]
                               (:ns opt) (conj (:ns opt))))
     (create-renderer (assoc-in this [:element attr] v)))

   `replicant/remove-attribute
   (fn [this attr]
     (swap! (:log this) conj [:remove-attribute attr])
     (create-renderer (update this :element dissoc attr)))

   `replicant/set-event-handler
   (fn [this event handler]
     (swap! (:log this) conj [:set-event-handler (:element this) event handler])
     (create-renderer (assoc-in this [:element :on event] handler)))

   `replicant/remove-event-handler
   (fn [this event]
     (swap! (:log this) conj [:remove-event-handler event])
     (create-renderer (update-in this [:element :on] dissoc event)))

   `replicant/append-child
   (fn [this child-node]
     (swap! (:log this) conj [:append-child (:element this) (:element child-node)])
     (-> this
         (update-in [:element :children] #(vec (concat % [(:element child-node)])))
         (update-parent this)
         create-renderer))

   `replicant/insert-before
   (fn [this child-node reference-node]
     (let [child (:element child-node)
           reference (:element reference-node)]
       (swap! (:log this) conj [:insert-before (:element this) child reference])
       (-> this
           (update-in [:element :children] -insert-before child reference)
           (update-parent this)
           create-renderer)))

   `replicant/remove-child
   (fn [this child-node]
     (swap! (:log this) conj [:remove-child (:element this) (:element child-node)])
     (let [to-remove #{(:element child-node)}]
       (-> this
           (update-in [:element :children] #(vec (remove to-remove %)))
           (update-parent this)
           create-renderer)))

   `replicant/replace-child
   (fn [this insert-child replace-child]
     (swap! (:log this) conj [:replace-child (:element insert-child) (:element replace-child)])
     (-replace-child this insert-child replace-child))

   `replicant/get-child
   (fn [this idx]
     (swap! (:log this) conj [:get-child idx])
     (-get-child this idx))

   `replicant/get-parent-node
   (fn [this]
     (swap! (:log this) conj [:get-parent-node])
     (create-renderer (update this :element #(:parent (meta %)))))})

(defn create-renderer [{:keys [log element]}]
  (with-meta
    {:log (or log [])
     :element (or element {})}
    mutation-log-impl))

(defn render [element new-hiccup & [old-hiccup]]
  (-> (create-renderer {:log (atom []) :element element})
      (d/reconcile new-hiccup old-hiccup)
      (update-in [:el :log] deref)))
