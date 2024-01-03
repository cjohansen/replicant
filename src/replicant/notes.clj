(ns replicant.notes)



(defn get-move-positions [new-xs old-xs]
  (let [n (same-head-n new-xs old-xs)
        old-xs (drop n old-xs)]
    (loop [xs (seq (drop n new-xs))
           i 0
           res {:new {} :old {}}]
      (if (nil? xs)
        res
        (let [x (first xs)
              idx (index-of #(same? x %) old-xs)]
          (recur
           (next xs)
           (inc i)
           (cond-> res
             (and (<= 0 idx) (not= idx i))
             (-> (update :new assoc (+ n i) (+ idx n))
                 (update :old assoc (+ idx n) (+ n i))))))))))



(time
 (doseq [i (range 1000000)]
   (let [new-styles {:background "red"
                     :color "white"
                     :font-size 12}
         old-styles {:background "red"
                     :color "white"
                     :font-size 12
                     :font-weight "bold"}]
     (into (set (keys new-styles)) (keys old-styles)))))

(time
 (doseq [i (range 1000000)]
   (let [new-styles {:background "red"
                     :color "white"
                     :font-size 12}
         old-styles {:background "red"
                     :color "white"
                     :font-size 12
                     :font-weight "bold"}]
     (set (concat (keys new-styles) (keys old-styles))))))


(defn update-children2 [impl el new old]
  (let [r (:renderer impl)
        old-children (get-children old (nth old hiccup-ns))
        n-children (count old-children)]
    (reduce
     (fn [[n [old-child & old-children] new-unknowns old-unknowns] new-child]
       (cond
         ;; Unchanged, nothing to see here
         (= old-child new-child)
         [(+ 1 n) old-children]

         ;; Append new node
         (nil? old-child)
         (let [child (create-node impl new-child)]
           (if (<= n-children n)
             (r/append-child r el child)
             (r/insert-before r el child (r/get-child r el n)))
           [(+ 1 n) old-children])))
     [0 (seq old-children)]
     (get-children new (nth new hiccup-ns)))))
