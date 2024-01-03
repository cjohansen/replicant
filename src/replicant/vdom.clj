(ns replicant.vdom)

(defmacro vget [x k]
  (if (:ns &env)
    `(aget ~x ~k)
    `(nth ~x ~k)))

(defmacro tag-name [vdom]
  `(vget ~vdom 0))

(defmacro rkey [vdom]
  `(vget ~vdom 1))

(defmacro attrs [vdom]
  `(vget ~vdom 2))

(defmacro children [vdom]
  `(vget ~vdom 3))

(defmacro child-ks [vdom]
  `(vget ~vdom 4))

(defmacro sexp [vdom]
  `(vget ~vdom 5))

(defn create [tag-name attrs children child-ks sexp]
  [tag-name (:replicant/key attrs) attrs children child-ks sexp])

(defn vdom? [x]
  (vector? x))
