(ns nrepl.async
  (:refer-clojure :exclude [let promise])
  #?(:cljs (:require-macros nrepl.async)))

(defmacro all [& args] `(js/Promise.all ~@args))

(defmacro promise [bindings & body]
  `(js/Promise. (fn ~bindings ~@body)))

(defmacro do [& body]
  (reduce
   (fn [chain form]
     `(.then ~chain
             (fn [] (js/Promise.resolve ~form))))
   `(js/Promise.resolve nil)
   body))

(defmacro let [bindings & body]
  (->> (partition-all 2 bindings)
       reverse
       (reduce (fn [body [n v]]
                 `(.then (js/Promise.resolve ~v)
                         (fn [~n] ~body)))
               `(nrepl.async/do ~@body))))

(defmacro if [test & body]
  `(nrepl.async/let [result# ~test] (if result# ~@body)))
