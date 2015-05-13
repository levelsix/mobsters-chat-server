(ns lvl6-chat.events
  (:require [lvl6-chat.dynamo-db :as dynamo-db]
            [lvl6-chat.io-utils :as io-utils]
            [lvl6-chat.rabbit-mq :as rabbit-mq]
            [lvl6-chat.protobuf :as p])
  (:import (clojure.lang APersistentMap APersistentVector)))

;Helper fns
;====================================
(defn add-status-to-result [result]
  (if (instance? Exception result)
    {:status :error}
    (assoc result :status :success)))

(defn write-response
  "Handles WebSocket requests that only require an ack for write success"
  [{:keys [response] :as rr} result]
  (let [status (if result :success :error)]
    ;return byte array data as a response
    (p/clj-data->proto->byte-array (assoc response :data {:status status}))))

(defn write-and-read-response
  "Handles websocket requests that might have both read and write portion;
   Those requests usually returns something more than just true/OK"
  [{:keys [response] :as rr} result]
  (let [result (add-status-to-result result)]
    (println "result::" result)
    (p/clj-data->proto->byte-array (assoc response :data result))))

;Events helper fns
(defn get-useruuids-in-room [roomuuid]
  (->> (io-utils/blocking-io-loop dynamo-db/get-room-users {:roomuuid roomuuid})
       (mapv :useruuid)))

(defn get-roomuuids-for-user [useruuid]
  (->> (io-utils/blocking-io-loop dynamo-db/get-user-rooms {:useruuid useruuid})
       (mapv :roomuuid)))

(defn retrieve-room-messages-join
  "Joins rooms with read receipts"
  [{:keys [roomuuid]}]
  (let [messages (io-utils/blocking-io-loop dynamo-db/get-room-messages {:roomuuid roomuuid})
        messages' (mapv (fn [{:keys [messageuuid] :as message}]
                         ;add useruuids who have read each message
                         (assoc message :readuseruuids
                                        ;fetch the users who have read a messages
                                        (->> (io-utils/blocking-io-loop dynamo-db/get-message-read-users {:messageuuid messageuuid})
                                             (mapv :useruuid))))
                       messages)]
     messages'))

(defn login-join
  "Joins chat rooms with last message and users per room"
  [{:keys [useruuid]}]
  (let [user-room-rows (io-utils/blocking-io-loop dynamo-db/get-user-rooms {:useruuid useruuid})
        user-room-rows' (mapv (fn [{:keys [roomuuid] :as user-room-row}]
                               ;get the room row
                               (let [chat-room-row (io-utils/blocking-io-loop dynamo-db/get-room {:roomuuid roomuuid
                                                                                                  :useruuid useruuid})
                                     ;get all users in the room
                                     useruuids (get-useruuids-in-room roomuuid)
                                     users (->> useruuids
                                                (mapv #(io-utils/blocking-io-loop dynamo-db/get-user {:useruuid %}))
                                                (filterv #(instance? APersistentMap %)))]
                                 (assoc chat-room-row :user users
                                                      :lastmessage (retrieve-room-messages-join {:roomuuid roomuuid}))))
                             user-room-rows)]
    user-room-rows'))



;Events
;====================================

(defn create-user-request [rr data]
  (write-and-read-response rr (io-utils/blocking-io-loop dynamo-db/create-user data)))

(defn create-room-request [rr {:keys [^String useruuid ^String roomname]}]
  (write-and-read-response rr (do
                                ;create the room
                                (let [{:keys [roomuuid] :as room-row} (io-utils/blocking-io-loop
                                                                                  dynamo-db/create-room
                                                                                  {:useruuid useruuid
                                                                                   :roomname roomname})]
                                  (println "roomuuid from create-room-request::" roomuuid)
                                  ;add the room owner to the room
                                  (io-utils/blocking-io-loop dynamo-db/add-user-to-chat-room {:useruuid useruuid
                                                                                              :roomuuid roomuuid})
                                  ;return
                                  {:room room-row}))))

(defn add-user-to-chat-room-request [rr data]
  (write-and-read-response rr (io-utils/blocking-io-loop dynamo-db/add-user-to-chat-room data)))

(defn remove-user-from-chat-room-request [rr data]
  (write-and-read-response rr (io-utils/blocking-io-loop dynamo-db/remove-user-from-chat-room data)))

(defn send-message-request [rr {:keys [^APersistentMap message ^String roomuuid] :as data}]
  ;destructure the message
  (let [{:keys [^String messageuuid ^String content]} message]
    (write-response rr (do
                         ;save message to dynamodb
                         (io-utils/blocking-io-loop dynamo-db/add-message {:messageuuid messageuuid
                                                                           :roomuuid roomuuid
                                                                           :content content})
                         ;notify other people of message
                         (let [useruuids-in-room (get-useruuids-in-room roomuuid)]
                           ;publish data to rabbitmq
                           (doseq [useruuid useruuids-in-room]
                             (rabbit-mq/publish-update (p/clj-data->proto->byte-array {:eventname :receive-message
                                                                                       :data      data})
                                                       useruuid)))
                         true))))

(defn retrieve-room-messages-request [rr {:keys [^String roomuuid]}]
  (write-and-read-response rr {:message (retrieve-room-messages-join {:roomuuid roomuuid})}))

(defn set-typing-status-request [rr {:keys [^String roomuuid ^String useruuid ^Boolean typingstatus] :as data}]
  (write-response rr (let [^APersistentVector useruuids-in-room (get-useruuids-in-room roomuuid)]
                       ;publish data to RabbitMQ
                       (doseq [^String useruuid useruuids-in-room]
                         (rabbit-mq/publish-update (p/clj-data->proto->byte-array {:eventname :receive-typing-status
                                                                                   :data      data})
                                                   useruuid))
                       true)))

(defn send-read-confirmation-request [rr {:keys [^String messageuuid ^String useruuid ^String roomuuid] :as data}]
  (write-response rr (let [^APersistentVector useruuids-in-room (get-useruuids-in-room roomuuid)]
                       ;public data to RabbitMQ
                       (doseq [^String useruuid useruuids-in-room]
                         (rabbit-mq/publish-update (p/clj-data->proto->byte-array {:eventname :receive-read-confirmation
                                                                                   :data      data})
                                                   useruuid))
                       ;write to dynamo
                       (io-utils/blocking-io-loop dynamo-db/add-message-read data))))


(defn login-request [rr {:keys [^String useruuid] :as data}]
  (write-and-read-response rr
                           (let [rooms (login-join {:useruuid useruuid})
                                 useruuids (->> rooms
                                                (mapcat (fn [room]
                                                          (map :useruuid (get room :user))))
                                                (vec))]
                             ;retrieve all users in those rooms
                             (doseq [useruuid useruuids]
                               ;send data to RabbitMQ
                               (rabbit-mq/publish-update
                                 (p/clj-data->proto->byte-array {:eventname :receive-online-status
                                                                 :data      data})
                                 useruuid))
                             {:room rooms})))

(defn logout-request [rr {:keys [^String useruuid]}])


(defn process-request-response
  "Main request/response router via (condp = eventname)"
  [{:keys [request response] :as rr}]
  (let [{:keys [eventname data]} request]
    (println "processing eventname::" eventname)
    (condp = eventname
      ;return
      :create-user-request (create-user-request rr data)
      :create-chat-room-request (create-room-request rr data)
      :add-user-to-chat-room-request (add-user-to-chat-room-request rr data)
      :remove-user-from-chat-room-request (remove-user-from-chat-room-request rr data)
      :send-message-request (send-message-request rr data)
      :retrieve-room-messages-request (retrieve-room-messages-request rr data)
      :set-typing-status-request (set-typing-status-request rr data)
      :send-read-confirmation-request (send-read-confirmation-request rr data)
      :login-request (login-request rr data)
      :logout-request (logout-request rr data)
      ;:login-request (write-and-read-response rr )
      ;else, just return a byte array as OK
      (throw (Exception. "Add eventname to events/process-request-response")))))


