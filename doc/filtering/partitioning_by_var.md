<!-- This document is generated based on a corresponding .feature file, do not edit directly -->

# Partitioning tests by var for load balancing

An easy way to make your tests run faster is to partition the test suite such that
  each partition runs in parallel on separate machines. If you tell Kaocha which
  machine it is running on, it can automatically run the tests for the current machine.

## Background: A simple test suite

- <em>Given </em> a file named "test/my/project/a_test.clj" with:

``` clojure
(ns my.project.a-test
  (:require [clojure.test :refer :all]))

(deftest test0
  (is (= 1 1)))

(deftest test1
  (is (= 2 2)))
```


- <em>Given </em> a file named "test/my/project/b_test.clj" with:

``` clojure
(ns my.project.b-test
  (:require [clojure.test :refer :all]))

(deftest test3
  (is (= 3 3)))

(deftest test4
  (is (= 4 4)))
```



## Running tests on the first machine of two.

- <em>When </em> I run `bin/kaocha --seed 0 --partition-index 0 --partitions 2 --partition-strategy :var --reporter documentation`

- <em>Then </em> the output should contain:

``` nil
--- unit (clojure.test) ---------------------------
my.project.a-test
  test1

my.project.b-test
  test4

2 tests, 2 assertions, 0 failures.
```



## Running tests on the second machine of two.

- <em>When </em> I run `bin/kaocha --seed 0 --partition-index 1 --partitions 2 --partition-strategy :var --reporter documentation`

- <em>Then </em> the output should contain:

``` nil
--- unit (clojure.test) ---------------------------
my.project.a-test
  test0

my.project.b-test
  test3

2 tests, 2 assertions, 0 failures.
```



