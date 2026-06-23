(ns replicant.run-tests
  (:require [clojure.test :as t]
            [replicant.alias-test]
            [replicant.core-test]
            [replicant.hiccup-test]
            [replicant.string-test]
            [replicant.transition-test]))

;; Set a non-zero exit code on failures/errors so CI / shells detect them,
;; while leaving clojure.test's default reporting (incl. the summary) intact.
(def ^:private old-fail (get-method t/report [:cljs.test/default :fail]))
(defmethod t/report [:cljs.test/default :fail] [m]
  (set! (.-exitCode js/process) 1)
  (old-fail m))

(def ^:private old-error (get-method t/report [:cljs.test/default :error]))
(defmethod t/report [:cljs.test/default :error] [m]
  (set! (.-exitCode js/process) 1)
  (old-error m))

(t/run-tests 'replicant.alias-test
             'replicant.core-test
             'replicant.hiccup-test
             'replicant.string-test
             'replicant.transition-test)
