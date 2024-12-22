(ns kaocha.plugin.partition
  (:require [clojure.edn :as edn]
            [kaocha.plugin :as plugin :refer [defplugin]]
            [kaocha.output :as output]
            [kaocha.result :as result]
            [kaocha.plugin.profiling.suggested-partitions :as suggested-partitions]
            [kaocha.plugin.profiling.combine-results :as combine-results]
            [kaocha.testable :as testable]
            [slingshot.slingshot :refer [throw+]]))

(defn weighted-partition
  "Partition collection into npartitions partitions. Will attempt
  to create equally weighted partitions according to weights (weights
  defaults to 1 for all elements). Partitions are reproducible but
  the order of items in coll is not preserved."
  ([npartitions coll] (weighted-partition npartitions coll nil))
  ([npartitions coll weights]
   {:pre [(pos-int? npartitions)
          (vector? coll)
          (or (nil? weights)
              (vector? weights))]}
   (let [weights (or weights (into [] (repeat (count coll) 1)))
         _ (assert (= (count coll) (count weights)))
         heaviest (reduce-kv (fn [m k v]
                               (update m v (fnil conj []) k))
                             (sorted-map-by (comp - compare)) weights)
         ;; allocate largest weighing remaining element to the smallest weighing partition (left-most if tied)
         partitioned (reduce (fn [acc idx]
                               (let [idx-weight (weights idx)
                                     smallest-partition (apply min-key :weight
                                                               ;; maintain order for equal weights
                                                               (rseq acc))]
                                 (update acc (:partition-idx smallest-partition)
                                         (fn [p]
                                           (-> p
                                               (update :partition-indices conj idx)
                                               (update :partition conj (nth coll idx))
                                               (update :weight +' idx-weight))))))
                             (mapv #(do {:partition-idx %
                                         :partition-indices []
                                         :partition []
                                         :weight 0})
                                   (range npartitions))
                             (mapcat identity (vals heaviest)))
         partitions (mapv :partition partitioned)]
     (assert (= (apply + (map count partitions)) (count coll))
             [partitions coll])
     (assert (= (sort (mapcat :partition-indices partitioned))
                (range (count coll))))
     (assert (every? (zipmap coll (repeat true)) (mapcat identity partitions)))
     partitions)))

(defn nth-weighted-partition
  ([partition-index npartitions coll]
   (nth-weighted-partition partition-index npartitions coll nil))
  ([partition-index npartitions coll weights]
   (-> (weighted-partition npartitions coll weights)
       (nth partition-index))))

(defn enabled-tests [test-plan]
  (->> test-plan
       testable/test-seq 
       (map ::testable/id)
       sort
       vec))

(defn record-enabled-tests [config]
  (update config ::expected-enabled-tests #(if %
                                             (throw (ex-info "Already recorded enabled tests" {}))
                                             (enabled-tests config))))

(defn partition-suites-by-suite [{{:keys [partition-strategy partition-index partitions]} ::partition
                                  suites :kaocha/tests :as config}]
  (case partition-strategy
    ;;TODO :suite-time
    :suite (let [enabled-suites (->> suites
                                     (keep #(when-not (::testable/skip %)
                                              (::testable/id %)))
                                     ;; must be sorted!
                                     sort
                                     vec)
                 suites-for-this-partition (set (nth-weighted-partition partition-index partitions enabled-suites))
                 suites (mapv (fn [suite]
                                (cond-> suite
                                  (not (suites-for-this-partition (::testable/id suite)))
                                  (assoc ::testable/skip true)))
                              suites)]
             (-> config
                 (assoc :kaocha/tests suites)
                 record-enabled-tests))
    config))

;;perhaps profiling plugin could assoc prior results into the actual test-plan
(defn test-weights [{:kaocha.plugin.profiling/keys [prior-profiling] :as test-plan}
                    enabled-test-ids]
  (if-not prior-profiling
    (println "Should provide profiling results via --read-profiling-file with :kaocha.plugin/profiling plugin, none found.")
    (let [var->duration (not-empty
                          (into {} (map (fn [[k v]]
                                          (assert (map? v))
                                          (let [weight (:kaocha.plugin.profiling/duration v)]
                                            (assert (<= 0 weight) (pr-str weight))
                                            [k weight])))
                                (-> prior-profiling :results :kaocha.type/var)))
          average-duration (when var->duration
                             (/ (apply +' (vals var->duration)) (count var->duration)))
          default-duration (or average-duration 1)]
      (into {} (map (fn [id]
                      [id (get var->duration id default-duration)]))
            enabled-test-ids))))

(defn- skip-tests [test-plan test-ids-to-skip]
  {:pre [(set? test-ids-to-skip)]}
  (if-some [tests (:kaocha.test-plan/tests test-plan)]
    (assoc test-plan
           :kaocha.test-plan/tests
           (->> tests
                (map #(-> %
                          (cond->
                            (test-ids-to-skip (::testable/id %))
                            (assoc ::testable/skip true))
                          (skip-tests test-ids-to-skip)))))
    test-plan))

(defn assert-deterministic-partitioning [{::keys [expected-enabled-tests] :as config}]
  (when (::partition config)
    (let [actual-enabled-tests (enabled-tests config)]
      (when-not (= expected-enabled-tests actual-enabled-tests)
        (throw (ex-info "Nondeterministic test partitioning detected"
                        {:keys (keys config)
                         :expected expected-enabled-tests
                         :actual actual-enabled-tests}))))))

(defn partition-test-plan-by-test [{{:keys [partition-strategy partition-index partitions]} ::partition :as test-plan}]
  (case partition-strategy
    ;;TODO :ns, :ns-time
    (:var :var-time)
    (let [;; must be sorted!
          enabled-ids (enabled-tests test-plan)
          id->weight (case partition-strategy
                       :var-time (test-weights test-plan enabled-ids)
                       :var nil)
          test-plan (cond-> test-plan
                      id->weight (assoc ::id->weight id->weight))
          test-ids-to-skip (into #{} cat
                                 (assoc (weighted-partition partitions enabled-ids (some-> id->weight (mapv enabled-ids)))
                                        partition-index []))
          test-plan (skip-tests test-plan test-ids-to-skip)]
      (record-enabled-tests test-plan))
    test-plan))

;;TODO must run after kaocha.plugin/filter
(defplugin kaocha.plugin/partition
  (main [config]
    (cond
      (:print-suggested-partitions config) (suggested-partitions/run (:print-suggested-partitions config))
      (:combine-partitioned-results config) (combine-results/run (:combine-partitioned-results config))))
  (cli-options [opts]
    (let [parse #(keyword (if (= \: (first %)) (subs % 1) %))
          parse-int #(Integer/parseInt %)
          assoc-partition-config (fn [m k v] (assoc-in m [::partition k] v))]
      (conj opts
            [nil  "--print-suggested-partitions MAP"  "Print the suggested number of test partitions based on prior timing."
             :parse-fn (fn [s]
                         (let [v (edn/read-string s)]
                           (when-not (map? v)
                             (output/error "Must provide 1 map argument: --print-suggested-partitions '{:input-file \"profiling1.edn\" :default-partitions 5 :max-partitions 10}'")
                             (throw+ {:kaocha/early-exit 253}))
                           v))]
            [nil  "--combine-partitioned-results MAP"   "Combine and verify results for a partitioned test run."
             :parse-fn (fn [s]
                         (let [v (edn/read-string s)]
                           (when-not (map? v)
                             (output/error "Must provide 1 map argument: --combine-partitioned-results'")
                             (throw+ {:kaocha/early-exit 253}))
                           v))]
            [nil  "--partition-index NAT-INT"    "Zero-based index of the partition of the test suite to run."
             :parse-fn parse-int]
            [nil  "--partitions POS-INT"         "The number of partitions to divide the test suite into."
             :parse-fn parse-int]
            [nil  "--partition-strategy STRING"
             "Approach to partition tests by. :suite by test suite, :var{-time} by var {timing}. Chooses fastest strategy by default."
             :parse-fn (comp #(do (assert (#{:var :var-time :suite} %) (str "Bad --partition-strategy: " (pr-str %)))
                                  %)
                             parse)]
            [nil  "--target-partition-minutes NUMBER"  "Target number of minutes in which to run all tests. Use to partition future runs."
             :parse-fn parse-int]
            [nil  "--max-partitions NUMBER"  "Maximum number of partitions to use in order meet --partition-target-minutes. Default: 10"
             :parse-fn parse-int])))

  (config [config]
    (let [{:keys [partitions partition-index partition-strategy target-partition-minutes max-partitions]} (:kaocha/cli-options config)
          choose-partition (when (or partition-index partitions)
                             (fn [config]
                               ;;TODO assert at parse time
                               (when-not (and partition-index partitions)
                                 (throw (ex-info "Must provide --partition-index and --partition together"
                                                 {})))
                               (when-not (pos? partitions)
                                 (throw (ex-info "--partitions must be positive"
                                                 {})))
                               (when-not (and (<= 0 partition-index)
                                              (< partition-index partitions))
                                 (throw (ex-info "--partition-index must be non-negative and less than --partitions"
                                                 {})))
                               ;;TODO group at parse time?
                               (assoc config ::partition
                                      {:partition-strategy (or partition-strategy
                                                               ;;TODO choose fastest strategy based on test-plan and/or timings
                                                               :var)
                                       :partition-index partition-index
                                       :partitions partitions
                                       :target-partition-minutes target-partition-minutes
                                       :max-partitions (or max-partitions 10)})))]
      (cond-> config
        choose-partition choose-partition)))
  (pre-load [config] (partition-suites-by-suite config))
  (post-load [test-plan] (partition-test-plan-by-test test-plan))
  (post-run [{{:keys [partition-strategy]} ::partition ::keys [id->weight suites-for-this-partition] :as test-plan}]
    (assert-deterministic-partitioning test-plan)
    (when (and (= :var-time partition-strategy) id->weight (result/failed? test-plan))
      (print "\nPartitioned vars with weights " (pr-str id->weight)))
    test-plan))
