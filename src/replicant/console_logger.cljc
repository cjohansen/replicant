(ns replicant.console-logger
  (:require #?(:clj [clojure.pprint :as pprint]
               :cljs [cljs.pprint :as pprint])))

(defn log [x]
  #?(:clj (println x)
     :cljs (js/console.log x)))

(defn print-heading [x]
  #?(:clj (println x)
     :cljs (js/console.group x)))

(defn close-group []
  #?(:cljs (js/console.groupEnd)))

(defn pprstr [x]
  (with-out-str (pprint/pprint x)))

(defn abbreviate-sexp [hiccup]
  (conj (vec (take 2 hiccup)) '...))

(defn report [{:keys [title message hiccup fname data]}]
  (print-heading (str "Replicant warning: " title))
  (log message)
  (when fname
    (log (str "Function: " fname)))
  (when data
    (let [formatted (pprstr data)]
      (if (< (count formatted) 80)
        (log (str "Input data: " formatted))
        (do
          (log "Input data:")
          (log formatted)))))
  (log "Offending hiccup: ")
  (log (pprstr (abbreviate-sexp hiccup)))
  (close-group))
