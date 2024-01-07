(ns replicant.transition)

(defn get-transition-stats [transition-duration-s]
  (loop [str (str transition-duration-s)
         n 0
         duration 0]
    (let [s (.indexOf str "s")
          ms (.indexOf str "ms")
          comma (.indexOf str ",")]
      (if (and (< s 0) (< ms 0))
        [n (unchecked-int duration)]
        (recur
         (if (< comma 0)
           ""
           (#?(:cljs .trimLeft
               :clj .trim) (.substring str (unchecked-inc-int comma))))
         (unchecked-inc-int n)
         (max duration
              (if (or (< s ms) (< ms 0))
                (* 1000 (parse-double (.substring str 0 s)))
                (parse-long (.substring str 0 ms)))))))))
