(ns nrepl.repl
  (:refer-clojure :exclude [eval])
  (:require [cljs.js :as cljs]
            [cljs.env :as env]
            [nrepl.async :as a]
            [shadow.cljs.bootstrap.node :as boot]))

(defonce compile-state-ref (env/default-compiler-env))

(defn eval [source ns]
  (a/promise
   [resolve]
   (boot/init
    compile-state-ref
    {}
    #(cljs/eval-str
      compile-state-ref
      source
      "<repl>"
      {:eval          cljs/js-eval
       :ns            ns
       :target        :nodejs
       :context       :expr
       :def-emits-var true
       :load          (fn [name cb]
                        (println :args name cb)
                        (boot/load
                         compile-state-ref
                         name
                         (fn [result]
                           (println :result result)
                           (cb %))))}

      resolve))))
