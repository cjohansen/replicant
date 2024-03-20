(ns replicant.hiccup
  #?(:cljs (:require-macros [replicant.hiccup])))

(defmacro hget [x k]
  (if (:ns &env)
    `(aget ~x ~k)
    `(nth ~x ~k)))

(defmacro tag-name [headers]
  `(hget ~headers 0))

(defmacro id [headers]
  `(hget ~headers 1))

(defmacro classes [headers]
  `(hget ~headers 2))

(defmacro rkey [headers]
  `(hget ~headers 3))

(defmacro attrs [headers]
  `(hget ~headers 4))

(defmacro children [headers]
  `(hget ~headers 5))

(defmacro html-ns [headers]
  `(hget ~headers 6))

(defmacro sexp [headers]
  `(hget ~headers 7))

(defmacro text [headers]
  `(hget ~headers 8))

(defmacro get-key [parsed-tag attrs]
  `(when-let [k# (:replicant/key ~attrs)]
     [(hget ~parsed-tag 0) k#]))

(defmacro create [parsed-tag attrs children ns sexp]
  (if (:ns &env)
    `(doto ~parsed-tag
       (.push (get-key ~parsed-tag ~attrs))
       (.push ~attrs)
       (.push ~children)
       (.push ~ns)
       (.push ~sexp)
       (.push nil))
    `(-> ~parsed-tag
         (conj (get-key ~parsed-tag ~attrs))
         (conj ~attrs)
         (conj ~children)
         (conj ~ns)
         (conj ~sexp)
         (conj nil))))

(defmacro create-text-node [text]
  (if (:ns &env)
    `(js/Array. nil nil nil nil nil nil nil ~text ~text)
    `[nil nil nil nil nil nil nil ~text ~text]))

(defmacro update-attrs [headers & args]
  (if (:ns &env)
    `(do
       (aset ~headers 4 (~(first args) (aget ~headers 4) ~@(rest args)))
       ~headers)
    `(update ~headers 4 ~@args)))
