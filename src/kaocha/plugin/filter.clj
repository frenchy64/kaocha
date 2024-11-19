(ns kaocha.plugin.filter
  (:require [kaocha.plugin :as plugin :refer [defplugin]]
            [kaocha.testable :as testable]
            [kaocha.plugin.randomize :as randomize]
            [clojure.set :as set]
            [clojure.walk :as walk]
            [kaocha.output :as output]))

(defn- accumulate [m k v]
  (update m k (fnil conj []) v))

(defn matches? [{:as testable
                 ::testable/keys [id meta aliases]}
                filters meta-filters]
  (or (some #(= (keyword %) id) filters)
      (some #(= (str %) (namespace id)) filters)
      (seq (filter (set aliases) (map keyword filters)))
      (some #(get meta (keyword %)) meta-filters)))

(defn filters [{:as testable
                :kaocha.filter/keys [skip focus skip-meta focus-meta]}]
  {:skip skip
   :focus focus
   :skip-meta skip-meta
   :focus-meta focus-meta})

(defn merge-filters [f1 f2]
  {:skip       (concat (:skip f1) (:skip f2))
   :skip-meta  (concat (:skip-meta f1) (:skip-meta f2))
   :focus      (if (seq (:focus f2))
                 (:focus f2)
                 (:focus f1))
   :focus-meta (if (seq (:focus-meta f2))
                 (:focus-meta f2)
                 (:focus-meta f1))})

(defn truthy-keys [m]
  (map key (filter val m)))

(defn remove-missing-metadata-keys [focus-meta testable]
  (let [used-meta  (into #{}
                         (comp
                          (map (comp truthy-keys ::testable/meta))
                          cat
                          (map keyword))
                         (testable/test-seq testable))
        focus-meta (set focus-meta)
        unused     (set/difference focus-meta used-meta)]
    (doseq [u unused]
      (output/warn "No tests found with metadata key " u ". Ignoring --focus-meta " u "."))
    (set/difference focus-meta unused)))

(defn filter-testable [testable opts]
  (let [{:as opts
         :keys [skip focus skip-meta focus-meta]} (merge-filters opts (filters testable))
        recurse   (fn recurse
                    ([]
                     (recurse opts))
                    ([opts]
                     (cond-> testable
                       (:kaocha.test-plan/tests testable)
                       (update :kaocha.test-plan/tests (partial mapv #(filter-testable % opts))))))
        skip-test (fn []
                    (assoc testable ::testable/skip true))]
    (cond
      (or (seq focus) (seq focus-meta))
      (cond
        (::testable/load-error testable)
        testable

        ;; - positive
        ;;   - foo ^:a
        ;;   - bar ^:b
        ;; --focus positive --focus-meta a
        ;;
        (matches? testable focus focus-meta)
        (recurse (dissoc opts :focus :focus-meta))

        ;; Is it better to split this?
        ;; e.g. focus-meta inside

        ;; (matches? testable focus #{})
        ;; (recurse (dissoc opts :focus))

        ;; (matches? testable #{} focus-meta)
        ;; (recurse (dissoc opts :focus-meta))

        (some #(matches? % focus focus-meta) (testable/test-seq testable))
        (recurse)

        :else
        (skip-test))

      (matches? testable skip skip-meta)
      (skip-test)

      (:kaocha.test-plan/tests testable)
      (recurse)

      :else
      testable)))

(defn partition-into
  "Partition collection into npartitions partitions. Will attempt
  to create equally weighted partitions according to weights (weights
  defaults to 1 for all elements). Partitions are reproducible but
  the order of items in coll is not preserved."
  ([npartitions coll] (partition-into npartitions coll nil))
  ([npartitions coll weights]
   {:pre [(pos-int? npartitions)
          (vector? coll)
          (or (nil? weights)
              (vector? weights))]}
   (let [weights (or weights (into [] (repeat (count coll) 1)))
         _ (prn "weights" weights)
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

(defn partition-suites-by-suite [{:keys [partition-strategy partition-index partitions]} suites]
  (case partition-strategy
    ;;TODO :suite-time
    :suite (let [suites (vec suites)
                 enabled-suites (into [] (keep-indexed
                                           (fn [i suite]
                                             (when-not (:kaocha.testable/skip suite)
                                               i)))
                                      suites)
                 suites-for-this-partition (set (nth (partition-into partitions enabled-suites)
                                                     partition-index))]
             (prn "suites-for-this-partition" suites-for-this-partition)
             (mapv (fn [suite]
                     (assoc suite :kaocha.testable/skip (not (suites-for-this-partition suite))))
                   suites))
    suites))

;;perhaps profiling plugin could assoc prior results into the actual test-plan
(defn test-weights [{:kaocha.plugin.profiling/keys [prior-profiling] :as test-plan}
                    enabled-tests]
  (assert prior-profiling "Must provide profiling results via --read-profiling-file with :kaocha.plugin/profiling plugin.")
  (prn prior-profiling)
  (let [var->duration (not-empty
                        (into {} (map (fn [[k v]]
                                        (assert (= 1 (count v)) (str "Multiple results for " (pr-str k) ": " (pr-str v)))
                                        (let [weight (-> v first :kaocha.plugin.profiling/duration)]
                                          (assert (nat-int? weight) (pr-str weight))
                                          [k weight])))
                              (:kaocha.type/var prior-profiling)))
        average-duration (when var->duration
                           (/ (apply + (vals var->duration)) (count var->duration)))]
    (mapv (fn [{:keys [id path]}]
            (var->duration id average-duration))
          enabled-tests)))

(defn partition-test-plan-by-test [test-plan {:keys [partition-strategy partition-index partitions] :as partition-conf}]
  (prn "partition-suite" partition-conf)
  (case partition-strategy
    (:var :var-time)
    (let [randomly-randomized? (and (::randomize/randomized test-plan)
                                    (let [b (::randomize/randomized-seed? test-plan)]
                                      (assert (boolean? b))
                                      b))
          _ (when randomly-randomized?
              (output/warn "Please either provide consistent --seed to all partitions or move :kaocha.plugin/filter before :kaocha.plugin/randomize"))
          test-plan (cond-> test-plan
                      randomly-randomized? randomize/straight-sort)
          ;; make indexable
          test-plan (walk/postwalk (fn [s]
                                     (cond-> s
                                       (sequential? s) vec))
                                   test-plan)
          enabled-test-paths (fn enabled-test-paths
                               [testable path]
                               (if (::testable/skip testable)
                                 []
                                 (if-some [tests (:kaocha.test-plan/tests testable)]
                                   (into [] (comp (map-indexed #(enabled-test-paths %2 (conj path :kaocha.test-plan/tests %1)))
                                                  cat)
                                         tests)
                                   (cond-> []
                                     (and (map? testable) (not (::testable/skip testable)))
                                     ;; TODO is this always a var test?
                                     (conj {:path path :id (doto (:kaocha.testable/id testable)
                                                             assert)})))))
          enabled-tests (enabled-test-paths test-plan [])
          _ (prn "enabled-tests" enabled-tests)
          tests-to-skip (nth (partition-into partitions enabled-tests
                                             (case partition-strategy
                                               :var-time (test-weights test-plan enabled-tests)
                                               :var nil))
                             partition-index)
          _ (prn "tests-to-skip" tests-to-skip)
          test-plan (reduce (fn [test-plan {:keys [path]}]
                              (assoc-in test-plan (conj path :kaocha.testable/skip) true))
                            test-plan tests-to-skip)]
      ;; re-randomize the current partition
      (cond-> test-plan
        randomly-randomized? randomize/randomize-test-plan))
    test-plan))

(defplugin kaocha.plugin/filter
  (cli-options [opts]
    (let [parse #(keyword (if (= \: (first %)) (subs % 1) %))
          parse-int #(Integer/parseInt %)
          assoc-partition-config (fn [m k v] (assoc-in m [:kaocha.filter/partition k] v))]
      (conj opts
            [nil "--skip SYM" "Skip tests with this ID and their children."
             :parse-fn parse
             :assoc-fn accumulate]
            [nil "--focus SYM" "Only run this test, skip others."
             :parse-fn parse
             :assoc-fn accumulate]
            [nil "--skip-meta SYM" "Skip tests where this metadata key is truthy."
             :parse-fn parse
             :assoc-fn accumulate]
            [nil "--focus-meta SYM" "Only run tests where this metadata key is truthy."
             :parse-fn parse
             :assoc-fn accumulate]
            [nil  "--partition-index NAT-INT"    "Zero-based index of the partition of the test suite to run."
             :parse-fn parse-int]
            [nil  "--partitions POS-INT"         "The number of partitions to divide the test suite into."
             :parse-fn parse-int]
            [nil  "--partition-strategy STRING"  "Approach to partition tests by."
             :parse-fn parse])))

  (config [config]
    (let [{:keys [skip focus skip-meta focus-meta partitions partition-index partition-strategy]} (:kaocha/cli-options config)
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
                               (assoc config :kaocha.filter/partition {:partition-strategy (or partition-strategy :suite)
                                                                       :partition-index partition-index
                                                                       :partitions partitions})))]
      (cond-> config
        (seq skip)       (assoc :kaocha.filter/skip skip)
        (seq focus)      (assoc :kaocha.filter/focus focus)
        (seq skip-meta)  (assoc :kaocha.filter/skip-meta skip-meta)
        (seq focus-meta) (assoc :kaocha.filter/focus-meta focus-meta)
        choose-partition choose-partition)))

  ;; In an earlier pass already filter at the test suite level. We don't have
  ;; the full test plan yet, but if a suite itself matches any of the focus/skip
  ;; directives then we can already apply that, thus possibly preventing the
  ;; loading of suites that won't run in this test run anyway.
  (pre-load [config]
    (let [{:kaocha.filter/keys [focus focus-meta skip skip-meta]} config
          focus-suites (->> config
                            :kaocha/tests
                            (filter #(matches? % focus focus-meta))
                            (map :kaocha.testable/id)
                            set)]
      (update config
              :kaocha/tests
              (fn [suites]
                (->> (mapv (fn [suite]
                             (if (and
                                   (not (:kaocha.testable/skip suite)) ; short circuit if this has been set elsewhere, saves a few cycles
                                   (or (matches? suite skip skip-meta)
                                       (and (seq focus-suites)
                                            (not (contains? focus-suites (:kaocha.testable/id suite))))))
                               (assoc suite :kaocha.testable/skip true)
                               suite))
                           suites)
                     (partition-suites-by-suite (:kaocha.filter/partition config)))))))

  (post-load [test-plan]
    (let [{:kaocha.filter/keys [focus focus-meta]} (:kaocha/cli-options test-plan)
          config testable/*config*]
      (when (and (seq focus) (empty? (filter #(matches? % focus nil) (testable/test-seq test-plan))))
        (output/warn ":focus " focus " did not match any tests."))
      (let [test-plan (update test-plan :kaocha.filter/focus-meta remove-missing-metadata-keys test-plan)
            filter-suite (fn [suite]
                           ;; Suites may already be marked as skipped in
                           ;; kaocha.config when procssing CLI arguments, in
                           ;; that case don't do any further processing, e.g. we
                           ;; don't want to emit warnings about missing metadata
                           ;; keys.
                           (if (:kaocha.testable/skip suite)
                             suite
                             (let [suite (update suite :kaocha.filter/focus-meta remove-missing-metadata-keys test-plan)]
                               (-> suite
                                   (filter-testable {})
                                   (dissoc :kaocha.filter/focus :kaocha.filter/focus-meta)
                                   (filter-testable (filters test-plan))))))]
        (-> test-plan
            (update :kaocha.test-plan/tests (partial map filter-suite))
            (partition-test-plan-by-test (:kaocha.filter/partition test-plan)))))))
