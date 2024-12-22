(ns kaocha.plugin.profiling.combine-results
  (:require [clojure.edn :as edn]
            [babashka.fs :as fs]))

;; ./bin/kaocha --write-profiling-file profiling.edn --read-profiling-file profiling.edn --focus kaocha.plugin.filter-test --partition-index 1 --partitions 5 --partition-strategy :var-time --seed 0 --target-partition-minutes 5
(defn combine-results [result-files]
  (let [forms (mapv (comp edn/read-string slurp fs/file)
                    (mapcat #(fs/glob "." %) result-files))
        _ (assert (seq forms) (pr-str result-files))
        results (mapv :results forms)
        _ (assert (every? #(= 1 (:kaocha.plugin.profiling/version %)) forms))
        base {:kaocha.plugin.profiling/version 1
              :results (apply merge-with #(merge-with (fn [l r]
                                                        (update l :kaocha.plugin.profiling/duration
                                                                +' (:kaocha.plugin.profiling/duration r)))
                                                      %1 %2)
                              results)}
        {:keys [target-partition-minutes max-partitions]} (-> forms first :kaocha/cli-options)]
    ;(prn "target-partition-minutes" target-partition-minutes)
    base))

(defn run [m]
  (let [{:keys [result-files output-file]} (edn/read-string m)]
    (spit output-file (binding [*print-length* nil
                                *print-level* nil
                                *print-namespace-maps* false]
                        (pr-str (combine-results result-files))))
    0))
