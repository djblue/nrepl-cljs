(ns nrepl.main
  (:require [nrepl.repl :as r]
            [nrepl.async :as a]
            [nrepl.server :as s]))

(defn -main []
  (a/let [result (r/eval "(ns cljs.user)" "cljs.user")]
    (s/start-server r/eval)))
