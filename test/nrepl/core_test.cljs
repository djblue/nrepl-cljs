(ns nrepl.core-test
  (:require [clojure.test :refer [deftest is async]]
            [cljs.tools.reader :refer [read-string]]
            [nrepl.server :as server]
            [nrepl.client :as client]))

(defn repl-eval [client code]
  (.then (client/message client {:op "eval" :code code})
         #(do (:value (first %)))))

(defn literals [conn]
  (.then
   (->> ["5" 5
         "0xff" 0xff
         "5.1" 5.1
         "-2e12" -2e12
         "1/4" 1/4
         "'symbol" 'symbol
         "'namespace/symbol" 'namespace/symbol
         ":keyword" :keyword
         ":other.ns/keyword" :other.ns/keyword
         "\"string\"" "string"
         "\"string\\nwith\\r\\nlinebreaks\"" "string\nwith\r\nlinebreaks"
         "'(1 2 3)" '(1 2 3)
         "[1 2 3]" [1 2 3]
         "{1 2 3 4}" {1 2 3 4}
         "#{1 2 3 4}" #{1 2 3 4}]
        (partition 2)
        (map (fn [[literal expected]]
               (.then (repl-eval conn literal)
                      #(is (= (read-string %) expected)))))
        js/Promise.all)
   #(-> conn)))

(deftest eval-literals
  (async done
         (let [srv (server/start-server)]
           (-> srv
               (.then #(client/connect :port 7888))
               (.then #(literals %))
               (.then #(client/close %))
               (.then #(-> srv))
               (.then #(server/stop-server %))
               (.then #(done))))))

