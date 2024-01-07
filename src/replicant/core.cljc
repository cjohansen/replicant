(ns replicant.core
  "To postpone as much processing as possible, Replicant uses three separate
  representations of the DOM:

  ## hiccup

  This is whatever the consumer uses to express the DOM structure of their
  components. This format is very permissive and is designed to be convenient to
  work with. In hiccup, the attribute map is optional, tag names are keywords,
  and can even include id and classes, like a CSS selector. Because of its
  leniency, replicant must process it to work with it.

  ## hiccup \"headers\"

  Hiccup \"headers\" is a partially processed version of the hiccup. It gives
  access to the string tag name, and key, if any. This version is used to make
  decisions about the hiccup being rendered - is it an update to existing nodes
  or new nodes, etc.

  For performance, hiccup headers is a positional tuple, and in CLJS even a
  native array. Individual values are accessed through some macros for
  readability, whithout sacrificing performance.

  The tuple contains raw hiccup children. `get-children` returns a structured,
  flattened representation of all children as hiccup headers.

  ## vdom

  vdom is the fully parsed representation. This format is only used for
  previously rendered hiccup. Hiccup must be fully processed to actually be
  rendered, and Replicant keeps the previously rendered vdom around to speed up
  subsequent renders.

  vdom is another positional tuple (and native JS array in CLJS), and has
  similar macro accessors as the hiccup headers."
  (:require [replicant.hiccup :as hiccup]
            [replicant.protocols :as r]
            [replicant.vdom :as vdom]))

;; Hiccup stuff

#_(set! *warn-on-reflection* true)
#_(set! *unchecked-math* :warn-on-boxed)

(defn hiccup? [sexp]
  (and (vector? sexp)
       (not (map-entry? sexp))
       (keyword? (first sexp))))

(defn parse-tag [^String tag]
  ;; Borrowed from hiccup, and adapted to support multiple classes
  (let [id-index (let [index (.indexOf tag "#")] (when (pos? index) index))
        class-index (let [index (.indexOf tag ".")] (when (pos? index) index))
        tag-name (cond
                   id-index (.substring tag 0 id-index)
                   class-index (.substring tag 0 class-index)
                   :else tag)
        id (when id-index
             (if class-index
               (.substring tag (unchecked-inc-int id-index) class-index)
               (.substring tag (unchecked-inc-int id-index))))
        classes (when class-index
                  (seq (.split (.substring tag (unchecked-inc-int class-index)) #?(:clj "\\." :cljs "."))))]
    #?(:clj [tag-name id classes]
       :cljs #js [tag-name id classes])))

(defn get-hiccup-headers
  "Hiccup symbols can include tag name, id and classes. The argument map is
  optional. This function finds the important bits of the hiccup data structure
  and returns a \"headers\" tuple with a stable position for:

  - tag-name
  - id from the hiccup symbol
  - classes from the hiccup symbol
  - key
  - attributes
  - children
  - namespace
  - original s-expression

  Attributes and children are completely untouched. Headers can be used to
  quickly determine tag name and key, or sent to `get-attrs` and
  `get-children` for usable information about those things.

  Returns a tuple (instead of a map) for speed."
  [sexp ns]
  (when sexp
    (if (hiccup? sexp)
      (let [sym (first sexp)
            args (rest sexp)
            has-args? (map? (first args))
            attrs (if has-args? (first args) {})]
        (hiccup/create (parse-tag (name sym)) attrs (if has-args? (rest args) args) ns sexp nil))
      (let [s (str sexp)]
        (hiccup/create
         #?(:clj [nil nil nil]
            :cljs #js [nil nil nil])
         nil nil nil s s)))))

(defn get-classes [classes]
  (cond
    (keyword? classes) [(name classes)]
    (empty? classes) []
    (coll? classes) (keep (fn [class]
                            (if (keyword? class)
                              (name class)
                              (not-empty (.trim class))))
                         classes)
    (string? classes) (keep #(not-empty (.trim ^String %)) (.split ^String classes " "))
    :else (throw (ex-info "class name is neither string, keyword, or a collection of those"
                          {:classes classes}))))

(def skip-pixelize-attrs
  #{:animation-iteration-count
    :box-flex
    :box-flex-group
    :box-ordinal-group
    :column-count
    :fill-opacity
    :flex
    :flex-grow
    :flex-positive
    :flex-shrink
    :flex-negative
    :flex-order
    :font-weight
    :line-clamp
    :line-height
    :opacity
    :order
    :orphans
    :stop-opacity
    :stroke-dashoffset
    :stroke-opacity
    :stroke-width
    :tab-size
    :widows
    :z-index
    :zoom})

(defn explode-styles
  "Converts string values for the style attribute to a map of keyword keys and
  string values."
  [^String s]
  (->> (.split s ";")
       (map (fn [^String kv]
              (let [[k v] (map #(.trim ^String %) (.split kv ":"))]
                [(keyword k) v])))
       (into {})))

(defn get-style-val [attr v]
  (if (number? v)
    (if (skip-pixelize-attrs attr)
      (str v)
      (str v "px"))
    v))

(defn prep-attrs [attrs id classes]
  (let [classes (concat (get-classes (:class attrs)) classes)]
    (cond-> (dissoc attrs :class :replicant/mounting :replicant/unmounting)
      id (assoc :id id)
      (seq classes) (assoc :classes classes)
      (string? (:style attrs)) (update :style explode-styles))))

(defn get-attrs
  "Given `headers` as produced by `get-hiccup-headers`, returns a map of all HTML
  attributes."
  [headers]
  (prep-attrs (hiccup/attrs headers) (hiccup/id headers) (hiccup/classes headers)))

(defn merge-attrs [attrs overrides]
  (cond-> (merge attrs (dissoc overrides :style))
    (or (:style attrs)
        (:style overrides))
    (update :style merge (:style overrides))))

(defn get-mounting-attrs [headers]
  (if-let [mounting (:replicant/mounting (hiccup/attrs headers))]
    [(get-attrs headers)
     (get-attrs (cond-> headers
                  mounting (hiccup/update-attrs merge-attrs mounting)))]
    [(get-attrs headers)]))

(defn get-unmounting-attrs [vdom]
  (when (vdom/async-unmount? vdom)
    (let [attrs (nth (vdom/sexp vdom) 1)
          unmounting (:replicant/unmounting attrs)]
      (prep-attrs (merge-attrs attrs unmounting) nil (vdom/classes vdom)))))

(defn ^:private flatten-seqs* [xs coll]
  (reduce
   (fn [_ x]
     (cond (nil? x) nil
           (seq? x) (flatten-seqs* x coll)
           :else (conj! coll x)))
   nil xs))

(defn flatten-seqs [xs]
  (let [coll (transient [])]
    (flatten-seqs* xs coll)
    (persistent! coll)))

(defn get-children
  "Given an optional tag namespace `ns` (e.g. for SVG nodes) and `headers`, as
  produced by `get-hiccup-headers`, returns a flat collection of children as
  \"hiccup headers\". Children will carry the `ns`, if any."
  [headers ns]
  (when-not (:innerHTML (hiccup/attrs headers))
    (->> (hiccup/children headers)
         flatten-seqs
         (mapv #(get-hiccup-headers % ns)))))

(defn get-children-ks
  "Like `get-children` but returns a tuple of `[children ks]` where `ks` is a set
  of the keys in `children`."
  [headers ns]
  (when-not (:innerHTML (hiccup/attrs headers))
    (let [[children ks]
          (->> (hiccup/children headers)
               flatten-seqs
               (reduce (fn bla [[children ks] hiccup]
                         (let [headers (get-hiccup-headers hiccup ns)
                               k (hiccup/rkey headers)]
                           [(conj! children headers)
                            (cond-> ks k (conj! k))]))
                       [(transient []) (transient #{})]))]
      [(persistent! children) (persistent! ks)])))

;; Events and life cycle hooks

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

(defn call-hook [[hook node new old details]]
  (let [f (get-life-cycle-hook hook)]
    (f (cond-> {:replicant/event :replicant.event/life-cycle
                :replicant/life-cycle
                (cond
                  (nil? old) :replicant/mount
                  (nil? new) :replicant/unmount
                  :else :replicant/update)
                :replicant/node node}
         details (assoc :replicant/details details)))))

(defn register-hook
  "Register the life-cycle hook from the corresponding virtual DOM node to call in
  `impl`, if any. The only time the hook in `old` is used is when `new` is
  `nil`, which means the node is unmounting. `details` is a vector of keywords
  that provide some detail about why the hook is invoked."
  [{:keys [hooks]} node headers & [vdom details]]
  (when-let [hook (:replicant/on-update (if headers (hiccup/attrs headers) (vdom/attrs vdom)))]
    (vswap! hooks conj [hook node (hiccup/sexp headers) (vdom/sexp vdom) details])))

(defn register-mount [{:keys [mounts]} node mounting-attrs attrs]
  (vswap! mounts conj [node mounting-attrs attrs]))

;; Perform DOM operations

(defn update-styles [renderer el new-styles old-styles]
  (run!
   #(let [new-style (% new-styles)]
      (cond
        (nil? new-style)
        (r/remove-style renderer el %)

        (not= new-style (% old-styles))
        (r/set-style renderer el % (get-style-val % new-style))))
   (into (set (keys new-styles)) (keys old-styles))))

(defn update-classes [renderer el new-classes old-classes]
  (->> (remove (set new-classes) old-classes)
       (run! #(r/remove-class renderer el %)))
  (->> (remove (set old-classes) new-classes)
       (run! #(r/add-class renderer el %))))

(defn add-event-listeners [renderer el val]
  (->> val
       (remove (comp nil? second))
       (run! (fn [[event handler]]
               (when-let [handler (get-event-handler handler event)]
                 (r/set-event-handler renderer el event handler))))))

(defn update-event-listeners [renderer el new-handlers old-handlers]
  (->> (remove (set (filter new-handlers (keys new-handlers)))
               (filter old-handlers (keys old-handlers)))
       (run! #(r/remove-event-handler renderer el %)))
  (->> (remove #(= (val %) (get old-handlers (key %))) new-handlers)
       (add-event-listeners renderer el)))

(def xlinkns "http://www.w3.org/1999/xlink")
(def xmlns "http://www.w3.org/XML/1998/namespace")

(defn set-attr-val [renderer el attr v]
  (let [an (name attr)]
    (->> (cond-> {}
           (= 0 (.indexOf an "xml:"))
           (assoc :ns xmlns)

           (= 0 (.indexOf an "xlink:"))
           (assoc :ns xlinkns))
         (r/set-attribute renderer el an v))))

(defn update-attr [renderer el attr new old]
  (case attr
    :replicant/key nil
    :replicant/on-update nil
    :style (update-styles renderer el (:style new) (:style old))
    :classes (update-classes renderer el (:classes new) (:classes old))
    :on (update-event-listeners renderer el (:on new) (:on old))
    (if-let [v (attr new)]
      (when (not= v (attr old))
        (set-attr-val renderer el attr v))
      (r/remove-attribute renderer el (name attr)))))

(defn update-attributes [renderer el new-attrs old-attrs]
  (->> (into (set (keys new-attrs)) (keys old-attrs))
       (reduce #(update-attr renderer el %2 new-attrs old-attrs) nil)))

(defn reconcile-attributes [renderer el new-attrs old-attrs]
  (if (= new-attrs old-attrs)
    false
    (do
      (update-attributes renderer el new-attrs old-attrs)
      true)))

;; These setters are not strictly necessary - you could just call the update-*
;; functions with `nil` for `old`. The pure setters improve performance for
;; `create-node`

(defn set-styles [renderer el new-styles]
  (->> (keys new-styles)
       (filter new-styles)
       (run! #(r/set-style renderer el % (get-style-val % (% new-styles))))))

(defn set-classes [renderer el new-classes]
  (->> new-classes
       (run! #(r/add-class renderer el %))))

(defn set-event-listeners [renderer el new-handlers]
  (add-event-listeners renderer el new-handlers))

(defn set-attr [renderer el attr new]
  (case attr
    :replicant/key nil
    :replicant/on-update nil
    :style (set-styles renderer el (:style new))
    :classes (set-classes renderer el (:classes new))
    :on (set-event-listeners renderer el (:on new))
    (set-attr-val renderer el attr (attr new))))

(defn set-attributes [renderer el new-attrs]
  (->> (keys new-attrs)
       (filter new-attrs)
       (run! #(set-attr renderer el % new-attrs))))

(defn create-node
  "Create DOM node according to virtual DOM in `headers`. Register relevant
  life-cycle hooks from the new node or its descendants in `impl`. Returns a
  tuple of the newly created node and the fully realized vdom."
  [{:keys [renderer] :as impl} headers]
  (if-let [text (hiccup/text headers)]
    [(r/create-text-node renderer text)
     (vdom/create-text-node text)]
    (let [tag-name (hiccup/tag-name headers)
          ns (or (hiccup/html-ns headers)
                 (when (= "svg" tag-name)
                   "http://www.w3.org/2000/svg"))
          node (r/create-element renderer tag-name (when ns {:ns ns}))
          [attrs mounting-attrs] (get-mounting-attrs headers)
          _ (set-attributes renderer node (or mounting-attrs attrs))
          [children ks] (->> (get-children headers ns)
                             (reduce (fn [[children ks] child-headers]
                                       (let [[child-node vdom] (create-node impl child-headers)
                                             k (vdom/rkey vdom)]
                                         (r/append-child renderer node child-node)
                                         [(conj! children vdom) (cond-> ks k (conj! k))]))
                                     [(transient []) (transient #{})]))]
      (register-hook impl node headers)
      (when mounting-attrs
        (register-mount impl node mounting-attrs attrs))
      [node (vdom/from-hiccup headers attrs (persistent! children) (persistent! ks))])))

(defn reusable?
  "Two elements are considered the similar enough for reuse if they are both
  hiccup elements with the same tag name and the same key (or both have no key)
  - or they are both strings.

  Similarity in this case indicates that the node can be used for reconciliation
  instead of creating a new node from scratch."
  [headers vdom]
  (or (and (hiccup/text headers) (vdom/text vdom))
      (and (= (hiccup/rkey headers) (vdom/rkey vdom))
           (= (hiccup/tag-name headers) (vdom/tag-name vdom)))))

;; reconcile* and update-children are mutually recursive
(declare reconcile*)

(defn index-of [f xs]
  (loop [n 0
         xs (seq xs)]
    (cond
      (nil? xs) -1
      (f (first xs)) n
      :else (recur (unchecked-inc-int n) (next xs)))))

(defn get-ns [headers]
  (or (hiccup/html-ns headers)
      (when (= "svg" (hiccup/tag-name headers))
        "http://www.w3.org/2000/svg")))

(defn ^:private insert-children [{:keys [renderer] :as impl} el children vdom]
  (->> (reduce (fn [res child]
                 (let [[node vdom] (create-node impl child)]
                   (r/append-child renderer el node)
                   (conj! res vdom)))
               vdom children)
       persistent!))

(defn remove-child [{:keys [renderer] :as impl} unmounts el n vdom]
  ;; An assigned id means the node has already started unmounting
  (if-let [id (vdom/unmount-id vdom)]
    ;; If the id is in the unmounts set, it has not yet finished unmounting
    (when (unmounts id)
      vdom)
    (let [res (if-let [attrs (get-unmounting-attrs vdom)]
                ;; The node has unmounting attributes: mark it as unmounting,
                ;; and start the process
                (let [marked-vdom (vdom/mark-unmounting vdom)
                      child (r/get-child renderer el n)]
                  (update-attributes renderer child attrs (vdom/attrs vdom))
                  ;; Record the node as unmounting
                  (vswap! (:unmounts impl) conj (vdom/unmount-id vdom))
                  (->> (fn []
                         ;; We're done, remove it from the set of unmounting
                         ;; nodes
                         (vswap! (:unmounts impl) disj (vdom/unmount-id vdom))
                         (r/remove-child renderer el child)
                         (when-let [hook (:replicant/on-update (vdom/attrs vdom))]
                           (call-hook [hook child nil vdom]))
                         renderer)
                       (r/on-transition-end renderer child))
                  marked-vdom)
                (let [child (r/get-child renderer el n)]
                  (r/remove-child renderer el child)
                  (register-hook impl child nil vdom)
                  nil))]
      res)))

(def move-node-details [:replicant/move-node])

(defn unchanged? [headers vdom]
  (= (hiccup/sexp headers) (vdom/sexp vdom)))

(defn ^:private move-nodes [{:keys [renderer] :as impl} el headers new-children vdom old-children n n-children]
  (let [o-idx (if (hiccup/rkey headers)
                (int (index-of #(reusable? headers %) old-children))
                -1)
        n-idx (if (vdom/rkey vdom)
                (int (index-of #(reusable? % vdom) new-children))
                -1)]
    (if (< o-idx n-idx)
      ;; The new node needs to be moved back
      ;;
      ;; Old: 1 2 3
      ;; New: 2 3 1
      ;;
      ;; vdom: 1, n-idx: 2
      ;; headers: 2, o-idx: 1
      ;;
      ;; The old node is now at the end, move it there and continue. It will be
      ;; reconciled when the loop reaches it.
      ;;
      ;; append-child 0
      ;; Old: 2 3 1
      ;; New: 2 3 1
      (let [idx (unchecked-inc-int (unchecked-add-int n n-idx))
            child (r/get-child renderer el n)]
        (if (< idx n-children)
          (r/insert-before renderer el child (r/get-child renderer el idx))
          (r/append-child renderer el child))
        (register-hook impl child (nth new-children n-idx) vdom move-node-details)
        [new-children
         (concat (take n-idx (next old-children)) [(first old-children)] (drop (unchecked-inc-int n-idx) old-children))
         n
         (unchecked-dec-int idx)])

      ;; The new node needs to be brought to the front
      ;;
      ;; Old: 1 2 3
      ;; New: 3 1 2
      ;;
      ;; vdom: 1, n-idx: 1
      ;; headers: 3, o-idx: 2
      ;;
      ;; The new node used to be at the end, move it to the front and reconcile
      ;; it, then continue with the rest of the nodes.
      ;;
      ;; insert-before 3 1
      ;; Old: 1 2
      ;; New: 1 2
      (let [idx (unchecked-add-int n o-idx)
            child (r/get-child renderer el idx)
            corresponding-old-vdom (nth old-children o-idx)]
        (r/insert-before renderer el child (r/get-child renderer el n))
        (reconcile* impl el headers corresponding-old-vdom n)
        (when (unchanged? headers corresponding-old-vdom)
          ;; If it didn't change, reconcile* did not schedule a hook
          ;; Because the node just moved we still need the hook
          (register-hook impl child headers corresponding-old-vdom move-node-details))
        [(next new-children)
         (concat (take o-idx old-children) (drop (unchecked-inc-int o-idx) old-children))
         (unchecked-inc-int n)
         (unchecked-inc-int (unchecked-add-int n o-idx))
         corresponding-old-vdom]))))

(defn update-children [impl el new-children new-ks old-children old-ks]
  (let [r (:renderer impl)
        unmounts @(:unmounts impl)]
    (loop [new-c (seq new-children)
           old-c (seq old-children)
           n 0
           move-n 0
           n-children (count old-children)
           changed? false
           vdom (transient [])]
      (let [new-headers (first new-c)
            old-vdom (first old-c)
            new-empty? (nil? new-c)
            old-empty? (nil? old-c)]
        (cond
          ;; Both empty, we're done
          (and new-empty? old-empty?)
          [changed? (persistent! vdom) new-ks]

          ;; There are old nodes where there are no new nodes: delete
          new-empty?
          [true
           (->> (reduce
                 (fn [[vd n] c]
                   (if-let [pending-vdom (remove-child impl unmounts el n c)]
                     [(conj! vd pending-vdom) (unchecked-inc-int n)]
                     [vd n]))
                 [vdom n]
                 old-c)
                first
                persistent!)
           new-ks]

          ;; There are new nodes where there were no old ones: create
          old-empty?
          [true (insert-children impl el new-c vdom) new-ks]

          ;; Old node is already on its way out from a previous render
          (vdom/unmount-id old-vdom)
          (if (unmounts (vdom/unmount-id old-vdom))
            ;; Still unmounting
            (recur new-c (next old-c) (unchecked-inc-int n) move-n n-children changed? (conj! vdom old-vdom))
            ;; It's gone!
            (recur new-c (next old-c) n (unchecked-dec-int move-n) (unchecked-dec-int n-children) changed? vdom))

          ;; It's a reusable node, reconcile
          (reusable? new-headers old-vdom)
          (let [new-vdom (reconcile* impl el new-headers old-vdom n)
                node-unchanged? (unchanged? new-headers old-vdom)]
            (when (and node-unchanged? (< n move-n))
              (register-hook impl (r/get-child r el n) new-headers old-vdom move-node-details))
            (recur (next new-c) (next old-c) (unchecked-inc-int n) move-n n-children (or changed? (not node-unchanged?)) (conj! vdom new-vdom)))

          ;; New node did not previously exist, create it
          (not (old-ks (hiccup/rkey new-headers)))
          (let [[child child-vdom] (create-node impl new-headers)]
            (if (<= n-children n)
              (r/append-child r el child)
              (r/insert-before r el child (r/get-child r el n)))
            (recur (next new-c) old-c (unchecked-inc-int n) move-n (unchecked-inc-int n-children) true (conj! vdom child-vdom)))

          ;; Old node no longer exists, remove it
          (not (new-ks (vdom/rkey old-vdom)))
          (if-let [unmounting-node (remove-child impl unmounts el n old-vdom)]
            (recur new-c (next old-c) (unchecked-inc-int n) move-n n-children true (conj! vdom unmounting-node))
            (recur new-c (next old-c) n move-n (unchecked-dec-int move-n) true vdom))

          ;; Node has moved
          :else
          (let [[nc oc n move-n vdom-node] (move-nodes impl el new-headers new-c old-vdom old-c n n-children)]
            (recur nc oc n move-n n-children true (cond-> vdom vdom-node (conj! vdom-node)))))))))

(defn wipe? [old-children old-ks new-children new-ks]
  (let [oc (count old-children)]
    (and (when (< 0 oc)
           (or
            ;; No new children, remove any existing ones
            (= 0 (count new-children))

            (and
             ;; All the old children have keys, and none of those keys are in use in the
             ;; new children: remove them all
             (= oc (count old-ks))
             (nil? (loop [[k & ks] old-ks]
                     (cond
                       (nil? k) nil
                       (new-ks k) k
                       :else (recur ks)))))))
         (loop [xs (seq old-children)]
           (cond
             (nil? xs) true
             (vdom/async-unmount? (first xs)) false
             :else (recur (next xs)))))))

(defn reconcile* [{:keys [renderer] :as impl} el headers vdom index]
  (cond
    (unchanged? headers vdom)
    vdom

    ;; Replace the text node at this index with a new one
    (not= (hiccup/text headers) (vdom/text vdom))
    (let [[node vdom] (create-node impl headers)]
      (r/replace-child renderer el node (r/get-child renderer el index))
      vdom)

    ;; Update the node's attributes and reconcile its children
    :else
    (let [child (r/get-child renderer el index)
          attrs (get-attrs headers)
          attrs-changed? (reconcile-attributes renderer child attrs (vdom/attrs vdom))
          [new-children new-ks] (get-children-ks headers (get-ns headers))
          [children-changed? children child-ks] (update-children impl child new-children new-ks (vdom/children vdom) (vdom/child-ks vdom))
          attrs-changed? (or attrs-changed?
                             (not= (:replicant/on-update (hiccup/attrs headers))
                                   (:replicant/on-update (vdom/attrs vdom))))]
      (->> (cond
             (and attrs-changed? children-changed?)
             [:replicant/updated-attrs
              :replicant/updated-children]

             attrs-changed?
             [:replicant/updated-attrs]

             :else
             [:replicant/updated-children])
           (register-hook impl child headers vdom))
      (vdom/from-hiccup headers attrs children child-ks))))

(defn perform-post-mount-update [renderer [node mounting-attrs attrs]]
  (update-attributes renderer node attrs mounting-attrs))

(defn reconcile
  "Reconcile the DOM in `el` by diffing `hiccup` with `vdom`. If there is no
  `vdom`, `reconcile` will create the DOM as per `hiccup`. Assumes that the DOM
  in `el` is in sync with `vdom` - if not, this will certainly not produce the
  desired result."
  [renderer el hiccup & [vdom unmounts]]
  (let [impl {:renderer renderer
              :hooks (volatile! [])
              :mounts (volatile! [])
              :unmounts (or unmounts (volatile! #{}))}
        vdom (let [headers (get-hiccup-headers hiccup nil)]
               ;; Not strictly necessary, but it makes noop renders faster
               (if (and headers vdom (unchanged? headers (first vdom)) (= 1 (count vdom)))
                 vdom
                 (let [k (hiccup/rkey headers)]
                   (-> (update-children
                        impl el
                        (when headers [headers])
                        (set (keep #(vdom/rkey %) vdom))
                        vdom
                        (cond-> #{} k (conj k)))
                       ;; second, because update-children returns [changed? children]
                       second))))
        hooks @(:hooks impl)]
    (if-let [mounts (seq @(:mounts impl))]
      (->> (fn []
             (run! #(perform-post-mount-update renderer %) mounts)
             (run! call-hook hooks))
           (r/next-frame renderer))
      (run! call-hook hooks))
    {:hooks hooks
     :vdom vdom
     :unmounts (:unmounts impl)}))
