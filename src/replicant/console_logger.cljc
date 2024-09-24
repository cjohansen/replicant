(ns replicant.console-logger
  (:require #?(:clj [clojure.pprint :as pprint]
               :cljs [cljs.pprint :as pprint])
            [clojure.walk :as walk]))

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

(defn scrub-sexp [sexp]
  (walk/prewalk
   (fn [x]
     (if (map? x)
       (->> x
            (remove #(:replicant/internal (meta (val %))))
            (into {}))
       x))
   sexp))

(defn abbreviate-sexp [hiccup]
  (let [scrubbed (scrub-sexp hiccup)
        len (count (pr-str scrubbed))]
    (if (< len 100)
      scrubbed
      (conj (vec (take 2 scrubbed)) '...))))

(defn report [{:keys [title message hiccup fname alias data]}]
  (print-heading (str "Replicant warning: " title))
  (log message)
  (when fname
    (log (str "Function: " fname)))
  (when alias
    (log (str "Alias: " alias)))
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
