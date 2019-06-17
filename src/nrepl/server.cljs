(ns nrepl.server
  (:require [net :as net]
            [System]
            [cljs.tools.reader :refer [read-string]]
            [cljs.js :as cljs]
            [lumo.repl :as repl]
            [clojure.repl]
            [nrepl.bencode :refer [encode decode-all]]
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
    (let [new-session (str (random-uuid))]
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
                    (reset! value (-> code read-string (nrepl-eval name-space))))
              res {:ns (name name-space) :value @value}]
          (if (empty? out) res (assoc res :out out)))
        (catch js/Object e
          (set! *e e)
          {:err (.-stack e)
           :ex (pr-str e)})))
    :load-file {}
    :close {}))

(defn dispatch-send [req send]
  (let [op (-> req :op keyword)
        res (dispatch (assoc req :op op))]
    (if (= op :clone)
      (send (assoc res :status [:done]))
      (do
        (.then (js/Promise.resolve (send res))
               #(send {:status ["done"]}))))))

(defn promise? [v] (instance? js/Promise v))

(defn stringify-value [handler]
  (fn [req send]
    (handler req
             (fn [res]
               (if (contains? res :value)
                 (let [value (:value res)]
                   (if (promise? value)
                     (-> value
                         (.then #(str "#object[Promise " (pr-str %) "]"))
                         (.catch #(str "#object[Promise " (pr-str %) "]"))
                         (.then #(send (assoc res :value %))))
                     (send (assoc res :value (pr-str value)))))
                 (send res))))))

(defn attach-id [handler]
  (fn [req send]
    (let [{:keys [id session]} req]
      (handler req #(send (merge {:id id} (when session {:session session}) %))))))

(defn logger [handler]
  (fn [req send]
    (pprint (assoc req :type :request))
    (handler req #(do (pprint (assoc % :type :response)) (send %)))))

(defn transport [socket state data]
  (let [data (if (nil? state)
               data
               (js/Buffer.concat (clj->js [state data])))
        [reqs data] (decode-all data :keywordize-keys true)
        handler (-> dispatch-send
                    attach-id
                    stringify-value
                    #_logger)]
    (doseq [req reqs]
      (handler req #(do
                      (.write socket (encode %))
                      (when (and (= (:op req) "close")
                                 (contains? % :status))
                        (.end socket)))))
    data))

(defn setup [socket]
  (let [state (atom nil)]
    (.setNoDelay socket true)
    (.on socket "data" #(reset! state (transport socket @state %)))))

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
          (resolve {:handle srv :port (.-port (.address srv))})))))))

(defn stop-server [server]
  (.close (:handle server)))

(defn -main [] (start-server))

