(ns kaocha.plugin.profiling.suggested-partitions
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [kaocha.output :as output]))

(defn- dbg [{:keys [debug] :as m} msg]
  (some-> debug (spit (str msg "\n") :append true)))

(defn suggest-partitions
  "Returns a suggested number of test partitions based on previous results."
  [{:keys [input-file default-partitions min-partitions max-partitions target-partition-minutes partition-strategy]}]
  {:post [(pos-int? %)]}
  (assert input-file "Must provide :input-file")
  (assert default-partitions "Must provide :default-partitions")
  (assert min-partitions "Must provide :min-partitions")
  (assert max-partitions "Must provide :max-partitions")
  (assert target-partition-minutes "Must provide :target-partition-minutes")
  (assert (= :var-time partition-strategy) "Must provide :partition-strategy must be :var-time")
  (let [base (when (.exists (io/file input-file))
               (-> input-file slurp edn/read-string))
        suggested-partitions (if-not base 
                               default-partitions
                               ;;FIXME this doesn't seem to work
                               (let [max-partitions (or max-partitions 10)
                                     _ (assert (pos? max-partitions))
                                     total-duration-minutes (/ (apply +' (map :kaocha.plugin.profiling/duration
                                                                              (vals (get-in base [:results :kaocha.type/var]))))
                                                               6e10)]
                                 ;(prn "target-partition-minutes" target-partition-minutes)
                                 ;(prn "total-duration-minutes" total-duration-minutes)
                                 ;(prn "div" (/ total-duration-minutes target-partition-minutes)
                                 ;     (/ target-partition-minutes total-duration-minutes))
                                 ;; 100ns total duration
                                 ;; 10ns target
                                 ;; 100/10 => 10 partitions
                                 (-> (/ total-duration-minutes target-partition-minutes)
                                     Math/ceil
                                     long
                                     (max min-partitions))))]
    (min suggested-partitions max-partitions)))

;; save a dep on cheshire on jvm
(defn- json-matrix [n]
  (str "[" (apply str (interpose "," (range n))) "]"))

(defn- set-github-actions-output [{:github-actions/keys [set-matrix-output] :as m} npartitions]
  (when set-matrix-output
    (-> npartitions json-matrix
        (as-> $ (spit (System/getenv "GITHUB_OUTPUT")
                      (let [delim (random-uuid)
                            s (format "%s<<%s\n%s\n%s\n" set-matrix-output delim $ delim)]
                        (dbg m (str "output\nvvvvvv" s "^^^^^^^"))
                        s)
                      :append true)))))

(defn run [m]
  (dbg m (format "m=%s" (pr-str m)))
  (let [npartitions (suggest-partitions m)]
    (dbg m (format "npartitions=%s" npartitions))
    (set-github-actions-output m npartitions)
    (println
      (case (:print m :partitions)
        :github-actions/json-matrix (json-matrix npartitions)
        :partitions npartitions))
    0))
