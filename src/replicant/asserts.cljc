(ns replicant.asserts
  (:require [replicant.assert :as assert]
            [replicant.hiccup :as hiccup]))

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
