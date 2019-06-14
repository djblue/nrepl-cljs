(ns nrepl.server
  (:require [net :as net]
            [System]
            [cljs.tools.reader :refer [read-string]]
            [cljs.js :as cljs]
            [lumo.repl :as repl]
            [clojure.repl]
            [uuid :as uuid]
            [nrepl.bencode :refer [encode decode]]
            [clojure.pprint :refer [pprint]]))

(defonce sessions (atom {}))

(defn nrepl-eval [form name-space]
  (binding [cljs/*load-fn* lumo.repl/load
            cljs/*eval-fn* lumo.repl/caching-node-eval]
    (cond
      (= form '(require 'cljs.repl.nashorn))
      nil
      (= form '((or (resolve 'cider.piggieback/cljs-repl)
                    (resolve 'cemerick.piggieback/cljs-repl))
                (cljs.repl.nashorn/repl-env)))
      nil
      :else (repl/eval form name-space repl/st))))

(defn init []
  (let [vars
        '{cljs.core/all-ns lumo.repl/all-ns
          cljs.core/ns-map lumo.repl/ns-map
          cljs.core/ns-aliases lumo.repl/ns-map
          cljs.repl/special-doc lumo.repl/special-doc
          cljs.repl/namespace-doc lumo.repl/namespace-doc}]
    (->> vars
         (map
          (fn [[k v]]
            (nrepl-eval
             (list 'def (-> k name symbol) v)
             (-> k namespace symbol))))
         dorun)))

(defn promise? [v] (instance? js/Promise v))

(defn handler [request send]
  (let [id (get request "id")
        op (get request "op")
        response
        (case op
          "clone"
          (let [new-session (str (uuid/v4))]
            (swap! sessions
                   assoc
                   new-session
                   {:compiler (cljs.js/empty-state)})
            {"id" id
             "new-session" new-session
             "status" ["done"]})
          "describe"
          {"id" id
           "ops" {"stacktrace" 1}
           "versions"
           {"nrepl"
            {"major" 0 "minor" 2}}
           "status" ["done"]}
          "stacktrace"
          {"id" id
           "name" (.-name *e)
           "class" (.-name *e)
           "method" "test"
           "message" (.-message *e)
           "status" ["done"]}
          "interrupt"
          {"id" id
           "status" ["done"]}
          "eval"
          (let [code (get request "code")
                session (get request "session")
                name-space (symbol (or (get request "ns") "cljs.user"))
                compiler (get-in @sessions [session :compiler])]
            ;(println code)
            (try
              (let [value (atom nil)
                    out (with-out-str
                          (reset! value (-> code read-string (nrepl-eval name-space))))]
                (.then
                 (js/Promise.resolve @value)
                 #(-> {"id" id
                       "out" out
                       "value"
                       (if (promise? @value)
                         (str "#object[Promise " (pr-str %) "]")
                         (pr-str %))
                       "status" ["done"]})))
              (catch js/Object e
                (set! *e e)
                {"id" id
                 "err" (.-stack e)
                 "ex" (pr-str e)
                 "status" ["done"]})))
          "close"
          {})]
    (if (= op "close")
      (.end socket)
      (.then
       (js/Promise.resolve response)
       #(send %)))))

(defn transport [socket data]
  (let [[request] (decode (.toString data "utf8"))]
    (handler request #(.write socket (encode %)))))

(defn setup [socket]
  (.on (.setNoDelay socket true) "data" #(transport socket %)))

(defn start-server []
  (init)
  (js/Promise.
   (fn [resolve reject]
     (let [srv (net/createServer setup)]
       (.on srv "error" reject)
       (.listen
        srv
        7888
        (fn []
          (resolve (.address srv))))))))

(defn stop-server [server]
  (.close server))

(defn -main [] (start-server))

