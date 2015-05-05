(ns lvl6-chat.zeromq
  (:require [com.keminglabs.zmq-async.core :refer [register-socket! create-context initialize!]]
            [clojure.core.async :refer [chan close! go >! <! <!! >!! go-loop put! thread alts! alts!! timeout sliding-buffer pipeline pipeline-blocking pipeline-async]]))


(def zmq-pub-to-sub-ch (chan))

(defn zmq-pub! [host port]
  (let [[zmq-pub-in zmq-pub-out] (repeatedly 2 #(chan))
        context (doto (create-context (str "pub"))
                  (initialize!))]
    (register-socket! {:context      context
                       :in           zmq-pub-in
                       :out          zmq-pub-out
                       :socket-type  :pub
                       :configurator (fn [socket] (.bind socket (str "tcp://" host ":" port)))})

    (go (loop []
          (let [pub-msg (<! zmq-pub-to-sub-ch)]
            (>! zmq-pub-in pub-msg))
          (recur)))))

(def sub-context (atom nil))

(defn get-sub-context [host port]
  (if (= nil @sub-context)
    (reset! sub-context (doto (create-context (str "sub"))
                          (initialize!)))
    @sub-context))

(defn zmq-sub! [host port subscribe-str]
  (let [[zmq-sub-in zmq-sub-out] (repeatedly 2 #(chan (sliding-buffer 64)))
        context (get-sub-context host port)]
    (register-socket! {:context      context
                       :in           zmq-sub-in
                       :out          zmq-sub-out
                       :socket-type  :sub
                       :configurator (fn [socket]
                                       (doto socket
                                         (.connect (str "tcp://" host ":" port))
                                         (.subscribe (.getBytes subscribe-str))))})

    (go-loop []
      (let [b-a (<! zmq-sub-out)]
        (when-not (nil? b-a)
          (clojure.pprint/pprint b-a)
          (println "string::" (String. b-a))
          ;(>! channels/zmq-sub-input-ch (map data/to-string byte-vector))
          (recur))))
    zmq-sub-in))