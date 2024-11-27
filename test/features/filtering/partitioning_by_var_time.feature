Feature: Partitioning tests by var time for load balancing

  An easy way to make your tests run faster is to partition the test suite such that
  each partition runs in parallel on separate machines. If you tell Kaocha which
  machine it is running on, it can automatically run the tests for the current machine.

  Kaocha can partition tests based on previous profiling results.

  Background: A simple test suite
    Given a file named "tests.edn" with:
      """ edn
      #kaocha/v1
      {:plugins [:profiling]
       :reporter kaocha.report/documentation}
      """
    Given a file named "test/my/project/a_test.clj" with:
      """ clojure
      (ns my.project.a-test
        (:require [clojure.test :refer :all]))

      (deftest test0
        (Thread/sleep 500)
        (is (= 1 1)))

      (deftest test1
        (is (= 2 2)))
      """
    Given a file named "test/my/project/b_test.clj" with:
      """ clojure
      (ns my.project.b-test
        (:require [clojure.test :refer :all]))

      (deftest test3
        (is (= 3 3)))

      (deftest test4
        (is (= 4 4)))
      """

  Scenario: Running tests on the first machine of two without prior timing.
    When I run `bin/kaocha --no-randomize --partition-index 0 --partitions 2 --partition-strategy :var-time --read-profiling-file kaocha-profiling.edn --write-profiling-file current-kaocha-profiling-0.edn`
    Then the output should contain:
      """ text
      --- unit (clojure.test) ---------------------------
      my.project.a-test
        test0
      my.project.b-test
        test3
      2 tests, 2 assertions, 0 failures.
      """

  Scenario: Running tests on the second machine of two without prior timing.
    When I run `bin/kaocha --no-randomize --partition-index 1 --partitions 2 --partition-strategy :var-time --read-profiling-file kaocha-profiling.edn --write-profiling-file current-kaocha-profiling-1.edn`
    Then the output should contain:
      """ text
      --- unit (clojure.test) ---------------------------
      my.project.a-test
        test1
      my.project.b-test
        test4
      2 tests, 2 assertions, 0 failures.
      """
