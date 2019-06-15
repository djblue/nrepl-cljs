(ns nrepl.server
  (:require [net :as net]
            [System]
            [cljs.tools.reader :refer [read-string]]
            [cljs.js :as cljs]
            [lumo.repl :as repl]
            [clojure.repl]
            [uuid :as uuid]
            [nrepl.bencode :refer [encode decode]]
            [clojure.walk :as walk]
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

(defn dispatch [req]
  (case (:op req)
    :clone
    (let [new-session (str (uuid/v4))]
      (swap! sessions
             assoc
             new-session
             {:compiler (cljs.js/empty-state)})
      {:new-session new-session})
    :describe
    {:ops {:stacktrace 1}
     :versions
     {"nrepl"
      {"major" 0 "minor" 2}}}
    :stacktrace
    {:name (.-name *e)
     :class (.-name *e)
     :method "test"
     :message (.-message *e)}
    :interrupt {}
    :eval
    (let [code (:code req)
          session (:session req)
          name-space (symbol (or (:ns req) "cljs.user"))
          compiler (get-in @sessions [session :compiler])]
      (try
        (let [value (atom nil)
              out (with-out-str
                    (reset! value (-> code read-string (nrepl-eval name-space))))]
          {:out out
           :value @value})
        (catch js/Object e
          (set! *e e)
          {:err (.-stack e)
           :ex (pr-str e)})))
    :close
    nil))

(defn dispatch-send [req send]
  (let [op (-> req :op keyword)]
    (send (when-let [res (dispatch (assoc req :op op))]
            (assoc res :status [:done])))))

(defn promise? [v] (instance? js/Promise v))

(defn stringify-value [handler]
  (fn [req send]
    (handler req
             (fn [res]
               (if (contains? res :value)
                 (let [value (:value res)]
                   (if (promise? value)
                     (.then value
                            #(let [value (str "#object[Promise " (pr-str %) "]")]
                               (send (assoc res :value value))))
                     (send (assoc res :value (pr-str value)))))
                 (send res))))))

(defn attach-id [handler]
  (fn [req send]
    (let [id (:id req)]
      (handler req #(send (assoc % :id id))))))

(defn logger [handler]
  (fn [req send]
    (pprint (assoc req :type :request))
    (handler req #(do (pprint (assoc % :type :response)) (send %)))))

(defn transport [socket data]
  (let [[req] (decode (.toString data "utf8"))
        handler (-> dispatch-send
                    attach-id
                    stringify-value
                    logger)]
    (handler (walk/keywordize-keys req)
             #(if (nil? %)
                (.end socket)
                (.write socket (encode %))))))

(defn setup [socket]
  (.setNoDelay socket true)
  (.on socket "data" #(transport socket %)))

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

