(ns lvl6-chat.aleph-netty
  (:require [aleph.http :as http]
            [manifold.stream :as s]
            [lvl6-chat.state :as state]
            [lvl6-chat.protobuf :as p]
            [flatland.protobuf.core :as flatland-proto :refer [protobuf protobuf-dump protobuf-load protodef]]
            [clojure.core.async :refer [chan go >! <! <!! >!! go-loop put! thread alts! alts!! timeout pipeline pipeline-blocking pipeline-async]])
  (:import (clojure.lang APersistentMap)))



(defn send-to-api [stream-in-ch stream-out-ch]
  (go (loop []
        (let [ws-data (<! stream-in-ch)]
          (when-not (nil? ws-data)
            (let [{:keys [eventname data uuid] :as clj-data} (p/byte-array->proto->clj-data ws-data)
                  ;check if the message requires a response
                  response-protobuf-kw  (if (.contains (name eventname) "-request")
                                          (keyword (clojure.string/replace (name eventname) "-request" "-response"))
                                          nil)]
              (println "GOT ON WS::" clj-data)
              (println "data type:::" (class data))
              (println "response protobuf is::" response-protobuf-kw)
              #_(>! stream-out-ch (byte-array [1 2 3]))
              (recur)))))))

(defn ws-handler [{:keys [headers] :as req}]
  (println "req::" req)
  (if (= "websocket" (get headers "upgrade"))
    (let [s @(http/websocket-connection req)]
      (let [stream-in-ch (chan 1024)
            stream-out-ch (chan 1024)]
        (s/connect
          s
          stream-in-ch)
        (s/connect stream-out-ch
                   s)
        (send-to-api stream-in-ch stream-out-ch)))
    {:status 200
     :body "not a websocket request"}))

(defn start-server []
  (reset! state/ws-server (http/start-server ws-handler {:port 8081})))


(defn stop-server []
  (.close @state/ws-server))

(def ws-client-chans (atom nil))

(defn ws-client-write [{:keys [eventname data uuid] :as m}]
  ;transform clojure data to protobuf and then to byte-array
  (let [b-a (p/proto->byte-array (p/chat-event-proto
                                   {:eventname :create-user-request
                                    :data      (p/event-name-dispatch eventname data flatland-proto/protobuf)
                                    :uuid      uuid}))]
    ;send over WebSocket
    (>!! (nth @ws-client-chans 1) b-a)))

(defn ws-client-read []
  ;TODO load from protobuf
  (<!! (nth @ws-client-chans 0)))

(defn ws-client []
  (let [s @(http/websocket-client "ws://10.0.1.33:8081/")]
    (let [stream-in-ch (chan 1024)
          stream-out-ch (chan 1024)]
      (s/connect
        s
        stream-in-ch)
      (s/connect stream-out-ch
                 s)
      (reset! ws-client-chans [stream-in-ch stream-out-ch]))))