(ns ^:no-doc replicant.asserts
  (:require [clojure.string :as str]
            [replicant.assert :as assert]
            [replicant.hiccup-headers :as hiccup]
            [replicant.hiccup :as h]
            [replicant.vdom :as vdom])
  #?(:cljs (:require-macros replicant.asserts)))

(defmacro assert-no-nested-renders []
  `(assert/assert
    false
    "Avoid triggering rendering while rendering"
    (str "replicant.dom/render was called while working on a previous render. "
         "This render call will be postponed. Renders are synchronous and cannot "
         "nest.\n\nNested renders can occur when life-cycle hooks trigger renders "
         "- consider avoiding this if possible, or do so using "
         "requestAnimationFrame. Nested renders can cause performance issues, or, "
         "in the worst case - unresponsive UIs.")))

(defmacro assert-no-class-name [headers]
  `(assert/assert
    (not (contains? (hiccup/attrs ~headers) :className))
    "Use :class, not :className"
    ":className is not supported, please use :class instead. It takes a keyword, a string, or a collection of either of those."
    (hiccup/sexp ~headers)))

(defmacro assert-no-space-separated-class [headers]
  `(assert/assert
    (let [class# (:class (hiccup/attrs ~headers))]
      (or (not (string? class#)) (< (.indexOf class# " ") 0)))
    "Avoid space separated :class strings"
    (let [class# (:class (hiccup/attrs ~headers))]
      (str ":class supports collections of keywords and/or strings as classes. These perform better, and are usually more convenient to work with. Solve by converting "
           (pr-str class#) " to " (pr-str (vec (.split class# " ")))))
    (hiccup/sexp ~headers)))

(defmacro assert-no-string-style [headers]
  `(assert/assert
    (not (string? (:style (hiccup/attrs ~headers))))
    "Avoid string styles"
    ":style supports structured maps of CSS property/value pairs. Strings must be parsed, so they're both slower and harder to read and write."
    (hiccup/sexp ~headers)))

(defmacro assert-event-handler-casing [k]
  `(assert/assert
    (let [event# (name ~k)]
      (or (= "DOMContentLoaded" event#)
          (= event# (str/lower-case event#))))
    (str "Use " (keyword (str/lower-case (name ~k))) ", not " ~k)
    (str "Most event names should be in all lower-case. Replicant passes your event names directly to addEventListener, and mis-cased event names will fail silently.")))

(defmacro assert-style-key-type [k]
  `(assert/assert
    (keyword? ~k)
    (str "Style key " ~k " should be a keyword")
    (str "Replicant expects your style keys to be keywords. While anything that supports `name` (strings, symbols) will "
         "technically work, mixing types will hinder Replicant from recognizing changes properly. Rendering once with "
         (str ~k) " and once with " (keyword (str ~k))
         " may produce undesired results. Your safest option is to always use keywords.")))

(defmacro assert-non-empty-id [tag sexp]
  `(assert/assert
    (not (re-find #"#($|\.)" (str ~tag)))
    (str "Hiccup tag " ~tag " contains an empty id")
    "Either complete the id or remove the # character."
    ~sexp))

(defmacro assert-valid-id [tag sexp]
  `(assert/assert
    (not (re-find #"#[^a-zA-Z_\.]" (str ~tag)))
    (str "Hiccup tag " ~tag " contains an invalid id")
    "IDs must start with a letter."
    ~sexp))

(defmacro assert-non-empty-class [tag sexp]
  `(assert/assert
    (not (re-find #"\.$" (str ~tag)))
    (str "Hiccup tag " ~tag " contains an empty class")
    "This may cause a DOMException and is considered a coding error. Replicant will not sacrifice performance to work around it."
    ~sexp))

(defn camel->dash [s]
  (->> s
       (re-seq #"[A-Z][a-z0-9]*|[a-z0-9]+")
       (map str/lower-case)
       (str/join "-")))

(defn camel->dash-k [k]
  (keyword (camel->dash (name k))))

(defmacro assert-style-key-casing [k]
  `(assert/assert
    (let [name# (name ~k)]
      (or (str/starts-with? name# "--")
          (= name# (str/lower-case name#))))
    (str "Use " (camel->dash-k ~k) ", not " ~k)
    "Replicant passes style keys directly to `el.style.setProperty`, which expects CSS-style dash-cased property names."))

(defmacro assert-no-event-attribute [k]
  `(assert/assert
    (not (str/starts-with? (name ~k) "on"))
    "Set event listeners in the :on map"
    (str "Event handler attributes are not supported. Instead of "
         ~k " set :on {" (keyword (camel->dash (.substring (name ~k) 2))) " ,,,}")))

(defmacro assert-valid-attribute-name [attr v]
  `(assert/assert
    (re-find #"^[a-zA-Z\-:_][a-zA-Z0-9\-:\._]*$" (name ~attr))
    (str "Invalid attribute name " (name ~attr))
    (let [attr# (name ~attr)]
      (str "Tried to set attribute " attr# " to value " ~v ". This will fail"
           "horribly in the browser because "
           (cond
             (re-find #"^[0-9]" attr#)
             " it starts with a number"

             (re-find #"^\." attr#)
             " it starts with a dot"

             :else
             (str " it contains the character " (re-find #"[^a-zA-Z0-9\-:\._]" attr#)))
           ", which isn't allowed as per the HTML spec."))))

(defn has-bad-conditional-attrs? [vdom headers]
  (if (or (< 0 (count (hiccup/children headers)))
          (< 0 (count (vdom/children vdom))))
    (let [[new-selector new-attrs] (hiccup/sexp headers)
          [old-selector old-attrs] (vdom/sexp vdom)]
      (cond
        (not= new-selector old-selector) false
        (nil? new-attrs) (map? old-attrs)
        (map? new-attrs) (nil? old-attrs)
        :else false))
    false))

(defn abbreviate-map [m n]
  (str "{" (->> (take n m)
                (mapv (fn [[k v]]
                        (str k " " (if (map? v)
                                     (abbreviate-map v n)
                                     (pr-str v)))))
                (str/join ", "))
       (when (< n (count m))
         " ,,,")
       "}"))

(defn abbreviate-node [x]
  (cond
    (h/hiccup? x)
    (str "[" (first x) " ,,,]")

    (nil? x)
    "nil"

    (string? x)
    (if (< 20 (count x))
      (str (str/join (take 20 x)) "...")
      x)

    (coll? x)
    (str "(,,,)")

    :else
    (pr-str x)))

(defn format-hiccup-part [x]
  (cond
    (map? x)
    (abbreviate-map x 2)

    (h/hiccup? x)
    (let [s (pr-str x)]
      (if (< (count s) 20)
        s
        (abbreviate-node x)))

    (coll? x)
    (if (= 1 (count x))
      (str "(" (abbreviate-node (first x)) ")")
      (str "(" (abbreviate-node (first x)) " ,,,)"))

    :else
    (pr-str x)))

(defn convey-bad-conditional-attributes [vdom headers]
  (let [[k v] (first (or (not-empty (vdom/attrs vdom))
                         (not-empty (hiccup/attrs headers))))]
    (str "Replicant treats nils as hints of nodes that come and go. Wrapping "
         "the entire attribute map in a conditional such that what used to be "
         (format-hiccup-part (second (vdom/sexp vdom))) " is now "
         (format-hiccup-part (second (hiccup/sexp headers)))
         " can impair how well Replicant can match up child nodes without keys, and "
         "may lead to undesirable behavior for life-cycle events and transitions.\n\n"
         "Instead of:\n[" (first (hiccup/sexp headers))
         " (when something? {"
         (when k
           (str k " " (pr-str v)))
         "}) ,,,]\n\nConsider:\n["
         (first (hiccup/sexp headers))
         (if k
           (str "\n  "
                "(cond-> {}\n    something? (assoc " k " " (pr-str v) "))\n")
           " {}")
         " ,,,]")))

(defmacro assert-no-conditional-attributes [headers vdom]
  `(assert/assert
    (not (has-bad-conditional-attrs? ~vdom ~headers))
    "Avoid conditionals around the attribute map"
    (convey-bad-conditional-attributes ~vdom ~headers)))

(defmacro assert-alias-exists [tag-name f available-aliases]
  `(assert/assert
    (fn? ~f)
    (str "Alias " ~tag-name " isn't defined")
    (str "There's no available function to render this alias. Replicant will "
         "render an empty element with data attributes in its place. Available "
         "aliases are:\n" (str/join "\n" ~available-aliases))))

(defmacro assert-valid-alias-result [tag-name hiccup]
  `(assert/assert
    (or (string? ~hiccup) (h/hiccup? ~hiccup))
    (str "Aliases must return valid hiccup")
    (str "Aliases must always represent a node in the document, and "
         "cannot return " (cond
                            (nil? ~hiccup) "nil"
                            (map? ~hiccup) "a map"
                            (coll? ~hiccup) "multiple nodes"
                            :else (pr-str ~hiccup))
         ". Please check the implementation of " ~tag-name ".")))
