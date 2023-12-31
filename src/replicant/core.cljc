(ns replicant.core
  (:require [replicant.protocols :as r]))

;; Hiccup stuff

(def hiccup-tag-name 0)
(def hiccup-id 1)
(def hiccup-class 2)
(def hiccup-key 3)
(def hiccup-attrs 4)
(def hiccup-children 5)
(def hiccup-ns 6)

#_(set! *warn-on-reflection* true)
#_(set! *unchecked-math* :warn-on-boxed)

(defn hiccup? [sexp]
  (and (vector? sexp)
       (not (map-entry? sexp))
       (keyword? (first sexp))))

(defn parse-tag [^String tag]
  ;; Borrowed from hiccup, and adapted to support multiple classes
  (let [id-index (let [index (.indexOf tag "#")] (when (pos? index) index))
        class-index (let [index (.indexOf tag ".")] (when (pos? index) index))]
    [(cond
       id-index (.substring tag 0 id-index)
       class-index (.substring tag 0 class-index)
       :else tag)
     (when id-index
       (if class-index
         (.substring tag (unchecked-inc-int id-index) class-index)
         (.substring tag (unchecked-inc-int id-index))))
     (when class-index
       (seq (.split (.substring tag (unchecked-inc-int class-index)) #?(:clj "\\."
                                                                        :cljs "."))))]))

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

  Attributes and children are completely untouched. Headers can be used to
  quickly determine tag name and key, or sent to `get-attrs` and `get-children`
  for usable information about those things.

  Returns a tuple (instead of a map) for speed. The the above vars for indexed
  lookups, e.g.: `(nth headers hiccup-key)`"
  [sexp ns]
  (when sexp
    (if (hiccup? sexp)
      (let [sym (first sexp)
            args (rest sexp)
            has-args? (map? (first args))
            attrs (if has-args? (first args) {})]
        (-> (parse-tag (name sym))
            (conj (:key attrs))
            (conj attrs)
            (conj (if has-args? (rest args) args))
            (conj ns)))
      (str sexp))))

(defn get-classes [classes]
  (cond
    (empty? classes) []
    (coll? classes) (map (fn [class]
                           (if (keyword? class)
                             (name class)
                             (map (fn [^String s] (.trim  s)) (.split ^String class " "))))
                         classes)
    (keyword? classes) [(name classes)]
    (string? classes) (map #(.trim ^String %) (.split ^String classes " "))
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

(defn get-attrs
  "Given `headers` as produced by `get-hiccup-headers`, returns a map of all HTML
  attributes."
  [headers]
  (let [id (nth headers hiccup-id)
        attrs (nth headers hiccup-attrs)
        classes (concat
                 (remove empty? (get-classes (:class attrs)))
                 (nth headers hiccup-class))]
    (cond-> (dissoc attrs :key :replicant/on-update)
      id (assoc :id id)
      (seq classes) (assoc :classes classes)
      (string? (:style attrs)) (update :style explode-styles))))

(defn flatten-seqs [xs]
  (loop [res []
         [x & xs] xs]
    (cond
      (and (nil? xs) (nil? x)) (not-empty res)
      (seq? x) (recur (concat res (flatten-seqs x)) xs)
      :else (recur (conj res x) xs))))

(defn get-children
  "Given an optional tag namespace `ns` (e.g. for SVG nodes) and `headers`, as
  produced by `get-hiccup-headers`, returns a flat collection of children as
  \"hiccup headers\". Children will carry the `ns`, if any."
  [headers ns]
  (when-not (:innerHTML (nth headers hiccup-attrs))
    (->> (nth headers hiccup-children)
         flatten-seqs
         (map #(get-hiccup-headers % ns)))))

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

(defn register-hook
  "Register the life-cycle hook from the corresponding virtual DOM node to call in
  `impl`, if any. The only time the hook in `old` is used is when `new` is
  `nil`, which means the node is unmounting. `details` is a vector of keywords
  that provide some detail about why the hook is invoked."
  [{:keys [hooks]} node new & [old details]]
  (when-let [hook (:replicant/on-update (if new (nth new hiccup-attrs) (nth old hiccup-attrs)))]
    (vswap! hooks conj [hook node new old details])))

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
    :style (update-styles renderer el (:style new) (:style old))
    :classes (update-classes renderer el (:classes new) (:classes old))
    :on (update-event-listeners renderer el (:on new) (:on old))
    (if-let [v (attr new)]
      (when (not= v (attr old))
        (set-attr-val renderer el attr v))
      (r/remove-attribute renderer el (name attr)))))

(defn update-attributes [renderer el new old]
  (let [new-attrs (get-attrs new)
        old-attrs (get-attrs old)]
    (if (= new-attrs old-attrs)
      false
      (do
        (->> (into (set (keys new-attrs)) (keys old-attrs))
             (run! #(update-attr renderer el % new-attrs old-attrs)))
        true))))

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
    :style (set-styles renderer el (:style new))
    :classes (set-classes renderer el (:classes new))
    :on (set-event-listeners renderer el (:on new))
    (set-attr-val renderer el attr (attr new))))

(defn set-attributes [renderer el new-attrs]
  (->> (keys new-attrs)
       (filter new-attrs)
       (run! #(set-attr renderer el % new-attrs))))

(defn create-node
  "Create DOM node according to virtual DOM in `hiccup-headers`. Register relevant
  life-cycle hooks from the new node or its descendants in `impl`. Returns the
  newly created node."
  [{:keys [renderer] :as impl} hiccup-headers]
  (if (string? hiccup-headers)
    (r/create-text-node renderer hiccup-headers)
    (let [tag-name (nth hiccup-headers hiccup-tag-name)
          ns (or (nth hiccup-headers hiccup-ns)
                 (when (= "svg" tag-name)
                   "http://www.w3.org/2000/svg"))
          node (r/create-element renderer tag-name (when ns {:ns ns}))]
      (set-attributes renderer node (get-attrs hiccup-headers))
      (->> (get-children hiccup-headers ns)
           (run! #(r/append-child renderer node (create-node impl %))))
      (register-hook impl node hiccup-headers)
      node)))

(defn reusable?
  "Two elements are considered the similar enough for reuse if they are both
  hiccup elements with the same tag name and the same key (or both have no key)
  - or they are both strings.

  Similarity in this case indicates that the node can be used for reconciliation
  instead of creating a new node from scratch."
  [a b]
  (or (and (string? a) (string? b))
      (and (= (nth a hiccup-key) (nth b hiccup-key))
           (= (nth a hiccup-tag-name) (nth b hiccup-tag-name)))))

(defn changed?
  "Returns `true` when nodes have changed in such a way that a new node should be
  created. `changed?` is not the strict complement of `resuable?`, because it
  does not consider any two strings the same - only the exact same string."
  [new old]
  (or (not= (type old) (type new))
      (and (string? old) (not= new old))
      (not= (nth old hiccup-tag-name) (nth new hiccup-tag-name))))

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
  (or (nth headers hiccup-ns)
      (when (= "svg" (nth headers hiccup-tag-name))
        "http://www.w3.org/2000/svg")))

(defn update-children [impl el new old]
  (let [r (:renderer impl)
        old-children (get-children old (get-ns old))]
    (loop [new-c (seq (get-children new (get-ns new)))
           old-c (seq old-children)
           n 0
           move-n 0
           n-children (count old-children)
           changed? false]
      (let [new-hiccup (first new-c)
            old-hiccup (first old-c)]
        (cond
          ;; Both empty, we're done
          (and (nil? new-c) (nil? old-c))
          changed?

          ;; There are old nodes where there are no new nodes: delete
          (nil? new-c)
          (do
            (run! #(let [child (r/get-child r el n)]
                     (r/remove-child r el child)
                     (register-hook impl child nil %)) old-c)
            true)

          ;; There are new nodes where there were no old ones: create
          (nil? old-c)
          (do
            (run! #(r/append-child r el (create-node impl %)) new-c)
            true)

          ;; It's "the same node" (e.g. reusable), reconcile
          (reusable? new-hiccup old-hiccup)
          (let [node-changed? (reconcile* impl el new-hiccup old-hiccup n)]
            (when (and (not node-changed?) (< n move-n))
              (register-hook impl (r/get-child r el n) new-hiccup old-hiccup [:replicant/move-node]))
            (recur (next new-c) (next old-c) (unchecked-inc-int n) move-n n-children (or changed? node-changed?)))

          ;; Nodes are either new, have been replaced, or have moved. Find the
          ;; original position of the two nodes currently being considered, to
          ;; determine which it is.
          :else
          (let [o-idx (int (index-of #(reusable? new-hiccup %) old-c))]
            (if (< o-idx 0)
              ;; new-hiccup represents a node that did not previously exist,
              ;; create it
              (let [child (create-node impl new-hiccup)]
                (if (<= n-children n)
                  (r/append-child r el child)
                  (r/insert-before r el child (r/get-child r el n)))
                (recur (next new-c) old-c (unchecked-inc-int n) move-n (unchecked-inc-int n-children) true))
              (let [n-idx (int (index-of #(reusable? old-hiccup %) new-c))]
                (cond
                  ;; the old node no longer exists, remove it
                  (< n-idx 0)
                  (let [child (r/get-child r el n)]
                    (r/remove-child r el child)
                    (register-hook impl child nil old-hiccup)
                    (recur new-c (next old-c) n move-n (dec n-children) true))

                  (< o-idx n-idx)
                  ;; The new node needs to be moved back
                  ;;
                  ;; Old: 1 2 3
                  ;; New: 2 3 1
                  ;;
                  ;; old-hiccup: 1, n-idx: 2
                  ;; new-hiccup: 2, o-idx: 1
                  ;;
                  ;; The old node is now at the end, move it there and continue. It
                  ;; will be reconciled when the loop reaches it.
                  ;;
                  ;; append-child 0
                  ;; Old: 2 3 1
                  ;; New: 2 3 1
                  (let [idx (unchecked-inc-int (unchecked-add-int n n-idx))
                        child (r/get-child r el n)]
                    (if (< idx n-children)
                      (r/insert-before r el child (r/get-child r el idx))
                      (r/append-child r el child))
                    (register-hook impl child (nth new-c n-idx) old-hiccup [:replicant/move-node])
                    (recur
                     new-c
                     (concat (take n-idx (next old-c)) [(first old-c)] (drop (unchecked-inc-int n-idx) old-c))
                     n
                     (unchecked-dec-int idx)
                     n-children
                     true))

                  :else
                  ;; The new node needs to be brought to the front
                  ;;
                  ;; Old: 1 2 3
                  ;; New: 3 1 2
                  ;;
                  ;; old-hiccup: 1, n-idx: 1
                  ;; new-hiccup: 3, o-idx: 2
                  ;;
                  ;; The new node used to be at the end, move it to the front and
                  ;; reconcile it, then continue with the rest of the nodes.
                  ;;
                  ;; insert-before 3 1
                  ;; Old: 1 2
                  ;; New: 1 2
                  (let [idx (unchecked-add-int n o-idx)
                        child (r/get-child r el idx)
                        corresponding-old-hiccup (nth old-c o-idx)]
                    (r/insert-before r el child (r/get-child r el n))
                    (when (not (reconcile* impl el new-hiccup corresponding-old-hiccup n))
                      ;; If it didn't change, reconcile* did not schedule a hook
                      ;; Because the node just moved we still need the hook
                      (register-hook impl child new-hiccup corresponding-old-hiccup [:replicant/move-node]))
                    (recur (next new-c) (concat (take o-idx old-c) (drop (unchecked-inc-int o-idx) old-c)) (unchecked-inc-int n) (unchecked-inc-int (unchecked-add-int n o-idx)) n-children true)))))))))))

(defn reconcile* [{:keys [renderer] :as impl} el new old index]
  (cond
    (= new old)
    false

    (nil? new)
    (let [child (r/get-child renderer el index)]
      (r/remove-child renderer el child)
      (register-hook impl child new old)
      true)

    ;; The node at this index is of a different type than before, replace it
    ;; with a fresh one. Use keys to avoid ending up here.
    (changed? new old)
    (let [node (create-node impl new)]
      (r/replace-child renderer el node (r/get-child renderer el index))
      true)

    ;; Update the node's attributes and reconcile its children
    (not (string? new))
    (let [child (r/get-child renderer el index)
          attrs-changed? (update-attributes renderer child new old)
          children-changed? (update-children impl child new old)
          attrs-changed? (or attrs-changed?
                             (not= (:replicant/on-update (nth new hiccup-attrs))
                                   (:replicant/on-update (nth old hiccup-attrs))))]
      (->> (cond
             (and attrs-changed? children-changed?)
             [:replicant/updated-attrs
              :replicant/updated-children]

             attrs-changed?
             [:replicant/updated-attrs]

             :else
             [:replicant/updated-children])
           (remove nil?)
           (register-hook impl child new old))
      true)))

(defn reconcile
  "Reconcile the DOM in `el` by diffing the `new` hiccup with the `old` hiccup. If
  there is no `old` hiccup, `reconcile` will create the DOM as per `new`.
  Assumes that the DOM in `el` is in sync with `old` - if not, this will
  certainly not produce the desired result."
  [renderer el new & [old]]
  (let [impl {:renderer renderer
              :hooks (volatile! [])}]
    (if (nil? old)
      (r/append-child renderer el (create-node impl (get-hiccup-headers new nil)))
      (reconcile* impl el (get-hiccup-headers new nil) (get-hiccup-headers old nil) 0))
    (let [hooks @(:hooks impl)]
      (run! call-hooks hooks)
      hooks)))
