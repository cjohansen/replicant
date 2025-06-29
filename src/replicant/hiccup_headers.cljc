(ns ^:no-doc replicant.hiccup-headers
  #?(:cljs (:require-macros [replicant.hiccup-headers])))

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
    `(let [pt# ~parsed-tag]
       (doto pt#
         (.push (get-key pt# ~attrs))
         (.push ~attrs)
         (.push ~children)
         (.push ~ns)
         (.push ~sexp)
         (.push nil)
         (.push nil)))
    `(let [pt# ~parsed-tag]
       (-> pt#
           (conj (get-key pt# ~attrs))
           (conj ~attrs)
           (conj ~children)
           (conj ~ns)
           (conj ~sexp)
           (conj nil)
           (conj nil)))))

(defmacro create-text-node [text]
  (if (:ns &env)
    `(let [text# ~text] (js/Array. nil nil nil nil nil nil nil text# text# nil))
    `(let [text# ~text] [nil nil nil nil nil nil nil text# text# nil])))

(defmacro update-attrs [headers & args]
  (if (:ns &env)
    `(let [headers# ~headers]
       (aset headers# 4 (~(first args) (aget headers# 4) ~@(rest args)))
       headers#)
    `(update ~headers 4 ~@args)))

(defmacro from-alias [alias headers]
  (if (:ns &env)
    `(let [hh# ~headers
           alias# ~alias]
       (when hh#
         (doto hh#
           (aset 3 (or (rkey alias#) (rkey hh#)))
           (aset 6 (html-ns alias#))
           (aset 7 (sexp hh#))
           (aset 9 (sexp alias#)))))
    `(let [hh# ~headers
           alias# ~alias]
       (when hh#
         (-> hh#
             (assoc 3 (or (rkey alias#) (rkey hh#)))
             (assoc 6 (html-ns alias#))
             (assoc 7 (sexp hh#))
             (assoc 9 (sexp alias#)))))))
