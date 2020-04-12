(ns nrepl.client.tty
  (:require [readline :as r]
            [nrepl.client :as c]
            [nrepl.async :as a]))

(def project->string
  {:nrepl  "nREPL"
   :clojure "Clojure"
   :java "Java"
   :clojurescript "ClojureScript"
   :node "Node.js"})

(defn format-welcome [info]
  (str "Connecting to nREPL at " (:host info) ":" (:port info) "\n"
       (apply
        str
        (for [[project info] (:versions info)]
          (str (project->string project) " " (:version-string info) "\n")))))

(defn -main []
  (a/let [port 7888
          host "127.0.0.1"
          client (c/connect :port port)
          info   (c/message client {:op "describe"})
          ns     (atom (get-in info [0 :aux :current-ns] "cljs.user"))
          is-tty? (true? js/process.stdin.isTTY)
          rl (r/createInterface
              (if-not is-tty?
                #js {:input js/process.stdin}
                #js {:input js/process.stdin
                     :output js/process.stdout}))
          prompt! (fn []
                    (.setPrompt rl (str @ns "=> "))
                    (.prompt rl))]

    (when is-tty?
      (-> info
          first
          (merge {:port port
                  :host host})
          format-welcome
          print))

    (.on rl "close" #(c/close client))

    (.on rl "line"
         (fn [line]
           (cond
             (empty? line) (prompt!)

             :else
             (a/let [res (c/message client {:op "eval" :code line :ns @ns})]
               (doseq [msg res]
                 (when (:ns msg)
                   (reset! ns (:ns msg)))
                 (when-let [err (:err msg)]
                   (js/process.stderr.write err))
                 (when-let [out (:out msg)]
                   (js/process.stdout.write out))
                 (when is-tty?
                   (when-let [value (:value msg)]
                     (println value))))

               (prompt!)))))

    (prompt!)))
