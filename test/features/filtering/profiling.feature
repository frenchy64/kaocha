Feature: Profiling tests

  Kaocha can write timing results to a file.

  Background: A simple test suite
    Given a file named "test/my/project/sample_test.clj" with:
      """clojure
      (ns my.project.sample-test
        (:require [clojure.test :refer :all]))

      (deftest fast-test
        (Thread/sleep 10)
        (is (= 1 1)))

      (deftest slow-test
        (Thread/sleep 1000)
        (is (= 2 2)))
      """

  Scenario: Writing profiling results to a file
    When I run `bin/kaocha --plugin profiling --write-profiling-file profiling.edn`
    Then the output should contain:
      """
      2 tests, 2 assertions, 0 failures.
      """
    Given a file named "test_profiling.clj" with:
      """clojure
      (ns test-profiling
        (:require [clojure.edn :as edn]))

      (let [{:my.project.sample-test/keys [fast-test slow-test]} (-> "profiling.edn" slurp edn/read-string :results :kaocha.type/var)
            fast-test-min-duration 1e7
            slow-test-min-duration 1e9
            duration (comp :kaocha.plugin.profiling/duration first)]
        (and (or (<= fast-test-min-duration (duration fast-test) (dec slow-test-min-duration))
                 (println "Bad fast-test duration:" (duration fast-test)))
             (or (<= slow-test-min-duration (duration slow-test))
                 (println "Bad slow-test duration:" (duration slow-test)))
             (println "fast-test was faster than slow-test")))
      (System/exit 0)
      """
    When I run `bb -f test_profiling.clj`
    Then the output should contain:
      """
      fast-test was faster than slow-test
      """
