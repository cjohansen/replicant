(ns replicant.core
  "Beware! This code is written for performance. It does a lot of things that can
  not be considered idiomatic Clojure. If you find yourself looking at it and
  asking \"why are things done like that?\" the answer is most likely
  \"performance\". With that out of the way...

  To postpone as much processing as possible, Replicant uses three separate
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
  (:require [replicant.assert :as assert]
            [replicant.asserts :as asserts]
            [replicant.hiccup :as hiccup]
            [replicant.protocols :as r]
            [replicant.vdom :as vdom]))

;; Hiccup stuff

#_(set! *warn-on-reflection* true)
#_(set! *unchecked-math* :warn-on-boxed)

(defn hiccup? [sexp]
  (and (vector? sexp)
       (not (map-entry? sexp))
       (keyword? (first sexp))))

(defn parse-tag [^clojure.lang.Keyword tag]
  (asserts/assert-non-empty-id tag)
  (asserts/assert-valid-id tag)
  (asserts/assert-non-empty-class tag)
  ;; Borrowed from hiccup, and adapted to support multiple classes
  (let [ns ^String (namespace tag)
        tag ^String (name tag)
        id-index (let [index (.indexOf tag "#")] (when (pos? index) index))
        class-index (let [index (.indexOf tag ".")] (when (pos? index) index))
        tag-name (cond->> (cond
                            id-index (.substring tag 0 id-index)
                            class-index (.substring tag 0 class-index)
                            :else tag)
                   ns (keyword ns))
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

  Returns a tuple (instead of a map) for speed.

  - `sexp` is the hiccup to parse

  - `ns` is the namespace of the elements, used for SVG elements. The SVG
  element has an explicit namespace, which needs to be set on all of its
  children, so they can all be created with createElementNS etc."
  [ns sexp]
  (when sexp
    (if (hiccup? sexp)
      (let [sym (first sexp)
            args (rest sexp)
            has-args? (map? (first args))
            attrs (if has-args? (first args) {})]
        (hiccup/create (parse-tag sym) attrs (if has-args? (rest args) args) ns sexp))
      (hiccup/create-text-node (str sexp)))))

(defn get-classes [classes]
  (cond
    (keyword? classes) [(name classes)]
    (empty? classes) []
    (coll? classes) (keep (fn [class]
                            (when class
                              (cond
                                (keyword? class) (name class)
                                (string? class) (not-empty (.trim class)))))
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
  (cond
    (number? v)
    (if (skip-pixelize-attrs attr)
      (str v)
      (str v "px"))

    (keyword? v)
    (name v)

    :else v))

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
  (asserts/assert-no-class-name headers)
  (asserts/assert-no-space-separated-class headers)
  (asserts/assert-no-string-style headers)
  (prep-attrs (hiccup/attrs headers) (hiccup/id headers) (hiccup/classes headers)))

(defn merge-attrs [attrs overrides]
  (cond-> (merge attrs (dissoc overrides :style))
    (or (:style attrs)
        (:style overrides))
    (update :style merge (:style overrides))))

(defn get-mounting-attrs [headers]
  (if-let [mounting (:replicant/mounting (hiccup/attrs headers))]
    [(get-attrs headers)
     (let [headers (cond-> headers
                     mounting (hiccup/update-attrs merge-attrs mounting))]
       (prep-attrs (hiccup/attrs headers) (hiccup/id headers) (hiccup/classes headers)))]
    [(get-attrs headers)]))

(defn get-unmounting-attrs [vdom]
  (when (vdom/async-unmount? vdom)
    (-> (vdom/attrs vdom)
        (merge-attrs (:replicant/unmounting (nth (vdom/sexp vdom) 1)))
        (prep-attrs nil (vdom/classes vdom)))))

(defn ^:private flatten-seqs* [xs coll]
  (reduce
   (fn [_ x]
     (cond (seq? x) (flatten-seqs* x coll)
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
         (mapv #(some->> % (get-hiccup-headers ns))))))

(defn get-children-ks
  "Like `get-children` but returns a tuple of `[children ks]` where `ks` is a set
  of the keys in `children`."
  [headers ns]
  (let [[children ks]
        (->> (hiccup/children headers)
             flatten-seqs
             (reduce (fn [[children ks] hiccup]
                       (if hiccup
                         (let [headers (get-hiccup-headers ns hiccup)
                               k (hiccup/rkey headers)]
                           [(conj! children headers)
                            (cond-> ks k (conj! k))])
                         [(conj! children nil) ks]))
                     [(transient []) (transient #{})]))]
    [(persistent! children) (persistent! ks)]))

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
          (let [node #?(:cljs (.-target e)
                        :clj nil)
                rd (cond-> {:replicant/trigger :replicant.trigger/dom-event
                            :replicant/js-event e ;; Backwards compatibility
                            :replicant/dom-event e}
                     node (assoc :replicant/node node))]
            (*dispatch* rd handler))))
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

(defn call-hook [[hook k node new old details]]
  (let [f (get-life-cycle-hook hook)
        life-cycle (cond
                     (nil? old) :replicant.life-cycle/mount
                     (nil? new) :replicant.life-cycle/unmount
                     :else :replicant.life-cycle/update)]
    (when (or (= :replicant/on-render k)
              (and (= k :replicant/on-mount)
                   (= life-cycle :replicant.life-cycle/mount))
              (and (= k :replicant/on-unmount)
                   (= life-cycle :replicant.life-cycle/unmount))
              (and (= k :replicant/on-update)
                   (= life-cycle :replicant.life-cycle/update)))
      (f (cond-> {:replicant/trigger :replicant.trigger/life-cycle
                  :replicant/life-cycle life-cycle
                  :replicant/node node}
           details (assoc :replicant/details details))))))

(defn register-hooks
  "Register the life-cycle hooks from the corresponding virtual DOM node to call
  in `impl`, if any. `details` is a vector of keywords that provide some detail
  about why the hook is invoked."
  [{:keys [hooks]} node headers & [vdom details]]
  (let [target (if headers (hiccup/attrs headers) (vdom/attrs vdom))
        new-hooks (keep (fn [life-cycle-key]
                          (when-let [hook (life-cycle-key target)]
                            [life-cycle-key hook]))
                        [:replicant/on-render
                         :replicant/on-mount
                         :replicant/on-unmount
                         :replicant/on-update])]
    (when-not (empty? new-hooks)
      (let [headers-sexp (some-> headers hiccup/sexp)
            vdom-sexp (some-> vdom vdom/sexp)]
        (->> new-hooks
             (map (fn [[k hook]] [hook k node headers-sexp vdom-sexp details]))
             (vswap! hooks into))))))

(defn register-mount [{:keys [mounts]} node mounting-attrs attrs]
  (vswap! mounts conj [node mounting-attrs attrs]))

;; Perform DOM operations

(defn update-styles [renderer el new-styles old-styles]
  (let [new-ks (set (remove #(nil? (% new-styles)) (keys new-styles)))
        old-ks (set (keys old-styles))]
    (run! #(r/remove-style renderer el %) (remove new-ks old-ks))
    (run!
     #(let [new-style (% new-styles)]
        (when (not= new-style (% old-styles))
          (asserts/assert-style-key-type %)
          (asserts/assert-style-key-casing %)
          (r/set-style renderer el % (get-style-val % new-style))))
     new-ks)))

(defn update-classes [renderer el new-classes old-classes]
  (->> (remove (set new-classes) old-classes)
       (run! #(r/remove-class renderer el %)))
  (->> (remove (set old-classes) new-classes)
       (run! #(r/add-class renderer el %))))

(defn add-event-listeners [renderer el val]
  (->> val
       (remove (comp nil? second))
       (run! (fn [[event handler]]
               (asserts/assert-event-handler-casing event)
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
    (asserts/assert-no-event-attribute attr)
    (->> (cond-> {}
           (= 0 (.indexOf an "xml:"))
           (assoc :ns xmlns)

           (= 0 (.indexOf an "xlink:"))
           (assoc :ns xlinkns))
         (r/set-attribute renderer el an (cond-> v
                                           (or (keyword? v)
                                               (symbol? v)) name)))))

(defn update-attr [renderer el attr new old]
  (when-not (namespace attr)
    (case attr
      :style (update-styles renderer el (:style new) (:style old))
      :classes (update-classes renderer el (:classes new) (:classes old))
      :on (update-event-listeners renderer el (:on new) (:on old))
      (if-let [v (attr new)]
        (when (not= v (attr old))
          (set-attr-val renderer el attr v))
        (r/remove-attribute renderer el (name attr))))))

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
       (run! #(do
                (asserts/assert-style-key-type %)
                (asserts/assert-style-key-casing %)
                (r/set-style renderer el % (get-style-val % (get new-styles %)))))))

(defn set-classes [renderer el new-classes]
  (->> new-classes
       (run! #(r/add-class renderer el %))))

(defn set-event-listeners [renderer el new-handlers]
  (add-event-listeners renderer el new-handlers))

(defn set-attr [renderer el attr new]
  (when-not (namespace attr)
    (case attr
      :style (set-styles renderer el (:style new))
      :classes (set-classes renderer el (:classes new))
      :on (set-event-listeners renderer el (:on new))
      (set-attr-val renderer el attr (attr new)))))

(defn set-attributes [renderer el new-attrs]
  (->> (keys new-attrs)
       (filter new-attrs)
       (run! #(set-attr renderer el % new-attrs))))

(defn render-default-alias [tag-name _attrs children]
  [:div
   {:data-replicant-error (str "Undefined alias " tag-name)}
   (for [child children]
     (cond-> child
       (not (string? child)) pr-str))])

(defn add-classes [class-attr classes]
  (cond
    (coll? class-attr)
    (concat class-attr classes)

    (nil? class-attr)
    classes

    :else (cons class-attr classes)))

(defn get-alias-headers [{:keys [aliases]} headers]
  (let [tag-name (hiccup/tag-name headers)]
    (when (keyword? tag-name)
      (let [f (or (get aliases tag-name) (partial render-default-alias tag-name))
            id (hiccup/id headers)
            classes (hiccup/classes headers)]
        (try
          (->> (hiccup/children headers)
               flatten-seqs
               seq
               (f (cond-> (hiccup/attrs headers)
                    id (update :id #(or % id))
                    (seq classes) (update :class add-classes classes)))
               (get-hiccup-headers nil)
               (hiccup/from-alias tag-name headers))
          (catch #?(:clj Exception :cljs :default) e
            (->> [:div {:data-replicant-error "Alias threw exception"
                        :data-replicant-exception #?(:clj (.getMessage e)
                                                     :cljs (.-message e))
                        :data-replicant-sexp (pr-str (hiccup/sexp headers))}]
                 (get-hiccup-headers nil))))))))

(defn create-node
  "Create DOM node according to virtual DOM in `headers`. Register relevant
  life-cycle hooks from the new node or its descendants in `impl`. Returns a
  tuple of the newly created node and the fully realized vdom."
  [{:keys [renderer] :as impl} headers]
  (assert/enter-node headers)
  (or
   (when-let [text (hiccup/text headers)]
     [(r/create-text-node renderer text)
      (vdom/create-text-node text)])

   (some->> (get-alias-headers impl headers)
            (create-node impl))

   (let [tag-name (hiccup/tag-name headers)
         ns (or (hiccup/html-ns headers)
                (when (= "svg" tag-name)
                  "http://www.w3.org/2000/svg"))
         node (r/create-element renderer tag-name (when ns {:ns ns}))
         [attrs mounting-attrs] (get-mounting-attrs headers)
         _ (set-attributes renderer node (or mounting-attrs attrs))
         [children ks n-children]
         (->> (get-children headers ns)
              (reduce (fn [[children ks n] child-headers]
                        (if child-headers
                          (let [[child-node vdom] (create-node impl child-headers)
                                k (vdom/rkey vdom)]
                            (r/append-child renderer node child-node)
                            [(conj! children vdom) (cond-> ks k (conj! k)) (unchecked-inc-int n)])
                          [(conj! children nil) ks n]))
                      [(transient []) (transient #{}) 0]))]
     (register-hooks impl node headers)
     (when mounting-attrs
       (register-mount impl node mounting-attrs attrs))
     [node (vdom/from-hiccup headers attrs (persistent! children) (persistent! ks) n-children)])))

(defn reusable?
  "Two elements are considered similar enough for reuse if they are both hiccup
  elements with the same tag name and the same key (or both have no key) - or
  they are both strings.

  Similarity in this case indicates that the node can be used for reconciliation
  instead of creating a new node from scratch."
  [headers vdom]
  (or (and (hiccup/text headers) (vdom/text vdom))
      (and (= (hiccup/rkey headers) (vdom/rkey vdom))
           (= (hiccup/ident headers) (vdom/ident vdom)))))

(defn same? [headers vdom]
  (and (= (hiccup/rkey headers) (vdom/rkey vdom))
       (= (hiccup/ident headers) (vdom/ident vdom))))

;; reconcile* and update-children are mutually recursive
(declare reconcile*)

(defn index-of [f xs]
  (loop [coll-n 0
         dom-n 0
         xs (seq xs)]
    (cond
      (nil? xs) [-1 -1]
      (nil? (first xs)) (recur (unchecked-inc-int coll-n) dom-n (next xs))
      (f (first xs)) [coll-n dom-n]
      :else (recur (unchecked-inc-int coll-n) (unchecked-inc-int dom-n) (next xs)))))

(defn get-ns [headers]
  (or (hiccup/html-ns headers)
      (when (= "svg" (hiccup/tag-name headers))
        "http://www.w3.org/2000/svg")))

(defn ^:private insert-children [{:keys [renderer] :as impl} el children vdom]
  (reduce (fn [[res n] child]
            (if child
              (let [[node vdom] (create-node impl child)]
                (r/append-child renderer el node)
                [(conj! res vdom) (unchecked-inc-int n)])
              [(conj! res nil) n]))
          [vdom 0] children))

(defn remove-child [{:keys [renderer] :as impl} unmounts el n vdom]
  ;; An assigned id means the node has already started unmounting
  (if-let [id (vdom/unmount-id vdom)]
    ;; If the id is in the unmounts set, it has not yet finished unmounting
    (when (unmounts id)
      vdom)
    (let [res (if-let [attrs (get-unmounting-attrs vdom)]
                ;; The node has unmounting attributes: mark it as unmounting,
                ;; and start the process
                (let [vdom (vdom/mark-unmounting vdom)
                      child (r/get-child renderer el n)]
                  (update-attributes renderer child attrs (vdom/attrs vdom))
                  ;; Record the node as unmounting
                  (vswap! (:unmounts impl) conj (vdom/unmount-id vdom))
                  (->> (fn []
                         ;; We're done, remove it from the set of unmounting
                         ;; nodes
                         (vswap! (:unmounts impl) disj (vdom/unmount-id vdom))
                         (r/remove-child renderer el child)
                         (when-let [hook (:replicant/on-render (vdom/attrs vdom))]
                           (call-hook [hook :replicant/on-render child nil vdom]))
                         renderer)
                       (r/on-transition-end renderer child))
                  vdom)
                (let [child (r/get-child renderer el n)]
                  (r/remove-child renderer el child)
                  (register-hooks impl child nil vdom)
                  nil))]
      res)))

(def move-node-details [:replicant/move-node])

(defn unchanged? [headers vdom]
  (= (some-> headers hiccup/sexp) (some-> vdom vdom/original-sexp)))

(defn ^:private move-nodes [{:keys [renderer] :as impl} el headers new-children vdom old-children n n-children]
  (let [[o-idx o-dom-idx] (if (hiccup/rkey headers)
                            (index-of #(same? headers %) old-children)
                            [-1 -1])
        [n-idx n-dom-idx] (if (vdom/rkey vdom)
                            (index-of #(same? % vdom) new-children)
                            [-1 -1])]
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
      (let [idx (unchecked-inc-int (unchecked-add-int n n-dom-idx))
            child (r/get-child renderer el n)]
        (if (< idx n-children)
          (r/insert-before renderer el child (r/get-child renderer el idx))
          (r/append-child renderer el child))
        (register-hooks impl child (nth new-children n-idx) vdom move-node-details)
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
      (let [idx (unchecked-add-int n o-dom-idx)
            child (r/get-child renderer el idx)
            corresponding-old-vdom (nth old-children o-idx)]
        (r/insert-before renderer el child (r/get-child renderer el n))
        (reconcile* impl el headers corresponding-old-vdom n)
        (when (unchanged? headers corresponding-old-vdom)
          ;; If it didn't change, reconcile* did not schedule a hook
          ;; Because the node just moved we still need the hook
          (register-hooks impl child headers corresponding-old-vdom move-node-details))
        [(next new-children)
         (concat (take o-idx old-children) (drop (unchecked-inc-int o-idx) old-children))
         (unchecked-inc-int n)
         (unchecked-inc-int (unchecked-add-int n o-idx))
         corresponding-old-vdom]))))

(defn insert-node [r el child n n-children]
  (if (<= n-children n)
    (r/append-child r el child)
    (r/insert-before r el child (r/get-child r el n))))

(defn update-children [impl el new-children new-ks old-children old-ks n-children]
  (let [r (:renderer impl)
        unmounts @(:unmounts impl)]
    (loop [new-c (seq new-children)
           old-c (seq old-children)
           n 0
           move-n 0
           n-children (or n-children 0)
           changed? false
           vdom (transient [])]
      (let [new-headers (first new-c)
            old-vdom (first old-c)
            new-empty? (nil? new-c)
            old-empty? (nil? old-c)
            new-nil? (nil? new-headers)
            old-nil? (nil? old-vdom)]
        (cond
          ;; Both empty, we're done
          (and new-empty? old-empty?)
          [changed? (persistent! vdom) new-ks n-children]

          ;; There are old nodes where there are no new nodes: delete
          new-empty?
          (loop [children (seq old-c)
                 vdom vdom
                 n n
                 n-children n-children]
            (cond
              (nil? children)
              [true (persistent! vdom) new-ks n-children]

              (nil? (first children))
              (recur (next children) (conj! vdom nil) n n-children)

              :else
              (if-let [pending-vdom (remove-child impl unmounts el n (first children))]
                (recur (next children) (conj! vdom pending-vdom) (unchecked-inc-int n) n-children)
                (recur (next children) vdom n (unchecked-dec-int n-children)))))

          ;; There are new nodes where there were no old ones: create
          old-empty?
          (let [[vdom n] (insert-children impl el new-c vdom)]
            [true (persistent! vdom) new-ks (+ n-children n)])

          ;; Both nodes are nil
          (and new-nil? old-nil?)
          (recur (next new-c) (next old-c) n move-n n-children changed? (conj! vdom nil))

          ;; Old node is already on its way out from a previous render
          (and old-vdom (vdom/unmount-id old-vdom))
          (let [[child child-vdom] (when (and new-headers (not (old-ks (hiccup/rkey new-headers))))
                                     (let [res (create-node impl new-headers)]
                                       (insert-node r el (first res) n n-children)
                                       res))]
            (if (unmounts (vdom/unmount-id old-vdom))
              ;; Still unmounting
              (cond
                new-nil?
                (recur (next new-c) (next old-c) (unchecked-inc-int n) move-n n-children changed? (conj! vdom old-vdom))

                child
                (recur (next new-c) (next old-c) (+ n 2) move-n (unchecked-inc-int n-children) true (conj! vdom child-vdom))

                :else
                (recur new-c (next old-c) (unchecked-inc-int n) move-n n-children changed? (conj! vdom old-vdom)))
              ;; It's gone!
              (cond
                new-nil?
                (recur (next new-c) (next old-c) n (unchecked-dec-int move-n) (unchecked-dec-int n-children) changed? (conj! vdom nil))

                child
                (recur (next new-c) (next old-c) (unchecked-inc-int n) move-n n-children true (conj! vdom child-vdom))

                :else
                (recur new-c (next old-c) n (unchecked-dec-int move-n) (unchecked-dec-int n-children) changed? vdom))))

          ;; Node was removed, or another nil was introduced
          new-nil?
          (if (contains? new-ks (vdom/rkey old-vdom))
            (recur (next new-c) old-c n move-n n-children true vdom)
            (if-let [unmounting-node (remove-child impl unmounts el n old-vdom)]
              (recur (next new-c) (next old-c) (unchecked-inc-int n) move-n n-children true (conj! vdom unmounting-node))
              (recur (next new-c) (next old-c) n move-n (unchecked-dec-int n-children) true (conj! vdom nil))))

          ;; It's a reusable node, reconcile
          (and old-vdom (reusable? new-headers old-vdom))
          (let [new-vdom (reconcile* impl el new-headers old-vdom n)
                node-unchanged? (unchanged? new-headers old-vdom)]
            (when (and node-unchanged? (< n move-n))
              (register-hooks impl (r/get-child r el n) new-headers old-vdom move-node-details))
            (recur (next new-c) (next old-c) (unchecked-inc-int n) move-n n-children (or changed? (not node-unchanged?)) (conj! vdom new-vdom)))

          ;; New node did not previously exist, create it
          (not (old-ks (hiccup/rkey new-headers)))
          (let [[child child-vdom] (create-node impl new-headers)]
            (insert-node r el child n n-children)
            (recur (next new-c) (cond-> old-c (nil? old-vdom) next) (unchecked-inc-int n) move-n (unchecked-inc-int n-children) true (conj! vdom child-vdom)))

          ;; Old node no longer exists, remove it
          (or old-nil? (not (new-ks (vdom/rkey old-vdom))))
          (if-let [unmounting-node (remove-child impl unmounts el n old-vdom)]
            (recur new-c (next old-c) (unchecked-inc-int n) move-n n-children true (conj! vdom unmounting-node))
            (recur new-c (next old-c) n move-n (unchecked-dec-int n-children) true vdom))

          ;; Node has moved
          :else
          (let [[nc oc n move-n vdom-node] (move-nodes impl el new-headers new-c old-vdom old-c n n-children)]
            (recur nc oc n move-n n-children true (cond-> vdom vdom-node (conj! vdom-node)))))))))

(defn reconcile* [{:keys [renderer] :as impl} el headers vdom index]
  (assert/enter-node headers)
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
          headers (or (get-alias-headers impl headers) headers)
          attrs (get-attrs headers)
          vdom-attrs (vdom/attrs vdom)
          attrs-changed? (reconcile-attributes renderer child attrs vdom-attrs)
          [new-children new-ks inner-html?] (if (:innerHTML (hiccup/attrs headers))
                                              [nil nil true]
                                              (get-children-ks headers (get-ns headers)))
          [old-children old-ks old-nc]
          (cond
            (:contenteditable vdom-attrs)
            (do
              ;; If the node is contenteditable, users can
              ;; modify the DOM, and we cannot trust that
              ;; the DOM children still reflect the state
              ;; in `vdom`. To avoid problems when
              ;; updating the children, all children are
              ;; cleared here, and the reconciliation
              ;; proceeds as if all new children are new.
              (r/remove-all-children renderer child)
              [nil nil 0])

            inner-html?
            [nil nil 0]

            :else
            [(vdom/children vdom) (vdom/child-ks vdom) (vdom/n-children vdom)])
          [children-changed? children child-ks n-children] (update-children impl child new-children new-ks old-children old-ks old-nc)
          attrs-changed? (or attrs-changed?
                             (not= (:replicant/on-render (hiccup/attrs headers))
                                   (:replicant/on-render vdom-attrs)))]
      (->> (cond
             (and attrs-changed? children-changed?)
             [:replicant/updated-attrs
              :replicant/updated-children]

             attrs-changed?
             [:replicant/updated-attrs]

             :else
             [:replicant/updated-children])
           (register-hooks impl child headers vdom))
      (vdom/from-hiccup headers attrs children child-ks n-children))))

(defn perform-post-mount-update [renderer [node mounting-attrs attrs]]
  (update-attributes renderer node attrs mounting-attrs))

(defn reconcile
  "Reconcile the DOM in `el` by diffing `hiccup` with `vdom`. If there is no
  `vdom`, `reconcile` will create the DOM as per `hiccup`. Assumes that the DOM
  in `el` is in sync with `vdom` - if not, this will certainly not produce the
  desired result."
  [renderer el hiccup & [vdom {:keys [unmounts aliases]}]]
  (let [impl {:renderer renderer
              :hooks (volatile! [])
              :mounts (volatile! [])
              :unmounts (or unmounts (volatile! #{}))
              :aliases aliases}
        vdom (let [headers (get-hiccup-headers nil hiccup)]
               (assert/enter-node headers)
               ;; Not strictly necessary, but it makes noop renders faster
               (if (and headers vdom (unchanged? headers (first vdom)) (= 1 (count vdom)))
                 vdom
                 (let [k (when headers (hiccup/rkey headers))]
                   (-> (update-children
                        impl el
                        (when headers [headers])
                        (cond-> #{} k (conj k))
                        vdom
                        (set (keep #(vdom/rkey %) vdom))
                        (if (first vdom) 1 0))
                       ;; second, because update-children returns [changed? children n-children]
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

(assert/configure)
