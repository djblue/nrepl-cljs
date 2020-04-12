(ns nrepl.protocols)

(defprotocol MessageStream (send [message]))

(defprotocol Encoding (encode [bytes]) (decode [bytes]))

(defprotocol Evaluation (eval []))

