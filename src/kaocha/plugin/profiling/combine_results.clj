#!/usr/bin/env bb
;;./src/kaocha/plugin/profiling/combine_results.clj '{:result-files ["profiling.edn"] :output-file "profiling1.edn"}'
(ns kaocha.plugin.profiling.combine-results
  (:require [clojure.edn :as edn]
            [babashka.fs :as fs]))

;; ./bin/kaocha --write-profiling-file profiling.edn --read-profiling-file profiling.edn --focus kaocha.plugin.filter-test --partition-index 1 --partitions 5 --partition-strategy :var-time --seed 0 --target-partition-minutes 5
(defn combine-results [result-files]
  (let [forms (mapv (comp edn/read-string slurp fs/file)
                    (mapcat #(fs/glob "." %) result-files))
        _ (assert (seq forms) (pr-str result-files))
        results (mapv :results forms)
        except-results (mapv #(dissoc % :results) forms)
        _ (assert (every? #(= 1 (:kaocha.plugin.profiling/version %)) forms))
        base (assoc {} ; (first except-results)
                    :results (apply merge-with #(merge-with into %1 %2) (map :results forms)))
        {:keys [target-partition-minutes max-partitions]} (:kaocha/cli-options base)]
    (cond-> base
      target-partition-minutes (assoc :suggested-partitions
                                      (let [max-partitions (or max-partitions 10)
                                            _ (assert (pos? max-partitions))
                                            target-partition-ns (*' 60 60 1e6 target-partition-minutes)
                                            total-duration-ns (apply +' (map :kaocha.plugin.profiling/duration
                                                                             (vals (get-in base [:results :kaocha.type/var]))))]
                                        ;; 100ns total duration
                                        ;; 10ns target
                                        ;; 100/10 => 10 partitions
                                        (-> (/ total-duration-ns target-partition-ns)
                                            Math/ceil
                                            (max max-partitions)
                                            (min 1)))))))

(defn parse+run [& args]
  (assert (= 1 (count args)) (pr-str args))
  (let [{:keys [result-files output-file]} (edn/read-string (first args))]
    (spit output-file (binding [*print-length* nil
                                *print-level* nil
                                *print-namespace-maps* false]
                        (pr-str (combine-results result-files))))
    0))

(defn -main [& args]
  (try (System/exit (apply parse+run args)) 
       (catch Throwable e
         (.printStackTrace e)
         (System/exit 1))
       (finally (System/exit 1))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
