(ns lvl6-chat.aleph-netty
  (:require [aleph.http :as http]
            [manifold.stream :as s]
            [lvl6-chat.state :as state]
            [lvl6-chat.protobuf :as p]
            [lvl6-chat.dynamo-db :as dynamo-db]
            [lvl6-chat.events :as events]
            [lvl6-chat.io-utils :as io-utils]
            [clojure.core.incubator :refer [dissoc-in]]
            [flatland.protobuf.core :as flatland-proto :refer [protobuf protobuf-dump protobuf-load protodef]]
            [lvl6-chat.rabbit-mq :as rabbit-mq]
            [clojure.core.async :refer [chan close! go >! <! <!! >!! go-loop put! thread alts! alts!! timeout pipeline pipeline-blocking pipeline-async]]
            [lvl6-chat.util :as util])
  (:import (clojure.core.async.impl.channels ManyToManyChannel)))

;map that holds { socket-key uuid } pairs
(def socket-uuid (ref {}))

(defn get-socket-uuid
  "Get the user-id for a socket-key"
  [socket-key]
  (get @socket-uuid socket-key))

;map that holds a map like this: { uuid {socket-key-1 {:stream-in-ch (chan) :stream-out-ch (chan)}, socket-key-2 {:stream-in-ch (chan) :stream-out-ch (chan)}} }
(def uuid-sockets (ref {}))

(defn get-sockets
  "Get all sockets for that user on this server"
  [uuid]
  (get @uuid-sockets uuid))


(defn uuid-socket-update-timestamp
  "Updates the timestamp on the specified uuid and socket-key pair for heartbeat purposes"
  [uuid socket-key]
  (dosync (alter uuid-sockets assoc-in [uuid socket-key :timestamp] (System/currentTimeMillis))))

;socket transaction functions
(defn add-socket-transaction
  "Modifies (adds) socket-uuid and uuid-sockets maps with a Clojure STM transaction"
  [uuid socket-key {:keys [stream-in-ch stream-out-ch] :as ws-chans}]
  (dosync
    (alter socket-uuid assoc socket-key uuid)
    (alter uuid-sockets assoc-in [uuid socket-key] (assoc ws-chans :timestamp (System/currentTimeMillis)))))

(defn remove-socket-transaction
  "Modifies (removes) socket-uuid and uuid-sockets maps with a Clojure STM transaction"
  [uuid socket-key]
  (dosync
    (alter socket-uuid dissoc socket-key)
    (alter uuid-sockets dissoc-in [uuid socket-key])))

(defn send-to-socket!
  "Sends byte-array to a websocket socket"
  [uuid socket-key b-a]
  (let [stream-out-ch (get-in @uuid-sockets [uuid socket-key :stream-out-ch])]
    (if (instance? ManyToManyChannel stream-out-ch)
      (>!! stream-out-ch b-a)
      false)))

(defn send-to-sockets! [uuid b-a]
  "Sends byte-array to all websockets for that user on this server (one or more)"
  (if (instance? util/byte-array-class b-a)
    (doseq [{:keys [stream-out-ch]} (vals (get @uuid-sockets uuid))]
      (>!! stream-out-ch b-a))))

;============================================


(defn send-to-api
  "Connects Aleph to a pair of core.async channels, -in-ch and -out-ch"
  [{:keys [stream-in-ch stream-out-ch req]}]
  (let [{:keys [headers]} req
        {:keys [useruuid sec-websocket-key]} headers]
    (println "useruuid" useruuid)
    (if (and (not (nil? useruuid)) (string? useruuid))
      ;useruuid provided, proceed
      ;TODO check user authtoken here
      (do
        ;add websocket
        (add-socket-transaction useruuid sec-websocket-key {:stream-in-ch stream-in-ch :stream-out-ch stream-out-ch})
        ;RabbitMQ start subscription
        (rabbit-mq/start-subscription! {:useruuid useruuid
                                        :sec-websocket-key sec-websocket-key
                                        :ws-stream-out-ch stream-out-ch})
        (go (loop []
              (let [^bytes ws-data (<! stream-in-ch)]
                (if-not (nil? ws-data)
                  (let [{:keys [eventname data uuid] :as request} (p/byte-array->proto->clj-data ws-data)
                        ;check if the message requires a response
                        response-eventname-kw (if (.contains (name eventname) "-request")
                                                ;replace "-request" with "-response"
                                                (keyword (clojure.string/replace (name eventname) "-request" "-response"))
                                                nil)]
                    (if response-eventname-kw
                      ;if response is needed, prepare the response; process the request/response on a separate thread
                      (let [response-ch (thread (events/process-request-response {:request  request
                                                                             :response {:eventname response-eventname-kw
                                                                                        :uuid      uuid}}))]
                        (println "GOT ON WS::" request)
                        (println "data type:::" (class data))
                        (println "response protobuf is::" response-eventname-kw)
                        (go (let [response (<! response-ch)]
                              (>! stream-out-ch response)))))
                    (recur))
                  (do
                    ;closing websocket, cleanup memory
                    (println "closing websocket, cleanup memory" useruuid sec-websocket-key)
                    ;remove websockets
                    (remove-socket-transaction useruuid sec-websocket-key)
                    ;clean up RabbitMQ
                    (rabbit-mq/stop-subscription! sec-websocket-key)))))))
      ;no useruuid provided in headers, closing socket
      (do (println "no useruuid provided in headers, closing socket")
          (close! stream-out-ch)))))

(defn ws-handler
  "New websocket connections get processed here"
  [{:keys [headers] :as req}]
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
        (send-to-api {:stream-in-ch  stream-in-ch
                      :stream-out-ch stream-out-ch
                      :req req})))
    {:status 200
     :body "not a websocket request"}))

;server start/stop
;========================================
(defn start-server []
  (reset! state/ws-server (http/start-server ws-handler {:port 8081})))

(defn stop-server []
  (.close @state/ws-server))

;CLIENT TESTING
;========================================
(def ws-client-chans (atom nil))

(defn ws-client-write [{:keys [eventname data uuid] :as m}]
  ;transform clojure data to protobuf and then to byte-array
  (let [b-a (p/clj-data->proto->byte-array m)]
    ;send over WebSocket
    (>!! (nth @ws-client-chans 1) b-a)))

(defn ws-client-read []
  ;TODO load from protobuf
  (<!! (nth @ws-client-chans 0)))

(defn ws-client-close! []
  (close! (nth @ws-client-chans 1)))

(defn ws-client []
  (let [s @(http/websocket-client "ws://localhost:8081/" {:headers {:useruuid "raspasov"}})]
    (let [stream-in-ch (chan 1024)
          stream-out-ch (chan 1024)]
      (s/connect
        s
        stream-in-ch)
      (s/connect stream-out-ch
                 s)
      (reset! ws-client-chans [stream-in-ch stream-out-ch]))))

