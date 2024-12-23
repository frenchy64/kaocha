<!-- This document is generated based on a corresponding .feature file, do not edit directly -->

# Profiling tests

Kaocha can write timing results to a file.

## Background: A simple test suite

- <em>Given </em> a file named "test/my/project/sample_test.clj" with:

``` clojure
(ns my.project.sample-test
  (:require [clojure.test :refer :all]))

(deftest fast-test
  (Thread/sleep 10)
  (is (= 1 1)))

(deftest slow-test
  (Thread/sleep 1000)
  (is (= 2 2)))
```



## Writing profiling results to a file

- <em>When </em> I run `bin/kaocha --plugin profiling --write-profiling-file profiling.edn`

- <em>Then </em> the output should contain:

``` nil
2 tests, 2 assertions, 0 failures.
```


- <em>Given </em> a file named "test_profiling.clj" with:

``` clojure
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
```


- <em>When </em> I run `clojure test_profiling.clj`

- <em>Then </em> the exit-code should be 0

- <em>And </em> the output should contain:

``` nil
fast-test was faster than slow-test
```



