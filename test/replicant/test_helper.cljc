(ns replicant.test-helper
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [replicant.mutation-log :as mutation-log]))

(defn get-mutation-log-events [renderer]
  (->> renderer :el :log
       (remove (comp #{:get-child :get-parent-node} first))
       (walk/postwalk
        (fn [x]
          (if (:text x)
            (:text x)
            x)))))

(defn get-callback-events [log n]
  (let [events (-> log :el :log)
        f (-> log :el :callbacks deref (nth n))]
    (->> {:el (update (f) :log (comp #(drop (count events) %) deref))}
         get-mutation-log-events)))

(defn call-callback [log n]
  (let [f (-> log :el :callbacks deref (nth n))]
    (assoc log :el (update (dissoc (f) :log) :element deref))))

(defn get-text-nodes [el]
  (if (string? el)
    [el]
    (mapcat get-text-nodes (:children el))))

(defn get-text [el]
  (str/join " " (get-text-nodes el)))

(defn blank? [x]
  (or (nil? x)
      (and (coll? x)
           (empty? x))))

(defn get-tag-name [{:keys [tag-name] :strs [id]}]
  (keyword (str tag-name (when id (str "#" id)))))

(defn format-element [el]
  (if (instance? #?(:clj clojure.lang.Atom
                    :cljs cljs.core/Atom) el)
    (format-element @el)
    (if (:tag-name el)
      (vec (remove blank? [(get-tag-name el) (get-text el)]))
      el)))

(defn hiccup-tag [{:keys [tag-name classes]}]
  (str tag-name
       (when (seq classes)
         (str "." (str/join "." classes)))))

(defn summarize-events [events]
  (->> events
       (map (juxt :replicant/life-cycle
                  :replicant/details
                  (comp hiccup-tag deref :replicant/node)))
       (map #(vec (remove blank? %)))))

(defn summarize [events]
  (for [event events]
    (case (first event)
      :remove-child
      (let [[e from child] event]
        [e (format-element child) :from (:tag-name from)])

      :remove-all-children
      (let [[e from] event]
        [e :from (:tag-name from)])

      :append-child
      (let [[e to child] event]
        [e (format-element child) :to (or (:tag-name to) "Document")])

      :insert-before
      (let [[e in child reference] event]
        [e (format-element child) (format-element reference)
         :in (or (:tag-name in) "Document")])

      :set-attribute
      (let [[e element attr value ns] event]
        (if ns
          [e (format-element element) attr ns (get element attr) :to value]
          [e (format-element element) attr (get element attr) :to value]))

      :set-event-handler
      (let [[e element event handler] event]
        [e (format-element element) event handler])

      :remove-event-handler
      (let [[e element event] event]
        [e (format-element element) event])

      :set-style
      (let [[e element style v] event]
        [e (format-element element) style v])

      :remove-style
      (let [[e element style] event]
        [e (format-element element) style])

      :add-class
      (let [[e element cn] event]
        [e (format-element element) cn])

      :remove-class
      (let [[e element cn] event]
        [e (format-element element) cn])

      :on-transition-end
      (let [[e element _f] event]
        [e (format-element element)])

      event)))

(defn render
  ([vdom] (mutation-log/render {:tag-name "body"} vdom))
  ([{:keys [vdom el unmounts]} new-vdom]
   (mutation-log/render (:element el) new-vdom vdom unmounts)))

(defn text-node-event? [event]
  (or (= :create-text-node (first event))
      (and (= :append-child (first event))
           (string? (second event)))))

(defn remove-text-node-events [events]
  (remove text-node-event? events))

(defn ->hiccup [element]
  (when-let [el (some-> element deref)]
    (if-let [tag-name (:tag-name el)]
      (into [(keyword tag-name)]
            (if-let [inner-html (get el "innerHTML")]
              [inner-html]
              (map ->hiccup (:children el))))
      (:text el))))

(defn ->dom [{:keys [el]}]
  (->> (:element el)
       :children
       first
       ->hiccup))

(defn strip-id [data]
  (walk/postwalk
   (fn [x]
     (cond-> x
       (::mutation-log/id x) (dissoc ::mutation-log/id)))
   data))

(defn get-snapshot [el]
  (strip-id (mutation-log/get-snapshot el)))

(defn summarize-event [e]
  (update e :replicant/node get-snapshot))
