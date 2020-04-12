(ns nrepl.client.tty
  (:require [readline :as r]
            [nrepl.client :as c]
            [nrepl.async :as a]))

(defn -main []
  (a/let [client (c/connect :port 7888)
          ns     (atom "cljs.user")
          is-tty? (true? js/process.stdin.isTTY)
          rl (r/createInterface
              (if-not is-tty?
                #js {:input js/process.stdin}
                #js {:input js/process.stdin
                     :output js/process.stdout}))]
    (.setPrompt rl (str @ns "=> "))
    (.prompt rl)
    (.on rl "close" #(c/close client))
    (.on rl "line"
         #(a/let [res (c/message client {:op "eval" :code %})]
            (reset! ns (:ns res))
            (when is-tty?
              (println (-> res first :value)))
            (.prompt rl)))))
