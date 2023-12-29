(ns replicant.hiccup
  (:require [clojure.string :as str]))

(defn hiccup? [sexp]
  (and (vector? sexp)
       (not (map-entry? sexp))
       (or (keyword? (first sexp)) (fn? (first sexp)))))

(defn get-classes [classes]
  (set
   (cond
     (keyword? classes) [(name classes)]
     (string? classes) [classes]
     (empty? classes) []
     (coll? classes) (map #(if (keyword? %) (name %) %) classes)
     (nil? classes) []
     :else (throw (ex-info "Received class name that is neither string, keyword, or a collection of those"
                           {:classes classes})))))

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
       (loop [classes []
              s (.substring tag (unchecked-inc-int class-index))]
         (let [index (.indexOf s ".")]
           (if (pos? index)
             (recur
              (conj classes (.substring s 0 index))
              (.substring s (unchecked-inc-int index)))
             (conj classes s)))))]))

(defn parse-hiccup-symbol [sym attrs]
  (let [[el id classes] (parse-tag sym)
        classes (->> (concat
                      (get-classes (:class attrs))
                      (get-classes (:className attrs))
                      classes)
                     (mapcat #(str/split % #" +"))
                     (remove empty?))]
    [(str/lower-case el)
     (cond-> (dissoc attrs :class :className)
       id (assoc :id id)
       (seq classes) (assoc :classes classes))]))

(defn get-tag-name [hiccup]
  (when (coll? hiccup)
    (let [sym (first hiccup)]
      (when (keyword? sym)
        (let [s (name sym)
              id-index (.indexOf s "#")
              class-index (.indexOf s ".")]
          (cond
            (<= 0 id-index) (.substring s 0 id-index)
            (<= 0 class-index) (.substring s 0 class-index)
            :else s))))))

(defn explode-styles [s]
  (->> (str/split s #";")
       (map #(let [[k v] (map str/trim (str/split % #":"))]
               [(keyword k) v]))
       (into {})))

(def ^:private skip-pixelize-attrs
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

(defn prep-styles [styles]
  (reduce (fn [m [attr v]]
            (if (number? v)
              (if (skip-pixelize-attrs attr)
                (update m attr str)
                (update m attr str "px"))
              m))
          styles
          styles))

(defn prep-hiccup-attrs [attrs]
  (cond-> attrs
    (string? (:style attrs)) (update :style explode-styles)
    (:style attrs) (update :style prep-styles)))

(defn flatten-seqs [xs]
  (loop [res []
         [x & xs] xs]
    (cond
      (and (nil? xs) (nil? x)) (not-empty res)
      (seq? x) (recur (into res (flatten-seqs x)) xs)
      :else (recur (conj res x) xs))))

(defn inflate [sexp]
  (let [tag-name (first sexp)
        args (rest sexp)
        args (if (map? (first args)) args (concat [{}] args))]
    (if (fn? tag-name)
      (apply tag-name (rest sexp))
      (let [[tag-name attrs] (parse-hiccup-symbol (name tag-name) (first args))]
        {:tag-name tag-name
         :attrs (prep-hiccup-attrs attrs)
         :children (flatten-seqs (rest args))}))))
