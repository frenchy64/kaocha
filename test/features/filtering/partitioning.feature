Feature: Partitioning tests for load balancing

  An easy way to make your tests run faster is to partition the test suite such that
  each partition runs in parallel on separate machines. If you tell Kaocha which
  machine it is running on, it can automatically run the tests for the current machine.

  Background: A simple test suite
    Given a file named "test/my/project/sample_test.clj" with:
      """clojure
      (ns my.project.sample-test
        (:require [clojure.test :refer :all]))

      (deftest some-test
        (is (= 1 1)))

      (deftest other-test
        (is (= 2 2)))
      """

  Scenario: Running tests on the first machine of two.
    When I run `bin/kaocha --partition-index 0 --partitions 2 --reporter documentation`
    Then the output should contain:
      """
      --- unit (clojure.test) ---------------------------
      my.project.sample-test
        other-test

      1 tests, 1 assertions, 0 failures.
      """

  Scenario: Running tests on the second machine of two.
    When I run `bin/kaocha --partition-index 1 --partitions 2  --reporter documentation`
    Then the output should contain:
      """
      --- unit (clojure.test) ---------------------------
      my.project.sample-test
        other-test

      1 tests, 1 assertions, 0 failures.
      """
