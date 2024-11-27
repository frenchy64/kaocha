Feature: Partitioning tests by namespace for load balancing

  An easy way to make your tests run faster is to partition the test suite such that
  each partition runs in parallel on separate machines. If you tell Kaocha which
  machine it is running on, it can automatically run the tests for the current machine.

  Background: A simple test suite
    Given a file named "test/my/project/a_test.clj" with:
      """clojure
      (ns my.project.a-test
        (:require [clojure.test :refer :all]))

      (deftest test0
        (is (= 1 1)))

      (deftest test1
        (is (= 2 2)))
      """
    Given a file named "test/my/project/b_test.clj" with:
      """clojure
      (ns my.project.b-test
        (:require [clojure.test :refer :all]))

      (deftest test3
        (is (= 3 3)))

      (deftest test4
        (is (= 4 4)))
      """

  Scenario: Running tests on the first machine of two.
    When I run `bin/kaocha --seed 0 --partition-index 0 --partitions 2 --partition-strategy :ns --reporter documentation`
    Then the output should contain:
      """
      --- unit (clojure.test) ---------------------------
      my.project.a-test
        test0
        test1

      2 tests, 2 assertions, 0 failures.
      """

  Scenario: Running tests on the second machine of two.
    When I run `bin/kaocha --seed 0 --partition-index 1 --partitions 2 --partition-strategy :ns --reporter documentation`
    Then the output should contain:
      """
      --- unit (clojure.test) ---------------------------
      my.project.b-test
        test3
        test4

      2 tests, 2 assertions, 0 failures.
      """
