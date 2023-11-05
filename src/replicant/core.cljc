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
  `hooks`, if any. The only time the hook in `old` is used is when `new` is
  `nil`, which means the node is unmounting. `details` is a vector of keywords
  that provide some detail about why the hook is invoked."
  [hooks node new & [old details]]
  (let [hook (:replicant/on-update (if new (second new) (second old)))]
    (cond-> hooks
      hook (conj [hook node new old details]))))

(defn update-styles [el new-styles old-styles]
  (loop [el el
         ks (seq (into (set (keys new-styles)) (keys old-styles)))]
    (if (nil? ks)
      el
      (let [k (first ks)
            new-style (k new-styles)]
        (recur
         (cond
           (nil? new-style)
           (r/remove-style el k)

           (not= new-style (k old-styles))
           (r/set-style el k new-style)

           :else el)
         (next ks))))))

(defn update-classes [el new-classes old-classes]
  (let [el (->> (remove (set new-classes) old-classes)
                (reduce #(r/remove-class %1 %2) el))]
    (->> (remove (set old-classes) new-classes)
         (reduce #(r/add-class %1 %2) el))))

(defn add-event-listeners [el val]
  (reduce (fn [el [event handler]]
            (let [handler (get-event-handler handler event)]
              (cond-> el
                handler (r/set-event-handler event handler))))
          el val))

(defn update-event-listeners [el new-handlers old-handlers]
  (let [old-keys (keys old-handlers)
        el (->> (remove (set (keys new-handlers)) old-keys)
                (reduce #(r/remove-event-handler %1 %2) el))]
    (->> (remove #(= (val %) (get old-handlers (key %))) new-handlers)
         (add-event-listeners el))))

(def xlinkns "http://www.w3.org/1999/xlink")
(def xmlns "http://www.w3.org/XML/1998/namespace")

(defn update-attr [el attr new old]
  (case attr
    :style (update-styles el (:style new) (:style old))
    :classes (update-classes el (:classes new) (:classes old))
    :on (update-event-listeners el (:on new) (:on old))
    (if-let [v (attr new)]
      (if (= v (attr old))
        el
        (let [an (name attr)]
          (->> (cond-> {}
                 (#{["x" "m" "l" ":"] ;; ClojureScript
                    [\x \m \l \:]} ;; Clojure
                  (take 3 an))
                 (assoc :ns xmlns)

                 (#{["x" "l" "i" "n" "k" ":"]
                    [\x \l \i \n \k \:]}
                  (take 6 an))
                 (assoc :ns xlinkns))
               (r/set-attribute el an v))))
      (r/remove-attribute el (name attr)))))

(defn update-attributes [el new-attrs old-attrs]
  {:el (reduce
        (fn [el attr]
          (update-attr el attr new-attrs old-attrs))
        el
        (into (set (keys new-attrs)) (keys old-attrs)))
   :changed? (not= new-attrs old-attrs)})

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

(defn append-children [el children]
  (reduce #(r/append-child %1 %2) el children))

(defn create-node
  "Create DOM node according to virtual DOM in `hiccup`. Register relevant
  life-cycle hooks from the new node or its descendants in `hooks`. Returns a
  map of `{:node :hooks}` - the newly created node and an updated list of
  hooks."
  [el hooks hiccup]
  (if (hiccup/hiccup? hiccup)
    (let [{:keys [tag-name attrs children ns]} (inflate-hiccup hiccup)
          {:keys [children hooks]}
          (->> children
               (reduce
                (fn [{:keys [hooks children]} vdom]
                  (let [{:keys [node hooks]} (create-node el hooks vdom)]
                    {:children (conj children node)
                     :hooks hooks}))
                {:hooks hooks :children []}))
          node (-> (r/create-element el tag-name {:ns ns})
                   (update-attributes attrs nil)
                   :el
                   (append-children children))]
      {:node node
       :hooks (register-hook hooks node hiccup)})
    {:node (r/create-text-node el (str hiccup))
     :hooks hooks}))

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
  [el hooks shifts new old]
  (loop [el el
         hooks hooks
         ;; Pick up the child nodes before we start to move them, otherwise the
         ;; indexes in `shifts` will lead us to the wrong nodes. Such DOM, much
         ;; mutation, wow. Also: `mapv` because we can't afford this to be
         ;; lazy (it needs to happen now), and `seq` because we want `shifts` to
         ;; be `nil` if it's empty (to terminate the loop)
         shifts (seq (mapv #(conj % (r/get-child el (second %))) shifts))
         ref-node nil
         ref-i 0
         changed? false]
    (cond
      (nil? shifts)
      {:el el :hooks hooks :changed? changed?}

      (same-pos? (first shifts))
      (recur el hooks (next shifts) ref-node (dec ref-i) changed?)

      :else
      (let [[ni oi node] (first shifts)
            ;; ref-node is the next node that isn't moving
            [ref-i ref-node] (get-next-ref shifts ref-i ref-node)
            shifts (next shifts)]
        (if (= [oi ni] (take 2 (first shifts)))
          ;; Node is swapping places with its next sibling. A single DOM
          ;; operation will suffice, but both nodes will receive a hook, since
          ;; they both end up at new positions (could affect CSS, etc).
          (let [el-a (r/get-child el ni)
                el-b (r/get-child el oi)]
            (recur
             (r/insert-before el el-b el-a)
             (-> hooks
                 (register-hook el-a (get-in new [:children ni]) (get-in old [:children oi]) [:replicant/swap-node])
                 (register-hook el-b (get-in new [:children oi]) (get-in old [:children ni]) [:replicant/swap-node]))
             (next shifts)
             ref-node
             (- ref-i 2)
             true))
          (recur (if ref-node
                   (r/insert-before el node ref-node)
                   (r/append-child el node))
                 (register-hook hooks node (get-in new [:children ni]) (get-in old [:children oi]) [:replicant/move-node])
                 shifts
                 ref-node
                 (dec ref-i)
                 true))))))

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
  [this new old]
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
      (let [;; Create and insert child nodes that did not exist in old vdom
            this (reduce (fn [{:keys [el hooks]} [pos child-n]]
                           (let [vdom (nth (:children new) pos)
                                 {:keys [node hooks]} (create-node el hooks vdom)]
                             {:hooks hooks
                              :el (if (<= child-n pos)
                                    (r/append-child el node)
                                    (r/insert-before el node (r/get-child el pos)))}))
                         this new-positions)
            ;; Place new vdom entries in the corresponding places in the old
            ;; vdom, since these are now reconciled. This avoids further
            ;; reconciliation of the child nodes.
            old (update old :children
                        (fn [children]
                          (reduce
                           #(insert-before %1 (nth (:children new) %2) %2)
                           children
                           (map first new-positions))))]
        (assoc this
               :shifts (get-position-shifts same? (:children new) (:children old))
               :old old
               :changed? true))
      (assoc this :shifts shifts :old old :changed? false))))

;; reconcile* and update-children are mutually recursive
(declare reconcile*)

(defn update-children
  "Update the children in the DOM `:el` in `this` - and add corresponding to
  life-cycle hooks to call in `:hooks` - by diffing the children of `new` and
  `old`. Tries to perform as few DOM operations as possible:

  1. Insert new nodes, if any
  2. Move nodes
  3. Reconcile old nodes (e.g. update their attributes, children, etc) with new
     vdom"
  [this new old]
  (let [{:keys [el hooks shifts old] :as created} (create-new-children this new old)
        {:keys [el hooks] :as reordered} (reorder-children el hooks shifts new old)]
    (assoc
     (->> (max (count (:children old))
               (count (:children new)))
          range
          reverse
          (reduce 
           #(reconcile*
             %1
             (safe-nth (:children new) %2)
             (safe-nth (:children old) (get-in shifts [%2 1] %2))
             {:index %2})
           {:el el
            :hooks hooks}))
     :changed? (or (:changed? created) (:changed? reordered)))))

(defn reconcile* [{:keys [el hooks] :as this} new old {:keys [index]}]
  (cond
    (= new old)
    this

    (nil? new)
    (let [child (r/get-child el index)]
      {:el (r/remove-child el child)
       :hooks (register-hook hooks child new old)})

    ;; The node at this index is of a different type than before, replace it
    ;; with a fresh one. Use keys to avoid ending up here.
    (changed? new old)
    (let [{:keys [node hooks]} (create-node el hooks new)]
      {:el (r/replace-child el node (r/get-child el index))
       :hooks hooks})

    ;; Update the node's attributes and reconcile its children
    (not (string? new))
    (let [old* (inflate-hiccup old)
          new* (inflate-hiccup new)
          child (r/get-child el index)
          post-attrs (update-attributes child (:attrs new*) (:attrs old*))
          post-children (update-children (assoc post-attrs :hooks hooks) new* old*)
          attrs-changed? (or (:changed? post-attrs)
                             (not= (:replicant/on-update (second new))
                                   (:replicant/on-update (second old))))]
      (-> post-children
          (update :el r/get-parent-node)
          (update :hooks register-hook child new old
                  (remove nil? [(when attrs-changed? :replicant/updated-attrs)
                                (when (:changed? post-children) :replicant/updated-children)]))))))

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
  [el new & [old]]
  (let [{:keys [el hooks]}
        (if (nil? old)
          (let [{:keys [node hooks]} (create-node el [] new)]
            {:el (r/append-child el node)
             :hooks hooks})
          (reconcile* {:el el :hooks []} new old {:index 0}))]
    (doseq [hook hooks]
      (call-hooks hook))
    {:el el :hooks hooks}))
