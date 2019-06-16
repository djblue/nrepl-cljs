(ns nrepl.test-runner
  (:require [clojure.test :refer [run-tests report successful?]]
            nrepl.bencode-test
            nrepl.core-test))

(defn run-all-tests []
  (run-tests
    'nrepl.bencode-test
    'nrepl.core-test))

(defmethod report [:cljs.test/default :end-run-tests] [m]
  (when-not (successful? m) (js/process.exit 1)))

(defn -main [] (run-all-tests))
