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
(def ws-client-global-chans (atom nil))

(defn ws-client-global-write [{:keys [eventname data uuid] :as m}]
  ;transform clojure data to protobuf and then to byte-array
  (let [b-a (p/clj-data->proto->byte-array m)]
    ;send over WebSocket
    (>!! (nth @ws-client-global-chans 1) b-a)))

(defn ws-client-global-read []
  ;TODO load from protobuf
  (<!! (nth @ws-client-global-chans 0)))

(defn ws-client-close! []
  (close! (nth @ws-client-global-chans 1)))

(defn ws-client-global [{:keys [useruuid]}]
  (let [s @(http/websocket-client "ws://localhost:8081/" {:headers {:useruuid useruuid}})]
    (let [stream-in-ch (chan 1024)
          stream-out-ch (chan 1024)]
      (s/connect
        s
        stream-in-ch)
      (s/connect stream-out-ch
                 s)
      (reset! ws-client-global-chans [stream-in-ch stream-out-ch]))))

(defn ws-client-instance [{:keys [useruuid host]
                           :or {host "ws://localhost:8081/"}}]
  (let [s @(http/websocket-client host {:headers {:useruuid useruuid}})]
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
  ;(reset-all-data)
  (let [useruuid (util/random-uuid-str)
        ws-request-uuid (util/random-uuid-str)]
    (println "going to create user:" useruuid)
    ;init client WebSocket
    (ws-client-global {:useruuid useruuid})
    ;send data
    (ws-client-global-write {:eventname :create-user-request
                             :data      {:useruuid useruuid}
                             :uuid      ws-request-uuid})
    ;receive data
    (let [^bytes ws-data (ws-client-global-read)
          {:keys [eventname data uuid responseinfo] :as response} (p/byte-array->proto->clj-data ws-data)
          {:keys [status authtoken]} data]
      (println "got response::" response)
      (is (and (= eventname :create-user-response)
               (= responseinfo {:status :success})
               (string? authtoken)
               (= uuid ws-request-uuid))))))

(deftest create-user-test
  (println "create user test")
  (-create-user-test))


(defn -create-room-test []
;  (reset-all-data)
  (let [useruuid (util/random-uuid-str)
        ws-request-uuid (util/random-uuid-str)]
    ;init client WebSocket
    (ws-client-global {:useruuid useruuid})
    ;send data
    (ws-client-global-write {:eventname :create-chat-room-request
                             :data      {:useruuid useruuid
                                         :roomname (util/random-uuid-str)}
                             :uuid      ws-request-uuid})
    (let [^bytes ws-data (ws-client-global-read)
          {:keys [eventname data uuid responseinfo] :as event} (p/byte-array->proto->clj-data ws-data)
          {:keys [status room]} data
          {:keys [authtoken roomuuid roomname]} room]
      (println "event::" event)
      (is (and (= eventname :create-chat-room-response)
               (= responseinfo {:status :success})
               (string? authtoken)
               (string? roomuuid)
               (string? roomname))))))

(deftest create-room-test
  (println "create room test")
  (-create-room-test))


(defn -add-user-to-chat-room-test []
;  (reset-all-data)
  (let [useruuid (util/random-uuid-str)
        useruuid-to-add (util/random-uuid-str)
        roomuuid (util/random-uuid-str)
        ws-request-uuid (util/random-uuid-str)]
    (println "roomuuid" roomuuid)
    (println "useruuid-to-add" useruuid-to-add)
    ;init client WebSocket
    (ws-client-global {:useruuid useruuid})
    ;send data
    (ws-client-global-write {:eventname :add-user-to-chat-room-request
                             :data      {:useruuid useruuid-to-add
                                         :roomuuid roomuuid}
                             :uuid      ws-request-uuid})
    (let [^bytes ws-data (ws-client-global-read)
          {:keys [eventname data uuid responseinfo]} (p/byte-array->proto->clj-data ws-data)
          {:keys [status]} data]
      (is (= responseinfo {:status :success})))))

(deftest add-user-to-chat-room-test
  (println "add user to chat room")
  (-add-user-to-chat-room-test))


(defn -remove-user-from-chat-room-test []
  (reset-all-data)
  (let [useruuid (util/random-uuid-str)
        useruuid-to-add (util/random-uuid-str)
        roomuuid (util/random-uuid-str)
        ws-request-uuid (util/random-uuid-str)]
    (println "roomuuid" roomuuid)
    (println "useruuid-to-remove" useruuid-to-add)
    ;init client WebSocket
    (ws-client-global {:useruuid useruuid})
    ;send data
    (ws-client-global-write {:eventname :add-user-to-chat-room-request
                             :data      {:useruuid useruuid-to-add
                                         :roomuuid roomuuid}
                             :uuid      ws-request-uuid})
    (let [^bytes ws-data (ws-client-global-read)
          {:keys [eventname data uuid]} (p/byte-array->proto->clj-data ws-data)
          {:keys [status]} data]
      (is (= 1 (count (dynamo-db/get-room-users {:roomuuid roomuuid})))))
    ;remove user from room
    (ws-client-global-write {:eventname :remove-user-from-chat-room-request
                             :data      {:useruuid useruuid-to-add
                                         :roomuuid roomuuid}
                             :uuid      ws-request-uuid})
    (let [^bytes ws-data (ws-client-global-read)
          {:keys [eventname data]} (p/byte-array->proto->clj-data ws-data)]
      ;(is (= 0 (count (dynamo-db/get-room-users {:roomuuid roomuuid})))))))
      (is (empty? (dynamo-db/get-room-users {:roomuuid roomuuid}))))))



(defn -send-message-test []
  (reset-all-data)
  (let [useruuid-1 (util/random-uuid-str)
        client-1 (ws-client-instance {:useruuid useruuid-1})
        useruuid-2 (util/random-uuid-str)
        client-2 (ws-client-instance {:useruuid useruuid-2})]
    ;create room request
    (ws-client-write-instance client-1 {:eventname :create-chat-room-request
                                        :data      {:useruuid useruuid-1}
                                        :uuid      (util/random-uuid-str)})
    ;create room response
    (let [^bytes ws-data (ws-client-read-instance client-1)
          {:keys [eventname data uuid]} (p/byte-array->proto->clj-data ws-data)
          {:keys [status room]} data
          {:keys [authtoken roomuuid roomname]} room]
      (println "created room::" roomuuid)
      ;add user to room request
      ;(ws-client-write-instance client-1 {:eventname :add-user-to-chat-room-request
      ;                                    :data      {:useruuid useruuid-1
      ;                                                :roomuuid roomuuid}
      ;                                    :uuid      (util/random-uuid-str)})
      ;;add user to room response
      ;(println "add user to room response, client-1::"
      ;         (p/byte-array->proto->clj-data (ws-client-read-instance client-1)))
      ;add user to room request
      (ws-client-write-instance client-2 {:eventname :add-user-to-chat-room-request
                                          :data      {:useruuid useruuid-2
                                                      :roomuuid roomuuid}
                                          :uuid      (util/random-uuid-str)})
      (println "add user to room response, client-2::"
               (p/byte-array->proto->clj-data (ws-client-read-instance client-2)))
      ;send message test
      (ws-client-write-instance client-2 {:eventname :send-message-request
                                          :data {:roomuuid roomuuid
                                                 :message {:content "hello"
                                                           :messageuuid (util/random-uuid-str)}}
                                          :uuid (util/random-uuid-str)})
      (println "response, client-2::"
               (p/byte-array->proto->clj-data (ws-client-read-instance client-2)))
      (println "response, client-2::"
               (p/byte-array->proto->clj-data (ws-client-read-instance client-2)))
      (println "response, client-1::"
               (p/byte-array->proto->clj-data (ws-client-read-instance client-1))))))

(defn -retrieve-room-messages-test []
  (reset-all-data)
  (let [useruuid-1 (util/random-uuid-str)
        client-1 (ws-client-instance {:useruuid useruuid-1})
        roomuuid-1 (util/random-uuid-str)]
    ;add messages
    (dotimes [_ 3]
      (dynamo-db/add-message {:messageuuid (util/random-uuid-str)
                              :roomuuid roomuuid-1
                              :content "hello"}
                             (chan 1)))
    ;retrieve room message request
    (ws-client-write-instance client-1 {:eventname :retrieve-room-messages-request
                                        :data {:roomuuid roomuuid-1}
                                        :uuid (util/random-uuid-str)})
    ;retrieve room messages response
    (println "retrieve room messages::" (p/byte-array->proto->clj-data
                                          (ws-client-read-instance client-1)))))

(defn -set-typing-status-test []
  (reset-all-data)
  (let [useruuid-1 (util/random-uuid-str)
        client-1 (ws-client-instance {:useruuid useruuid-1})
        useruuid-2 (util/random-uuid-str)
        client-2 (ws-client-instance {:useruuid useruuid-2})]
    ;create room request
    (ws-client-write-instance client-1 {:eventname :create-chat-room-request
                                        :data      {:useruuid useruuid-1}
                                        :uuid      (util/random-uuid-str)})
    ;create room response
    (let [^bytes ws-data (ws-client-read-instance client-1)
          {:keys [eventname data uuid]} (p/byte-array->proto->clj-data ws-data)
          {:keys [status room]} data
          {:keys [authtoken roomuuid roomname]} room]
      (println "created room::" roomuuid)
      ;add user to room request
      (ws-client-write-instance client-1 {:eventname :add-user-to-chat-room-request
                                          :data      {:useruuid useruuid-1
                                                      :roomuuid roomuuid}
                                          :uuid      (util/random-uuid-str)})
      ;add user to room response
      (println "add user to room response, client-1::"
               (p/byte-array->proto->clj-data (ws-client-read-instance client-1)))
      ;add user to room request
      (ws-client-write-instance client-2 {:eventname :add-user-to-chat-room-request
                                          :data      {:useruuid useruuid-2
                                                      :roomuuid roomuuid}
                                          :uuid      (util/random-uuid-str)})
      ;add user to room response
      (println "add user to room response, client-2::"
               (p/byte-array->proto->clj-data (ws-client-read-instance client-2)))
      ;set typing status test
      (ws-client-write-instance client-2 {:eventname :set-typing-status-request
                                          :data {:roomuuid roomuuid
                                                 :useruuid useruuid-2
                                                 :typingstatus true}
                                          :uuid (util/random-uuid-str)})
      (println "response, client-2::"
               (p/byte-array->proto->clj-data (ws-client-read-instance client-2)))
      (println "response, client-2::"
               (p/byte-array->proto->clj-data (ws-client-read-instance client-2)))
      (println "response, client-1::"
               (p/byte-array->proto->clj-data (ws-client-read-instance client-1))))))


(defn -send-read-confirmation-test []
  (reset-all-data)
  (let [useruuid-1 (util/random-uuid-str)
        client-1 (ws-client-instance {:useruuid useruuid-1})
        useruuid-2 (util/random-uuid-str)
        client-2 (ws-client-instance {:useruuid useruuid-2})]
    ;create room request
    (ws-client-write-instance client-1 {:eventname :create-chat-room-request
                                        :data      {:useruuid useruuid-1}
                                        :uuid      (util/random-uuid-str)})
    ;create room response
    (let [^bytes ws-data (ws-client-read-instance client-1)
          {:keys [eventname data uuid]} (p/byte-array->proto->clj-data ws-data)
          {:keys [status room]} data
          {:keys [authtoken roomuuid roomname]} room]
      (println "created room::" roomuuid)
      ;add user to room request
      (ws-client-write-instance client-1 {:eventname :add-user-to-chat-room-request
                                          :data      {:useruuid useruuid-1
                                                      :roomuuid roomuuid}
                                          :uuid      (util/random-uuid-str)})
      ;add user to room response
      (println "add user to room response, client-1::"
               (p/byte-array->proto->clj-data (ws-client-read-instance client-1)))
      ;add user to room request
      (ws-client-write-instance client-2 {:eventname :add-user-to-chat-room-request
                                          :data      {:useruuid useruuid-2
                                                      :roomuuid roomuuid}
                                          :uuid      (util/random-uuid-str)})
      (println "add user to room response, client-2::"
               (p/byte-array->proto->clj-data (ws-client-read-instance client-2)))
      ;send message test
      (let [messageuuid-1 (util/random-uuid-str)]
        (ws-client-write-instance client-2 {:eventname :send-message-request
                                            :data      {:roomuuid roomuuid
                                                        :message  {:content     "hello"
                                                                   :messageuuid messageuuid-1}}
                                            :uuid      (util/random-uuid-str)})
        (println "response, client-2::"
                 (p/byte-array->proto->clj-data (ws-client-read-instance client-2)))
        (println "response, client-2::"
                 (p/byte-array->proto->clj-data (ws-client-read-instance client-2)))
        (println "response, client-1::"
                 (p/byte-array->proto->clj-data (ws-client-read-instance client-1)))
        (ws-client-write-instance client-1 {:eventname :send-read-confirmation-request
                                            :data      {:messageuuid messageuuid-1
                                                        :roomuuid roomuuid
                                                        :useruuid useruuid-1}})
        (println "response, client-1::"
                 (p/byte-array->proto->clj-data (ws-client-read-instance client-1)))
        (println "response, client-1::"
                 (p/byte-array->proto->clj-data (ws-client-read-instance client-1)))
        (println "response, client-2::"
                 (p/byte-array->proto->clj-data (ws-client-read-instance client-2)))))))


(defn -login-logout-test []
  (reset-all-data)
  (let [useruuid-1 (util/random-uuid-str)
        client-1 (ws-client-instance {:useruuid useruuid-1})
        useruuid-2 (util/random-uuid-str)
        client-2 (ws-client-instance {:useruuid useruuid-2})]
    ;register user 1
    (ws-client-write-instance client-1 {:eventname :create-user-request
                                        :data {:useruuid useruuid-1}
                                        :uuid (util/random-uuid-str)})
    ;register user 1 response
    (p/byte-array->proto->clj-data
      (ws-client-read-instance client-1))
    ;register user 2
    (ws-client-write-instance client-2 {:eventname :create-user-request
                                        :data {:useruuid useruuid-2}
                                        :uuid (util/random-uuid-str)})
    ;register user 2 response
    (p/byte-array->proto->clj-data
      (ws-client-read-instance client-2))

    ;create room request
    (ws-client-write-instance client-1 {:eventname :create-chat-room-request
                                        :data      {:useruuid useruuid-1}
                                        :uuid      (util/random-uuid-str)})
    ;create room response
    (let [{:keys [eventname data]} (p/byte-array->proto->clj-data
                                     (ws-client-read-instance client-1))
          {:keys [roomuuid]} (:room data)]
      (println "roomuuid" roomuuid)
      ;add user 2 to room
      (ws-client-write-instance client-1 {:eventname :add-user-to-chat-room-request
                                          :data      {:useruuid useruuid-2
                                                      :roomuuid roomuuid}
                                          :uuid      (util/random-uuid-str)})
      ;add user 2 to room response
      (ws-client-read-instance client-1)

      ;user 2 login
      (ws-client-write-instance client-2 {:eventname :login-request
                                          :data {:useruuid useruuid-2}
                                          :uuid (util/random-uuid-str)})
      ;user 2 login response
      (println "user 2 response::"
               (p/byte-array->proto->clj-data (ws-client-read-instance client-2))
               (p/byte-array->proto->clj-data (ws-client-read-instance client-2)))

      ;user 1 receive online status
      (println "user 1 receive online status::" (p/byte-array->proto->clj-data
                                                  (ws-client-read-instance client-1)))

      ;user 2 logout
      (ws-client-write-instance client-2 {:eventname :logout-request
                                          :data {:useruuid useruuid-2}
                                          :uuid (util/random-uuid-str)})

      ;user 2 logout response
      (println "user 2 response::"
               (p/byte-array->proto->clj-data (ws-client-read-instance client-2))
               (p/byte-array->proto->clj-data (ws-client-read-instance client-2)))

      ;user 1 receive online status
      (println "user 1 receive online status::" (p/byte-array->proto->clj-data
                                                  (ws-client-read-instance client-1)))
      )))


(defn -set-user-details-test []
  (reset-all-data)
  (let [useruuid-1 (util/random-uuid-str)
        client-1 (ws-client-instance {:useruuid useruuid-1})]
    ;create user request
    (ws-client-write-instance client-1 {:eventname :create-user-request
                                        :data {:useruuid useruuid-1}})
    ;response
    (println "create user response::"
             (p/byte-array->proto->clj-data
               (ws-client-read-instance client-1)))
    ;set user details
    (ws-client-write-instance client-1 {:eventname :set-user-details-request
                                        :data      {:useruuid    useruuid-1
                                                    :userdetails {:admin           true
                                                                  :avatarmonsterid 1
                                                                  :notincluded "notincluded"}}})
    ;response
    (println "set user details response::"
             (p/byte-array->proto->clj-data
               (ws-client-read-instance client-1)))

    ;get user
    (println "get user from db::"
             (dynamo-db/get-user {:useruuid useruuid-1}))

    ))



(deftest remove-user-from-chat-room-test
  (println "remove user from chat room test")
  (-remove-user-from-chat-room-test))

