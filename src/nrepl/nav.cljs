(ns nrepl.nav
  (:require [nrepl.client :as c]
            [nrepl.async :as a]
            [clojure.core.protocols :refer [nav]]))

(def doc
  {:op "Op name"
   :id "The ID of the request for which the response was generated."
   :session "The ID of the session for which the response was generated."
   :status "The status of the response. Here there would either be something like \"done\" if a request has been fully processed or the reason for a failure (e.g. \"namespace-not-found\"). Not every response message would have the status key. If some request generated multiple response messages only the final one would have the status attached to it."
   :ns "The stringified value of ns at the time of the response message’s generation."
   :out "Contains content written to out while the request’s code was being evaluated. Messages containing out content may be sent at the discretion of the server, though at minimum corresponding with flushes of the underlying stream/writer."
   :err "Same as :out, but for err."
   :value "The result of printing a result of evaluating a form in the code sent in the corresponding request. More than one value may be sent, if more than one form can be read from the request’s code string. In contrast to the output written to out and err, this may be usefully/reliably read and utilized by the client, e.g. in tooling contexts, assuming the evaluated code returns a printable and readable value. Interactive clients will likely want to simply stream `:value’s content to their UI’s primary output / log."})

(defn describe [nrepl-port]
  (a/let [client (c/connect :port nrepl-port)
          info   (c/message client {:op "describe" :verbose? "true"})]
    (c/close client)
    info))

(defn find-doc [op nrepl-port]
  (a/let [res (describe nrepl-port)]
    (get-in res [0 :ops (keyword op)] op)))

(defn find-op-arg [op arg nrepl-port]
  (a/let [arg (keyword arg)
          doc (find-doc op nrepl-port)]
    (or (get-in doc [:requires arg])
        (get-in doc [:optional arg])
        arg)))

(defn can-meta? [x] (implements? IMeta x))

(defn nav-docs
  ([v nrepl-port]
   (if-not (can-meta? v)
     v
     (with-meta v {`nav #'nav-docs :nrepl-port nrepl-port})))
  ([coll k v]
   (a/let [{:keys [nrepl-port]} (meta coll)
           v (cond
               (= k :ns) (find-ns v)
               (= k :op) (find-doc v nrepl-port)

               (and (keyword? v)
                    (contains? coll :id))
               (or (doc v) (find-op-arg (:op coll) v nrepl-port))

               :else v)]
     (nav-docs v nrepl-port))))
