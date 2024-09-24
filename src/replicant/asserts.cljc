(ns replicant.asserts
  (:require [replicant.assert :as assert]
            [replicant.hiccup :as hiccup]
            [clojure.string :as str])
  #?(:cljs (:require-macros [replicant.asserts])))

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
    (or (string? ~k) (keyword? ~k) (symbol? ~k))
    (str "Style key " ~k " should be a keyword")
    "Replicant expects your style keys to be strings, or the very least something that supports `name`. Other types will not work as expected."))

(defmacro assert-non-empty-id [tag]
  `(assert/assert
    (not (re-find #"#($|\.)" ~(str tag)))
    (str "Hiccup tag " ~tag " contains an empty id")
    "Either complete the id or remove the # character."))

(defmacro assert-valid-id [tag]
  `(assert/assert
    (not (re-find #"#[^a-zA-Z_\.]" ~(str tag)))
    (str "Hiccup tag " ~tag " contains an invalid id")
    "IDs must start with a letter."))

(defmacro assert-non-empty-class [tag]
  `(assert/assert
    (not (re-find #"\.$" ~(str tag)))
    (str "Hiccup tag " ~tag " contains an empty class")
    "This may cause a DOMException and is considered a coding error. Replicant will not sacrifice performance to work around it."))

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
