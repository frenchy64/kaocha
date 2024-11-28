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
       :color? false
       :reporter kaocha.report/documentation}
      """
    Given a file named "kaocha-profiling.edn" with:
      """ edn
      {:kaocha.plugin.profiling/version 1,
       :results {:kaocha.type/var {:my.project.a-test/test0 {:kaocha.plugin.profiling/duration 534083000,
                                                             :kaocha.testable/type :kaocha.type/var,
                                                             :kaocha.var/name my.project.a-test/test0,
                                                             :kaocha.testable/id :my.project.a-test/test0},
                                   :my.project.a-test/test1 {:kaocha.plugin.profiling/duration 27571000,
                                                             :kaocha.testable/type :kaocha.type/var,
                                                             :kaocha.var/name my.project.a-test/test1,
                                                             :kaocha.testable/id :my.project.a-test/test1},
                                   :my.project.b-test/test3 {:kaocha.plugin.profiling/duration 1452000,
                                                             :kaocha.testable/type :kaocha.type/var,
                                                             :kaocha.var/name my.project.b-test/test3,
                                                             :kaocha.testable/id :my.project.b-test/test3}
                                   :my.project.b-test/test4 {:kaocha.plugin.profiling/duration 2056000,
                                                             :kaocha.testable/type :kaocha.type/var,
                                                             :kaocha.var/name my.project.b-test/test4,
                                                             :kaocha.testable/id :my.project.b-test/test4}}}}
      """
    Given a file named "test/my/project/a_test.clj" with:
      """ clojure
      (ns my.project.a-test
        (:require [clojure.test :refer :all]))

      (deftest test0
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
    When I run `bin/kaocha --no-randomize --partition-index 0 --partitions 2 --partition-strategy :var-time --write-profiling-file current-kaocha-profiling-0.edn`
    Then the output should contain:
      """ text
      --- unit (clojure.test) ---------------------------
      my.project.a-test
        test0

      my.project.b-test
        test3

      2 tests, 2 assertions, 0 failures.
      """
    When I run `cat current-kaocha-profiling-0.edn`
    Then the EDN output should contain:
      """
      {:kaocha.plugin.profiling/version 1}
      """

  Scenario: Running tests on the second machine of two without prior timing.
    When I run `bin/kaocha --no-randomize --partition-index 1 --partitions 2 --partition-strategy :var-time --write-profiling-file current-kaocha-profiling-1.edn`
    Then the output should contain:
      """ text
      --- unit (clojure.test) ---------------------------
      my.project.a-test
        test1

      my.project.b-test
        test4

      2 tests, 2 assertions, 0 failures.
      """
    When I run `cat current-kaocha-profiling-1.edn`
    Then the EDN output should contain:
      """
      {:kaocha.plugin.profiling/version 1}
      """

  Scenario: Running tests on the first machine of two with prior timing.
    When I run `bin/kaocha --no-randomize --partition-index 0 --partitions 2 --partition-strategy :var-time --read-profiling-file kaocha-profiling.edn`
    Then the output should contain:
      """ text
      --- unit (clojure.test) ---------------------------
      my.project.a-test
        test0

      1 tests, 1 assertions, 0 failures.
      """

  Scenario: Running tests on the second machine of two with prior timing.
    When I run `bin/kaocha --no-randomize --partition-index 1 --partitions 2 --partition-strategy :var-time --read-profiling-file kaocha-profiling.edn`
    Then the output should contain:
      """ text
      --- unit (clojure.test) ---------------------------
      my.project.a-test
        test1

      my.project.b-test
        test3
        test4

      3 tests, 3 assertions, 0 failures.
      """
