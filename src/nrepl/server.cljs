(ns nrepl.server
  (:require [net :as net]
            [System]
            [cljs.tools.reader :refer [read-string]]
            [cljs.js :as cljs]
            [lumo.repl :as repl]
            [uuid :as uuid]
            [clojure.pprint :refer [pprint]]))

(defn index-of [s c] (.indexOf s c))

(defn decode [data]
  (case (first data)
    "i"
    (let [data (subs data 1)
          i (index-of data "e")]
      [(js/parseInt (subs data 0 i))
       (subs data (inc i))])
    "l"
    (let [data (subs data 1)]
      (loop [data data v []]
        (if (= (first data) "e")
          [v (subs data 1)]
          (let [[value data] (decode data)]
            (recur data (conj v value))))))
    "d"
    (let [data (subs data 1)]
      (loop [data data m {}]
        (if (= (first data) "e")
          [m (subs data 1)]
          (let [[k data] (decode data)
                [v data] (decode data)]
            (recur data (assoc m k v))))))
    (let [i (index-of data ":")
          n (js/parseInt (subs data 0 i))
          data (subs data (inc i))]
      [(subs data 0 n) (subs data n)])))

(defn encode [data]
  (cond
    (string? data)
    (str (count data) ":" data)
    (number? data)
    (str "i" data "e")
    (vector? data)
    (str "l" (apply str (map encode data)) "e")
    (map? data)
    (str "d" (->> data
                  (sort-by first)
                  (map (fn [[k v]]
                         (str (encode k) (encode v))))
                  (apply str))
         "e")))

(defonce sessions (atom {}))

(defn all-ns [] (lumo.repl/all-ns))

(defn ns-map [ns]
  (lumo.repl/ns-syms ns (constantly true)))

(defn ns-aliases [ns]
  (lumo.repl/set-ns (str ns))
  (lumo.repl/current-alias-map))

;(ns-aliases "nrepl.server")
;(range 100)
;(ns-map 'nrepl.server)
;(def state (cljs.js/empty-state))

(defn my-eval [form name-space]
  (binding [;cljs.env/*compiler* state
            cljs/*load-fn* lumo.repl/load
            ;*ns* (find-ns 'nrepl.server)
            cljs/*eval-fn* lumo.repl/caching-node-eval
            ]
    (prn form)
    (cond
      (= form '(require 'cljs.repl.nashorn))
      nil
      (= form '((or (resolve 'cider.piggieback/cljs-repl)
                    (resolve 'cemerick.piggieback/cljs-repl))
                (cljs.repl.nashorn/repl-env)))
      nil
      :else (repl/eval
              form
              (find-ns name-space)
              repl/st))))

(comment
  (+ 1 2 3)
  (require 'clojure.core)
  (clojure.core/conj [1 2 3] 4)
  (cljs.core/conj [1 2 3] 4)
  (-> clojure.core/conj)
  (-> conj)
  (into [1 2 3] [1 2 3]))

(defn handle [socket message]
  (let [request-string (.toString message "utf8")
        [request] (decode request-string)
        id (get request "id")
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
           "ops" {}
           "versions"
           {"nrepl"
            {"major" 0 "minor" 2}}
           "status" ["done"]}
          "interrupt"
          {"id" id
           "status" ["done"]}
          "eval"
          (let [code (get request "code")
                session (get request "session")
                compiler (get-in @sessions [session :compiler])]
            (try
              {"id" id
               "value" (-> code
                           read-string
                           (my-eval 'nrepl.server)
                           pr-str)
               "status" ["done"]}
              (catch js/Object e
                (js/console.error e)
                {})))
          "close"
          {})
        response-string (encode response)]
    (pprint
      {:request request
       ;:request-string request-string
       ;:response-string response-string
       :response response})
    (if (= op "close")
      (.end socket)
      (.write socket response-string))))

(defn handler [socket]
  (.on (.setNoDelay socket true) "data" #(handle socket %)))

(defn start-server []
  (js/Promise.
   (fn [resolve reject]
     (let [srv (net/createServer handler)]
       (.on srv "error" reject)
       (.listen
        srv
        3000
        (fn []
          (resolve (.address srv))))))))

(defn stop-server [server]
  (.close server))

#_(start-server)
