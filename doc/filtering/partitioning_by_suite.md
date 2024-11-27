<!-- This document is generated based on a corresponding .feature file, do not edit directly -->

# Partitioning tests by suite for load balancing

An easy way to make your tests run faster is to partition the test suite such that
  each partition runs in parallel on separate machines. If you tell Kaocha which
  machine it is running on, it can automatically run the tests for the current machine.

  Kaocha can partition tests by test suite.

## Background: A simple test suite

- <em>Given </em> a file named "tests.edn" with:

``` edn
#kaocha/v1
{:tests [{:id :asuite :test-paths ["test/asuite"]}
         {:id :bsuite :test-paths ["test/bsuite"]}]
 :reporter kaocha.report/documentation}
```


- <em>Given </em> a file named "test/asuite/my/project/aa_test.clj" with:

``` clojure
(ns my.project.aa-test
  (:require [clojure.test :refer :all]))

(deftest test-a0
  (dotimes [_ 10] (is (= 1 1))))

(deftest test-a1
  (dotimes [_ 20] (is (= 1 1))))

(deftest test-a2
  (dotimes [_ 30] (is (= 1 1))))
```


- <em>Given </em> a file named "test/asuite/my/project/ab_test.clj" with:

``` clojure
(ns my.project.ab-test
  (:require [clojure.test :refer :all]))

(deftest test-a3
  (dotimes [_ 100] (is (= 1 1))))

(deftest test-a4
  (dotimes [_ 200] (is (= 1 1))))

(deftest test-a5
  (dotimes [_ 300] (is (= 1 1))))
```


- <em>Given </em> a file named "test/bsuite/my/project/ba_test.clj" with:

``` clojure
(ns my.project.ba-test
  (:require [clojure.test :refer :all]))

(deftest test-b0
  (dotimes [_ 11] (is (= 1 1))))

(deftest test-b1
  (dotimes [_ 21] (is (= 1 1))))
```


- <em>Given </em> a file named "test/bsuite/my/project/bb_test.clj" with:

``` clojure
(ns my.project.bb-test
  (:require [clojure.test :refer :all]))

(deftest test-b2
  (dotimes [_ 31] (is (= 1 1))))

(deftest test-b3
  (dotimes [_ 41] (is (= 1 1))))
```



## Running tests on the first machine of two runs :asuite.

- <em>When </em> I run `bin/kaocha --seed 0 --partition-index 0 --partitions 2 --partition-strategy :suite --reporter documentation`

- <em>Then </em> the output should contain:

``` nil
6 tests, 660 assertions, 0 failures.
```



## Running tests on the second machine of two runs :bsuite.

- <em>When </em> I run `bin/kaocha --seed 0 --partition-index 1 --partitions 2 --partition-strategy :suite --reporter documentation`

- <em>Then </em> the output should contain:

``` nil
4 tests, 104 assertions, 0 failures.
```



