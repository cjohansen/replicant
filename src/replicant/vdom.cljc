(ns replicant.vdom
  (:require [replicant.hiccup :as h])
  #?(:cljs (:require-macros [replicant.vdom])))

(def id (volatile! 0))

(defmacro vget [x k]
  (if (:ns &env)
    `(aget ~x ~k)
    `(nth ~x ~k)))

(defmacro tag-name [vdom]
  `(vget ~vdom 0))

(defmacro rkey [vdom]
  `(vget ~vdom 1))

(defmacro classes [vdom]
  `(vget ~vdom 2))

(defmacro attrs [vdom]
  `(vget ~vdom 3))

(defmacro children [vdom]
  `(vget ~vdom 4))

(defmacro child-ks [vdom]
  `(vget ~vdom 5))

(defmacro async-unmount? [vdom]
  `(vget ~vdom 6))

(defmacro sexp [vdom]
  `(vget ~vdom 7))

(defmacro text [vdom]
  `(vget ~vdom 8))

(defmacro unmount-id [vdom]
  `(vget ~vdom 9))

(defmacro ident [vdom]
  `(vget ~vdom 10))

(defmacro original-sexp [vdom]
  `(let [vdom# ~vdom]
     (or (vget vdom# 11) (vget vdom# 7))))

(defmacro n-children [vdom]
  `(vget ~vdom 12))

(defmacro mark-unmounting [vdom]
  (if (:ns &env)
    `(let [vdom# ~vdom]
       (aset vdom# 9 (vswap! id inc))
       vdom#)
    `(assoc ~vdom 9 (vswap! id inc))))

(defmacro create-text-node [text]
  (if (:ns &env)
    `(let [text# ~text]
       (js/Array. nil nil nil nil nil nil false text# text# nil nil nil))
    `(let [text# ~text]
       [nil nil nil nil nil nil false text# text# nil nil nil])))

(defmacro from-hiccup [headers attrs children children-ks n-children]
  (if (:ns &env)
    `(let [headers# ~headers]
       (js/Array. (h/tag-name headers#) (h/rkey headers#) (h/classes headers#) ~attrs ~children ~children-ks (boolean (:replicant/unmounting (h/attrs headers#))) (h/sexp headers#) nil nil (h/ident headers#) (h/alias-sexp headers#) ~n-children))
    `(let [headers# ~headers]
       [(h/tag-name headers#) (h/rkey headers#) (h/classes headers#) ~attrs ~children ~children-ks (boolean (:replicant/unmounting (h/attrs headers#))) (h/sexp headers#) nil nil (h/ident headers#) (h/alias-sexp headers#) ~n-children])))
