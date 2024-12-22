(ns kaocha.plugin.partition-test
  (:require [clojure.test :refer [deftest is]]
            [kaocha.plugin.partition :as p]))

(deftest weighted-partition-test
  (is (= [["a"] ["b"] ["c"] ["d"]] (p/weighted-partition 4 ["a" "b" "c" "d"])))
  (is (= [["a"] ["b"] ["c"] ["d"]] (p/weighted-partition 4 ["a" "b" "c" "d"] [2 1 1 1])))
  (is (= [["a" "d"] ["b"] ["c"]] (p/weighted-partition 3 ["a" "b" "c" "d"] [1 1 1 1])))
  (is (= [["a" "d"] ["b"] ["c"]] (p/weighted-partition 3 ["a" "b" "c" "d"] [1 1 1 1])))
  (is (= [["b"] ["a" "d"] ["c"]] (p/weighted-partition 3 ["a" "b" "c" "d"] [1 2 1 1])))
  (is (= [["a" "d"] ["b"] ["c"]] (p/weighted-partition 3 ["a" "b" "c" "d"])))
  (is (= [["a"] ["b"] ["c"] ["d"] []] (p/weighted-partition 5 ["a" "b" "c" "d"]))))

(deftest nth-weighted-partition-test
  (is (= ["a"] (p/nth-weighted-partition 0 4 ["a" "b" "c" "d"])))
  (is (= ["c"] (p/nth-weighted-partition 2 4 ["a" "b" "c" "d"] [2 1 1 1])))
  (is (= ["a" "d"] (p/nth-weighted-partition 0 3 ["a" "b" "c" "d"] [1 1 1 1])))
  (is (= ["a" "d"] (p/nth-weighted-partition 0 3 ["a" "b" "c" "d"] [1 1 1 1])))
  (is (= ["a" "d"] (p/nth-weighted-partition 1 3 ["a" "b" "c" "d"] [1 2 1 1])))
  (is (= ["a" "d"] (p/nth-weighted-partition 0 3 ["a" "b" "c" "d"])))
  (is (= [] (p/nth-weighted-partition 4 5 ["a" "b" "c" "d"]))))
