(ns nrepl.server
  (:require [net :as net]
            [System]
            [cljs.tools.reader :refer [read-string]]
            [cljs.js :as cljs]
            [clojure.string :as s]
            [nrepl.bencode :refer [encode decode-all]]
            [nrepl.async :as a]
            [clojure.pprint :refer [pprint]]))

(defonce sessions (atom {}))

(def my-eval (atom nil))

(defn ignore [source]
  (some #(s/includes? source %)
        ["cljs.repl.nashorn"
         "cider.piggieback"
         "cemerick.piggieback"
         "cljs.repl.rhino"]))

(defn info [req]
  (when-not (empty? (:symbol req))
    (let [s (:symbol req)
          m (@my-eval
             (pr-str `(meta (var ~(symbol s))))
             (:ns req)
             (:session req))]
      (merge
       {:name s}
       (select-keys m [:doc])))))

(defn dispatch [req]
  (case (:op req)
    :info (info req)
    :ns-list {}
    :complete
    {}
    :clone
    (let [new-session (str (random-uuid))]
      (swap! sessions
             assoc
             new-session
             {:compiler (cljs.js/empty-state)})
      {:new-session new-session})
    :describe
    {:ops {:stacktrace 1 :eval 1}
     :versions
     {"nrepl"
      {"major" 0 "minor" 2}}}
    :stacktrace
    {:name ""
     :class ""
     :method "test"
     :message ""}
    :interrupt {}
    :eval
    (let [code (:code req)
          session (:session req)
          ns (or (:ns req) 'cljs.user)]
      (try
        (a/let [res (@my-eval code ns)
                out ""]
          (println res)
          (cond
            (contains? res :error)
            {:err (get-in res [:error :message])}

            (empty? out) res

            :else (assoc res :out out)))
        (catch js/Object e
          (set! *e e)
          (println e)
          (loop [e (ex-cause e)]
            (cond
              (nil? e) {:err "unknown"}
              (ex-cause e)
              {:err (.-stack (ex-cause e)) :ex (pr-str e)}
              :else (recur (ex-cause e)))))))
    :load-file {}
    :close {}
    {}))

(defn dispatch-send [req send]
  (prn :request req)
  (a/let [op (-> req :op keyword)
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
                      (prn :response %)
                      (.write socket (encode %))
                      (when (and (= (:op req) "close")
                                 (contains? % :status))
                        (.end socket)))))
    data))

(defn setup [eval]
  (fn [socket]
    (let [state (atom nil)]
      (.setNoDelay socket true)
      (.on socket "data"
           #(do
              (reset! my-eval eval)
              (reset! state (transport socket @state %)))))))

(defn start-server [eval]
  (js/Promise.
   (fn [resolve reject]
     (let [srv (net/createServer (setup eval))]
       (.on srv "error" reject)
       (.listen
        srv
        7888
        (fn []
          (println "started server on port 7888")
          (resolve {:handle srv :port (.-port (.address srv))})))))))

(defn stop-server [server]
  (.close (:handle server)))

