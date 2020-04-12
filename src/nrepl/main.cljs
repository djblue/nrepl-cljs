(ns nrepl.main
  (:require [nrepl.repl :as r]
            [nrepl.async :as a]
            [nrepl.server :as s]))

(defn -main []
  (a/let [result (r/eval "(ns cljs.user (:require [System]))" "cljs.user")]
    (println result))
  (s/start-server r/eval))
