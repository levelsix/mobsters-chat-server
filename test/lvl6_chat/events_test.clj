(ns lvl6-chat.events-test
  (:require [clojure.test :refer :all]
            [manifold.stream :as s]
            [aleph.http :as http]
            [lvl6-chat.dynamo-db :as dynamo-db]
            [lvl6-chat.protobuf :as p]
            [lvl6-chat.dynamo-db :as dynamo-db]
            [clojure.core.async :refer [chan close! go >! <! <!! >!! go-loop put! thread alts! alts!! timeout pipeline pipeline-blocking pipeline-async]]
            [lvl6-chat.util :as util]))


;WebSocket client setup
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

(defn ws-client-global [{:keys [useruuid]}]
  (let [s @(http/websocket-client "ws://localhost:8081/" {:headers {:useruuid useruuid}})]
    (let [stream-in-ch (chan 1024)
          stream-out-ch (chan 1024)]
      (s/connect
        s
        stream-in-ch)
      (s/connect stream-out-ch
                 s)
      (reset! ws-client-chans [stream-in-ch stream-out-ch]))))

(defn ws-client-instance [{:keys [useruuid]}]
  (let [s @(http/websocket-client "ws://localhost:8081/" {:headers {:useruuid useruuid}})]
    (let [stream-in-ch (chan 1024)
          stream-out-ch (chan 1024)]
      (s/connect
        s
        stream-in-ch)
      (s/connect stream-out-ch
                 s)
      {:stream-in-ch  stream-in-ch
       :stream-out-ch stream-out-ch})))

(defn ws-client-close-instance! [{:keys [stream-out-ch] :as ws-client}]
  (close! stream-out-ch))

(defn ws-client-read-instance [{:keys [stream-in-ch] :as ws-client}]
  (<!! stream-in-ch))

(defn ws-client-write-instance [{:keys [stream-out-ch] :as ws-client} {:keys [eventname data uuid] :as m}]
  (>!! stream-out-ch (p/clj-data->proto->byte-array m)))



;Tests
;========================================
(defn reset-all-data []
  (dynamo-db/delete-tables)
  (dynamo-db/create-tables))

(defn -create-user-test []
  (reset-all-data)
  (let [useruuid (util/random-uuid-str)
        ws-request-uuid (util/random-uuid-str)]
    (println "going to create user:" useruuid)
    ;init client WebSocket
    (ws-client-global {:useruuid useruuid})
    ;send data
    (ws-client-write {:eventname :create-user-request
                      :data      {:useruuid useruuid}
                      :uuid      ws-request-uuid})
    ;receive data
    (let [^bytes ws-data (ws-client-read)
          {:keys [eventname data uuid]} (p/byte-array->proto->clj-data ws-data)
          {:keys [status authtoken]} data]
      (is (and (= eventname :create-user-response)
               (= status :success)
               (string? authtoken)
               (= uuid ws-request-uuid))))))

(deftest create-user-test
  (-create-user-test))


(defn -create-room-test []
  (reset-all-data)
  (let [useruuid (util/random-uuid-str)
        ws-request-uuid (util/random-uuid-str)]
    ;init client WebSocket
    (ws-client-global {:useruuid useruuid})
    ;send data
    (ws-client-write {:eventname :create-chat-room-request
                      :data {:useruuid useruuid}
                      :uuid ws-request-uuid})
    (let [^bytes ws-data (ws-client-read)
          {:keys [eventname data uuid]} (p/byte-array->proto->clj-data ws-data)
          {:keys [status room]} data
          {:keys [authtoken roomuuid roomname]} room]
      (is (and (= eventname :create-chat-room-response)
               (= status :success)
               (string? authtoken)
               (string? roomuuid)
               (string? roomname))))))

(deftest create-room-test
  (-create-room-test))


(defn -add-user-to-chat-room-test []
  (reset-all-data)
  (let [useruuid (util/random-uuid-str)
        useruuid-to-add (util/random-uuid-str)
        roomuuid (util/random-uuid-str)
        ws-request-uuid (util/random-uuid-str)]
    (println "roomuuid" roomuuid)
    (println "useruuid-to-add" useruuid-to-add)
    ;init client WebSocket
    (ws-client-global {:useruuid useruuid})
    ;send data
    (ws-client-write {:eventname :add-user-to-chat-room-request
                      :data {:useruuid useruuid-to-add
                             :roomuuid roomuuid}
                      :uuid ws-request-uuid})
    (let [^bytes ws-data (ws-client-read)
          {:keys [eventname data uuid]} (p/byte-array->proto->clj-data ws-data)
          {:keys [status]} data]
      (is (= status :success)))))

(deftest add-user-to-chat-room-test
  (-add-user-to-chat-room-test))


(defn -remove-user-from-chat-room-test []
  (reset-all-data)
  (let [useruuid (util/random-uuid-str)
        useruuid-to-add (util/random-uuid-str)
        roomuuid (util/random-uuid-str)
        ws-request-uuid (util/random-uuid-str)]
    (println "roomuuid" roomuuid)
    (println "useruuid-to-add" useruuid-to-add)
    ;init client WebSocket
    (ws-client-global {:useruuid useruuid})
    ;send data
    (ws-client-write {:eventname :add-user-to-chat-room-request
                      :data      {:useruuid useruuid-to-add
                                  :roomuuid roomuuid}
                      :uuid      ws-request-uuid})
    (let [^bytes ws-data (ws-client-read)
          {:keys [eventname data uuid]} (p/byte-array->proto->clj-data ws-data)
          {:keys [status]} data]
      (is (= 1 (count (dynamo-db/get-room-users {:roomuuid roomuuid})))))
    ;remove user from room
    (ws-client-write {:eventname :remove-user-from-chat-room-request
                      :data      {:useruuid useruuid-to-add
                                  :roomuuid roomuuid}
                      :uuid      ws-request-uuid})
    (let [^bytes ws-data (ws-client-read)
          {:keys [eventname data]} (p/byte-array->proto->clj-data ws-data)]
      (is (= 0 (count (dynamo-db/get-room-users {:roomuuid roomuuid})))))))

(deftest remove-user-from-chat-room-test
  (-remove-user-from-chat-room-test))