(ns replicant.mutation-log
  (:require [replicant.core :as d]
            [replicant.protocols :as replicant]
            [clojure.string :as str]))

(declare create-renderer)

(defn -insert-before [children child reference]
  (let [idx (.indexOf children reference)]
    (concat (remove #{child} (take idx children))
            [child]
            (remove #{child} (drop idx children)))))

(defn -replace-child [element insert-child replace-child]
  (if-let [id (::id @replace-child)]
    (update element :children #(conj (vec (remove (comp #{id} ::id deref) %)) insert-child))
    (let [to-remove #{@replace-child}]
      (update element :children #(conj (vec (remove (comp to-remove deref) %)) insert-child)))))

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

(defn format-hiccup [h & [indent]]
  (let [[tag attrs & children] h
        indent (or indent 0)
        string-content? (= 0 (count (remove string? children)))]
    (str (str/join (repeat (* indent 2) " "))
         "[" tag " " attrs (when-not string-content? "\n")
         (str/join
          (if string-content? "" "\n")
          (for [child children]
            (str (if (string? child)
                   (str " " child)
                   (format-hiccup child (inc indent))))))
         "]")))

(defn get-hiccup [element]
  (let [el (deref element)]
    (if-let [tag (:tag-name el)]
      (cond-> [tag (dissoc el :tag-name :children)]
        (:children el) (into (map get-hiccup (:children el))))
      (:text el))))

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
     (atom {:text text}))

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
     (println (:tag-name @child-node)
              (:text @(first (:children @child-node)))
              "before"
              (:tag-name @reference-node)
              (:text @(first (:children @reference-node))))
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
     (f))

   `replicant/get-outer-html
   (fn [this el]
     (format-hiccup (get-hiccup el)))})

(defn create-renderer [{:keys [log element]}]
  (with-meta
    {:log (or log (atom []))
     :element (or element (atom {}))
     :callbacks (atom [])}
    mutation-log-impl))

(defn render [element new-hiccup & [old-vdom unmounts]]
  (let [el (atom (or element {}))
        renderer (create-renderer {:log (atom []) :element el})]
    (-> (d/reconcile renderer el new-hiccup old-vdom {:unmounts unmounts})
        (assoc :el (-> renderer
                       (update :log deref)
                       (update :element deref))))))

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
