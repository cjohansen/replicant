(ns replicant.core
  (:require [replicant.hiccup :as hiccup]
            [replicant.protocols :as r]))

(def ^:dynamic *dispatch* nil)

(defn get-event-handler
  "Returns the function to use for handling DOM events. Uses `handler` directly
  when it's a function or a string (assumed to be inline JavaScript, not really
  recommended), or a wrapper that dispatches through
  `replicant.core/*dispatch*`, if it is bound to a function. "
  [handler event]
  (or (when (fn? handler)
        handler)
      (when (ifn? *dispatch*)
        (fn [e]
          (*dispatch* {:replicant/event :replicant.event/dom-event} e handler)))
      (when (string? handler)
        ;; Strings could be inline JavaScript, so will be allowed when there is
        ;; no global event handler.
        handler)
      (throw (ex-info "Cannot use non-function event handler when replicant.core/*dispatch* is not bound to a function"
                      {:event event
                       :handler handler
                       :dispatch *dispatch*}))))

(defn get-life-cycle-hook
  "Returns the function to use to dispatch life-cycle hooks on an element. Uses
  `handler` directly when it's a function, or a wrapper that dispatches through
  `replicant.core/*dispatch*`, if it is bound to a function."
  [handler]
  (or (when (fn? handler)
        handler)
      (when (and handler (ifn? *dispatch*))
        (fn [e]
          (*dispatch* e handler)))
      (when handler
        (throw (ex-info "Cannot use non-function life-cycle hook when replicant.core/*dispatch* is not bound to a function"
                        {:handler handler
                         :dispatch *dispatch*})))))

(defn register-hook
  "Register the life-cycle hook from the corresponding virtual DOM node to call in
  `impl`, if any. The only time the hook in `old` is used is when `new` is
  `nil`, which means the node is unmounting. `details` is a vector of keywords
  that provide some detail about why the hook is invoked."
  [{:keys [hooks]} node new & [old details]]
  (when-let [hook (:replicant/on-update (if new (second new) (second old)))]
    (swap! hooks conj [hook node new old details])))

(defn update-styles [impl el new-styles old-styles]
  (doseq [k (seq (into (set (keys new-styles)) (keys old-styles)))]
    (let [new-style (k new-styles)]
      (cond
        (nil? new-style)
        (r/remove-style (:renderer impl) el k)

        (not= new-style (k old-styles))
        (r/set-style (:renderer impl) el k new-style)))))

(defn update-classes [impl el new-classes old-classes]
  (doseq [class (remove (set new-classes) old-classes)]
    (r/remove-class (:renderer impl) el class))
  (doseq [class (remove (set old-classes) new-classes)]
    (r/add-class (:renderer impl) el class)))

(defn add-event-listeners [impl el val]
  (doseq [[event handler] val]
    (when-let [handler (get-event-handler handler event)]
      (r/set-event-handler (:renderer impl) el event handler))))

(defn update-event-listeners [impl el new-handlers old-handlers]
  (doseq [event (remove (set (keys new-handlers)) (keys old-handlers))]
    (r/remove-event-handler (:renderer impl) el event))
  (->> (remove #(= (val %) (get old-handlers (key %))) new-handlers)
       (add-event-listeners impl el)))

(def xlinkns "http://www.w3.org/1999/xlink")
(def xmlns "http://www.w3.org/XML/1998/namespace")

(defn update-attr [impl el attr new old]
  (case attr
    :style (update-styles impl el (:style new) (:style old))
    :classes (update-classes impl el (:classes new) (:classes old))
    :on (update-event-listeners impl el (:on new) (:on old))
    (if-let [v (attr new)]
      (when (not= v (attr old))
        (let [an (name attr)]
          (->> (cond-> {}
                 (#{["x" "m" "l"] ;; ClojureScript
                    [\x \m \l]} ;; Clojure
                  (take 3 an))
                 (assoc :ns xmlns)

                 (#{["x" "l" "i" "n" "k" ":"]
                    [\x \l \i \n \k \:]}
                  (take 6 an))
                 (assoc :ns xlinkns))
               (r/set-attribute (:renderer impl) el an v))))
      (r/remove-attribute (:renderer impl) el (name attr)))))

(defn update-attributes [impl el new-attrs old-attrs]
  (doseq [attr (into (set (keys new-attrs)) (keys old-attrs))]
    (update-attr impl el attr new-attrs old-attrs))
  {:changed? (not= new-attrs old-attrs)})

(defn- strip-nil-vals [m]
  (into {} (remove (comp nil? val) m)))

(defn- update-existing [m k & args]
  (if (contains? m k)
    (apply update m k args)
    m))

(defn prep-attributes [attrs]
  (-> attrs
      (dissoc :key :replicant/on-update ::ns)
      strip-nil-vals
      (update-existing :style strip-nil-vals)
      (update-existing :on strip-nil-vals)))

(defn namespace-hiccup [hiccup el-ns]
  (cond
    (string? hiccup) hiccup

    (map? (second hiccup))
    (assoc-in hiccup [1 ::ns] el-ns)

    :else
    (into [(first hiccup) {::ns el-ns}] (rest hiccup))))

(defn inflate-hiccup
  "Normalize hiccup form. Parses out class names and ids from the tag and returns
  a map of:

  - `:tag-name` - A string
  - `:attrs` - Parsed attributes
  - `:children` - A flattened list of children
  - `:ns` - Namespace for element (SVG)

  Some attributes receive special care:

  - `:classes` is a list of classes, extracted by parsing out dotted classes
    from the hiccup tag (e.g. \"heading\" in `:h1.heading`), as well as strings,
    keywords, or a collection of either from both `:class` and `:className`.
  - `:style` is a map of styles, even when the input hiccup provided a string
  - `:innerHTML` when this attribute is present, `:children` will be ignored

  ```clj
  (inflate-hiccup [:h1.heading \"Hello\"])
  ;;=>
  ;; {:tag-name \"h1\",
  ;;  :attrs {:classes (\"heading\")},
  ;;  :children [\"Heading\"]}
  ```"
  [hiccup]
  (let [inflated (hiccup/inflate hiccup)
        el-ns (or (::ns (:attrs inflated))
                  (when (= "svg" (:tag-name inflated))
                    "http://www.w3.org/2000/svg"))]
    (cond-> (update inflated :attrs prep-attributes)
      (:innerHTML (:attrs inflated)) (dissoc :children)
      el-ns (assoc :ns el-ns)
      
      (and el-ns (:children inflated))
      (update :children (fn [xs] (map #(namespace-hiccup % el-ns) xs))))))

(defn append-children [impl el children]
  (doseq [child children]
    (r/append-child (:renderer impl) el child))
  el)

(defn create-node
  "Create DOM node according to virtual DOM in `hiccup`. Register relevant
  life-cycle hooks from the new node or its descendants in `impl`. Returns
  the newly created node."
  [impl hiccup]
  (if (hiccup/hiccup? hiccup)
    (let [{:keys [tag-name attrs children ns]} (inflate-hiccup hiccup)
          children (mapv #(create-node impl %) children)
          node (r/create-element (:renderer impl) tag-name {:ns ns})]
      (update-attributes impl node attrs nil)
      (append-children impl node children)
      (register-hook impl node hiccup)
      node)
    (r/create-text-node (:renderer impl) (str hiccup))))

(defn safe-nth [xs n]
  (when (< n (count xs))
    (nth xs n)))

(defn same?
  "Two elements are considered the \"same\" if they are both hiccup elements with
  the same tag name and the same key (or both have no key) - or they are both
  strings.

  Sameness in this case indicates that the node can be used for reconciliation
  instead of creating a new node from scratch."
  [a b]
  (or (and (string? a) (string? b))
      (and (= (hiccup/get-tag-name a) (hiccup/get-tag-name b))
           (= (get-in a [1 :key])
              (get-in b [1 :key])))))

(defn changed?
  "Returns `true` when nodes have changed in such a way that a new node should be
  created. `changed?` is not the strict complement of `same?`, because it does
  not consider any two strings the same - only the exact same string."
  [new old]
  (or (not= (type old) (type new))
      (and (string? old) (not= new old))
      (not= (hiccup/get-tag-name old) (hiccup/get-tag-name new))))

(defn get-position-shifts
  "Given two sequences, return a `(count as)` long list of tuples of
  `[position-in-a position-in-b]`. If all the tuples contain the same number, no
  element in `as` has changed place in `bs`. A `nil` in the right position
  indicates that the element in `as` was removed from `bs`. Additional items at
  the end of `bs` is not accounted for.

  `f` is a function that can determine if `a` and `b` are the same element."
  [f as bs]
  (loop [positions (range (count bs))
         as as
         i 0
         res []]
    (if-let [a (first as)]
      (let [idx (loop [pos positions]
                  (when-let [n (first pos)]
                    (if (f a (nth bs n))
                      n
                      (recur (next pos)))))]
        (recur (remove #{idx} positions) (next as) (inc i) (conj res [i idx])))
      res)))

(defn same-pos? [[new-pos old-pos]]
  (= new-pos old-pos))

(defn get-next-ref
  "Find the next position in `shifts` where the new and old node positions are the
  same. Returns `[i node]` where `i` is the number of steps until the position
  is reached, and `node` is the node at the position. If `ref-i` is positive,
  returns existing `ref`."
  [shifts ref-i ref-node]
  (if (< 0 ref-i)
    [ref-i ref-node]
    (let [i (count (take-while (complement same-pos?) shifts))]
      [i (nth (first (drop i shifts)) 2)])))

(defn reorder-children
  "Reorders child nodes in `el` according to `shifts`. Only moves nodes that have
  moved. Tries to conserve the number of move operations to the minimum required
  ones (assuming mutable implementations of insert-before and append-child)."
  [impl el shifts new old]
  (loop [;; Pick up the child nodes before we start to move them, otherwise the
         ;; indexes in `shifts` will lead us to the wrong nodes. Such DOM, much
         ;; mutation, wow. Also: `mapv` because we can't afford this to be
         ;; lazy (it needs to happen now), and `seq` because we want `shifts` to
         ;; be `nil` if it's empty (to terminate the loop)
         shifts (seq (mapv #(conj % (r/get-child (:renderer impl) el (second %))) shifts))
         ref-node nil
         ref-i 0
         changed? false]
    (cond
      (nil? shifts)
      {:changed? changed?}

      (same-pos? (first shifts))
      (recur (next shifts) ref-node (dec ref-i) changed?)

      :else
      (let [[ni oi node] (first shifts)
            ;; ref-node is the next node that isn't moving
            [ref-i ref-node] (get-next-ref shifts ref-i ref-node)
            shifts (next shifts)]
        (if (= [oi ni] (take 2 (first shifts)))
          ;; Node is swapping places with its next sibling. A single DOM
          ;; operation will suffice, but both nodes will receive a hook, since
          ;; they both end up at new positions (could affect CSS, etc).
          (let [el-a (r/get-child (:renderer impl) el ni)
                el-b (r/get-child (:renderer impl) el oi)]
            (r/insert-before (:renderer impl) el el-b el-a)
            (register-hook impl el-a (get-in new [:children ni]) (get-in old [:children oi]) [:replicant/swap-node])
            (register-hook impl el-b (get-in new [:children oi]) (get-in old [:children ni]) [:replicant/swap-node])
            (recur (next shifts) ref-node (- ref-i 2) true))
          (do
            (if ref-node
              (r/insert-before (:renderer impl) el node ref-node)
              (r/append-child (:renderer impl) el node))
            (register-hook impl node (get-in new [:children ni]) (get-in old [:children oi]) [:replicant/move-node])
            (recur shifts ref-node (dec ref-i) true)))))))

(defn insert-before [xs x i]
  (concat (take i xs) [x] (drop i xs)))

(defn create-new-children
  "Find positions in the child nodes in `new` that didn't exist in `old`. These
  need new DOM nodes. By filling these first, we can avoid making unnecessary
  moves when we try to reorder children. Imagine this scenario:

  ```clj
  [:ul
    [:li \"#1\"]
    [:li \"#2\"]]

  ;; =>

  [:ul
    [:li \"#0\"]
    [:li \"#1\"]
    [:li \"#2\"]]
  ```

  By first creating and inserting the #0 node, a new check for position shifts
  will not find any further moves to be necessary. If we did not create the new
  node first, `reorder-children` would have moved every node one step down."
  [impl el new old]
  (let [shifts (get-position-shifts same? (:children new) (:children old))
        n (count (:children old))
        new-positions (->> (filter (comp nil? second) shifts)
                           (map-indexed (fn [idx [new-pos]]
                                          ;; For each child inserted, the total
                                          ;; number of children in the parent
                                          ;; will be one higher. We need this
                                          ;; number to know the difference
                                          ;; between insert-before and
                                          ;; append-child
                                          [new-pos (+ idx n)])))]
    (if (seq new-positions)
      (do
        ;; Create and insert child nodes that did not exist in old vdom
        (doseq [[pos child-n] new-positions]
          (let [vdom (nth (:children new) pos)
                node (create-node impl vdom)]
            (if (<= child-n pos)
              (r/append-child (:renderer impl) el node)
              (r/insert-before (:renderer impl) el node (r/get-child (:renderer impl) el pos)))))
        (let [;; Place new vdom entries in the corresponding places in the old
              ;; vdom, since these are now reconciled. This avoids further
              ;; reconciliation of the child nodes.
              old (update old :children
                          (fn [children]
                            (reduce
                             #(insert-before %1 (nth (:children new) %2) %2)
                             children
                             (map first new-positions))))]
          {:shifts (get-position-shifts same? (:children new) (:children old))
           :old old
           :changed? true}))
      {:shifts shifts
       :old old
       :changed? false})))

;; reconcile* and update-children are mutually recursive
(declare reconcile*)

(defn update-children
  "Update the children in the DOM `el` - and register corresponding life-cycle
  hooks to call in `impl` - by diffing the children of `new` and `old`. Tries to
  perform as few DOM operations as possible:

  1. Insert new nodes, if any
  2. Move nodes
  3. Reconcile old nodes (e.g. update their attributes, children, etc) with new
     vdom"
  [impl el new old]
  (let [{:keys [shifts old] :as created} (create-new-children impl el new old)
        reordered (reorder-children impl el shifts new old)]
    (doseq [idx (->> (max (count (:children old))
                          (count (:children new)))
                     range
                     reverse)]
      (reconcile*
       impl
       el
       (safe-nth (:children new) idx)
       (safe-nth (:children old) (get-in shifts [idx 1] idx))
       {:index idx}))
    {:changed? (or (:changed? created) (:changed? reordered))}))

(defn reconcile* [impl el new old {:keys [index]}]
  (cond
    (= new old)
    nil

    (nil? new)
    (let [child (r/get-child (:renderer impl) el index)]
      (r/remove-child (:renderer impl) el child)
      (register-hook impl child new old))

    ;; The node at this index is of a different type than before, replace it
    ;; with a fresh one. Use keys to avoid ending up here.
    (changed? new old)
    (let [node (create-node impl new)]
      (r/replace-child (:renderer impl) el node (r/get-child (:renderer impl) el index)))

    ;; Update the node's attributes and reconcile its children
    (not (string? new))
    (let [old* (inflate-hiccup old)
          new* (inflate-hiccup new)
          child (r/get-child (:renderer impl) el index)
          post-attrs (update-attributes impl child (:attrs new*) (:attrs old*))
          post-children (update-children impl child new* old*)
          attrs-changed? (or (:changed? post-attrs)
                             (not= (:replicant/on-update (second new))
                                   (:replicant/on-update (second old))))]
      (->> [(when attrs-changed? :replicant/updated-attrs)
            (when (:changed? post-children) :replicant/updated-children)]
           (remove nil?)
           (register-hook impl child new old)))))

(defn call-hooks
  "Call the lifecycle hooks gathered during reconciliation."
  [[hook node new old details]]
  (let [f (get-life-cycle-hook hook)]
    (f (cond-> {:replicant/event :replicant.event/life-cycle
                :replicant/life-cycle
                (cond
                  (nil? old) :replicant/mount
                  (nil? new) :replicant/unmount
                  :else :replicant/update)
                :replicant/node node}
         details (assoc :replicant/details details)))))

(defn reconcile
  "Reconcile the DOM in `el` by diffing the `new` hiccup with the `old` hiccup. If
  there is no `old` hiccup, `reconcile` will create the DOM as per `new`.
  Assumes that the DOM in `el` is in sync with `old` - if not, this will
  certainly not produce the desired result."
  [renderer el new & [old]]
  (let [impl {:renderer renderer
              :hooks (atom [])}]
    (if (nil? old)
      (r/append-child renderer el (create-node impl new))
      (reconcile* impl el new old {:index 0}))
    (let [hooks @(:hooks impl)]
      (doseq [hook hooks]
        (call-hooks hook))
      {:hooks hooks})))
