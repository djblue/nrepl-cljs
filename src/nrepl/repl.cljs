(ns nrepl.repl
  (:refer-clojure :exclude [eval])
  (:require [cljs.js :as cljs]
            [cljs.env :as env]
            [nrepl.async :as a]
            [shadow.cljs.bootstrap.node :as boot]))

(defonce compile-state-ref (env/default-compiler-env))

(defn eval [source ns]
  (let [ns (symbol ns)]
    (a/promise
     [resolve]
     (boot/init
      compile-state-ref
      {:path "target/bootstrap"}
      #(cljs/eval-str
        compile-state-ref
        source
        "<repl>"
        {:eval          cljs/js-eval
         :ns            ns
         :target        :nodejs
         ;:verbose       true
         :context       :expr
         :analyze-deps  false
         :def-emits-var true
         :load (partial boot/load compile-state-ref)}
        resolve)))))
