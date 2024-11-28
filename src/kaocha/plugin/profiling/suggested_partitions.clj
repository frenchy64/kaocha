#!/usr/bin/env bb
;; bb -m kaocha.plugin.profiling.suggested-partitions '{:input-file "profiling1.edn" :default-partitions 5 :max-partitions 10}'
;; => 1

(ns kaocha.plugin.profiling.suggested-partitions
  (:require [clojure.edn :as edn]))

(defn- dbg [{:keys [debug] :as m} msg]
  (some-> debug (spit (str msg "\n") :append true)))

(defn suggest-partitions
  "Returns a suggested number of test partitions based on previous results."
  [{:keys [input-file default-partitions max-partitions target-partition-minutes partition-strategy]}]
  {:post [(pos-int? %)]}
  (assert input-file)
  (assert default-partitions)
  (assert max-partitions)
  (let [base (-> input-file slurp edn/read-string)
        suggested-partitions (let [max-partitions (or max-partitions 10)
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
                                   (min 1)))]
    (min (or suggested-partitions default-partitions)
         max-partitions)))

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

(defn parse+run [& args]
  (when (not= 1 (count args))
    (throw (ex-info "Must provide 1 map argument: '{:input-file \"profiling1.edn\" :default-partitions 5 :max-partitions 10}'" {})))
  (run (assoc (edn/read-string (first args)) :args args)))

(defn -main [& args]
  (try (System/exit (apply parse+run args))
       (catch Throwable e
         (.printStackTrace e)
         (System/exit 1))
       (finally (System/exit 1))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
