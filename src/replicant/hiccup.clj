(ns replicant.hiccup)

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

(defmacro create [parsed-tag attrs children ns sexp text]
  (if (:ns &env)
    `(doto ~parsed-tag
       (.push (:replicant/key ~attrs))
       (.push ~attrs)
       (.push ~children)
       (.push ~ns)
       (.push ~sexp)
       (.push ~text))
    `(-> ~parsed-tag
         (conj (:replicant/key ~attrs))
         (conj ~attrs)
         (conj ~children)
         (conj ~ns)
         (conj ~sexp)
         (conj ~text))))
