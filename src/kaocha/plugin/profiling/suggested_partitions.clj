#!/usr/bin/env bb
;;./src/kaocha/plugin/profiling/suggested_partitions.clj '{:input-file "profiling1.edn" :default-partitions 5 :max-partitions 10}'
;; => 1

(ns kaocha.plugin.profiling.suggested-partitions
  (:require [clojure.edn :as edn]
            [cheshire.core :as json]
            [babashka.fs :as fs]))

(defn suggest-partitions [{:keys [input-file default-partitions max-partitions]}]
  {:post [(pos-int? %)]}
  (assert input-file)
  (assert default-partitions)
  (assert max-partitions)
  (let [{:keys [suggested-partitions]} (-> input-file slurp edn/read-string)]
    (min (or suggested-partitions default-partitions)
         max-partitions)))

(defn set-github-actions-output [{:github-actions/keys [set-matrix-output debug] :as m} npartitions]
  (when set-matrix-output
    (-> npartitions range json/encode
        (as-> $ (spit (System/getenv "GITHUB_OUTPUT")
                      (let [delim (random-uuid)
                            s (format "%s<<%s\n%s\n%s\n" set-matrix-output delim $ delim)]
                        (some-> debug (spit s :append true))
                        s)
                      :append true)))))

(defn run [m]
  (let [npartitions (suggest-partitions m)]
    (set-github-actions-output m npartitions)
    (println
      (case (:print m :partitions)
        :github-actions/json-matrix (-> npartitions range json/encode)
        :partitions npartitions))
    0))

(defn parse+run [& args]
  (when (not= 1 (count args))
    (throw (ex-info "Must provide 1 map argument: '{:input-file \"profiling1.edn\" :default-partitions 5 :max-partitions 10}'" {})))
  (run (edn/read-string (first args))))

(defn -main [& args]
  (try (System/exit (apply parse+run args))
       (catch Throwable e
         (.printStackTrace e)
         (System/exit 1))
       (finally (System/exit 1))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
