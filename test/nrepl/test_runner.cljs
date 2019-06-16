(ns nrepl.test-runner
  (:require [clojure.test :refer [run-tests]]
            nrepl.bencode-test
            nrepl.core-test))

(defn run-all-tests []
  (run-tests
    'nrepl.bencode-test
    'nrepl.core-test))

(defn -main []
  (run-all-tests))
