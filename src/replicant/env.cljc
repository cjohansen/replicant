(ns ^:no-doc replicant.env
  #?@(:squint []
      :cherry []
      :cljs [(:require-macros [replicant.env])]))

;; squint and cherry load this file when looking for macros and cannot run the
;; JVM-side macros in env.clj. Give them a with-dev-key that skips dev-key
;; injection.
#?(:squint (defmacro with-dev-key [hiccup _k] hiccup)
   :cherry (defmacro with-dev-key [hiccup _k] hiccup))
