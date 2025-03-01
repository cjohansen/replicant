(ns ^:no-doc replicant.errors
  #?(:clj (:require [replicant.env :as env]))
  #?(:cljs (:require-macros [replicant.errors])))

(defn catch-exceptions? []
  #?(:clj (env/enabled? :replicant/catch-exceptions? (not (env/dev?)))))

(defmacro log [message ctx error]
  (if (:ns &env)
    `(do
       (js/console.log ~message)
       (when-let [ctx# ~ctx]
         (run! (fn [[k# v#]]
                 (js/console.log
                  (pr-str k#)
                  (if (or (coll? v#) (keyword? v#))
                    (pr-str v#)
                    v#)))
               ctx#))
       (js/console.error ~error))
    `(do
       (println ~message)
       (when-let [ctx# ~ctx]
         (prn ctx#))
       (prn ~error))))

(defmacro ^{:indent 2} with-error-handling [message ctx body & [catch-clause]]
  (if (catch-exceptions?)
    (let [e `e#]
      `(try
         ~body
         (catch ~(if (:ns &env)
                   :default
                   'Exception) ~e
           (log (str "Threw exception while " ~message) ~ctx ~e)
           ~(let [[binding & exprs] (drop 2 catch-clause)]
              (when binding
                `(let [~binding ~e]
                   ~@exprs))))))
    body))
