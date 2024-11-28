#!/usr/bin/env bb
;;./src/kaocha/plugin/profiling/suggested_partitions.clj '{:input-file "profiling1.edn" :default-partitions 5 :max-partitions 10}'
;; => 1

(ns kaocha.plugin.profiling.suggested-partitions
  (:require [clojure.edn :as edn]
            [babashka.fs :as fs]))

(defn suggest-partitions [{:keys [input-file default-partitions max-partitions]}]
  {:post [(pos-int? %)]}
  (assert input-file)
  (assert default-partitions)
  (assert max-partitions)
  (let [{:keys [suggested-partitions]} (-> input-file slurp edn/read-string)]
    (min (or suggested-partitions default-partitions)
         max-partitions)))

(defn -main [& args]
  (try (when (not= 1 (count args))
         (throw (ex-info "Must provide 1 map argument: '{:input-file \"profiling1.edn\" :default-partitions 5 :max-partitions 10}'" {})))
       (let [m (edn/read-string (first args))
             suggestion (suggest-partitions m)]
         (println suggestion)
         (System/exit 0))
       (catch Throwable e
         (.printStackTrace e)
         (System/exit 1))
       (finally (System/exit 1))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
