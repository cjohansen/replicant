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

(defmacro mark-unmounting [vdom]
  (if (:ns &env)
    `(do
       (aset ~vdom 9 (vswap! id inc))
       ~vdom)
    `(assoc ~vdom 9 (vswap! id inc))))

(defmacro create-text-node [text]
  (if (:ns &env)
    `(js/Array. nil nil nil nil nil nil false ~text ~text nil)
    `[nil nil nil nil nil nil false ~text ~text nil]))

(defmacro from-hiccup [headers attrs children children-ks]
  (if (:ns &env)
    `(js/Array. (h/tag-name ~headers) (h/rkey) (h/classes ~headers) ~attrs ~children ~children-ks (boolean (:replicant/unmounting (h/attrs ~headers))) (h/sexp ~headers))
    `[(h/tag-name ~headers) (h/rkey ~headers) (h/classes ~headers) ~attrs ~children ~children-ks (boolean (:replicant/unmounting (h/attrs ~headers))) (h/sexp ~headers) nil nil]))
