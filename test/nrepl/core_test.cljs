(ns nrepl.core-test
  (:require [clojure.test :refer [deftest is async]]
            [cljs.tools.reader :refer [read-string]]
            [nrepl.async :as a]
            [nrepl.server :as server]
            [nrepl.client :as client]))

(defn repl-eval [client code]
  (a/let [res (client/message client {:op "eval" :code code})]
    (-> res first :value)))

(def examples
  [;"5" 5
   ;"0xff" 0xff
   ;"5.1" 5.1
   ;"-2e12" -2e12
   ;"1/4" 1/4
   ;"'symbol" 'symbol
   ;"'namespace/symbol" 'namespace/symbol
   ;":keyword" :keyword
   ;":other.ns/keyword" :other.ns/keyword
   ;"\"string\"" "string"
   ;"\"string\\nwith\\r\\nlinebreaks\"" "string\nwith\r\nlinebreaks"
   ;"'(1 2 3)" '(1 2 3)
   ;"[1 2 3]" [1 2 3]
   ;"{1 2 3 4}" {1 2 3 4}
   ;"#{1 2 3 4}" #{1 2 3 4}
   ])

(defn literals [conn]
  (->> examples
       (partition 2)
       (map
        (fn [[literal expected]]
          (a/let [result (repl-eval conn literal)]
            (is (= (read-string result) expected)))))
       js/Promise.all))

(deftest eval-literals
  (async done
         (a/let [srv  (server/start-server)
                 conn (client/connect :port 7888)]
           (literals conn)
           (client/close conn)
           (server/stop-server srv)
           (done))))

