(ns lvl6-chat.events
  (:require [lvl6-chat.dynamo-db :as dynamo-db]
            [lvl6-chat.io-utils :as io-utils]
            [lvl6-chat.rabbit-mq :as rabbit-mq]
            [lvl6-chat.protobuf :as p]
            [lvl6-chat.util :as util])
  (:import (clojure.lang APersistentMap APersistentVector)))

;Helper fns
;====================================


(defn create-response
  "Handles WebSocket responses;
   Adds :responseinfo and :data to WebSocket response"
  [{:keys [response] :as rr} io-f]
  (let [result (try (io-f) (catch Exception e e))
        response
        (if (instance? Exception result)
          ;error, return :exceptionmessage to the client
          (assoc response :responseinfo {:status              :error
                                         :exceptionmessage    (.getMessage result)
                                         :exceptionstacktrace (util/stack-trace-as-string result)}
                          :data {})
          ;everything seems OK, return success
          (assoc response :responseinfo {:status :success}
                          :data result))]
    (p/clj-data->proto->byte-array response)))

;Events helper fns
(defn get-useruuids-in-room! [roomuuid]
  (->> (io-utils/blocking-io-loop dynamo-db/get-room-users {:roomuuid roomuuid})
       (mapv :useruuid)))

(defn get-roomuuids-for-user! [useruuid]
  (->> (io-utils/blocking-io-loop dynamo-db/get-user-rooms {:useruuid useruuid})
       (mapv :roomuuid)))

(defn retrieve-room-messages-join!
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

(defn login-join!
  "Joins chat rooms with last message and users per room"
  [{:keys [useruuid]}]
  (let [user-room-rows (io-utils/blocking-io-loop dynamo-db/get-user-rooms {:useruuid useruuid})
        user-room-rows' (mapv (fn [{:keys [roomuuid] :as user-room-row}]
                                ;get the room row
                                (let [chat-room-row (io-utils/blocking-io-loop dynamo-db/get-room {:roomuuid roomuuid
                                                                                                   :useruuid useruuid})
                                      ;get all users in the room
                                      useruuids (get-useruuids-in-room! roomuuid)
                                      users (->> useruuids
                                                 (mapv #(io-utils/blocking-io-loop dynamo-db/get-user {:useruuid %}))
                                                 (filterv #(instance? APersistentMap %)))]
                                  (assoc chat-room-row :user users
                                                       :lastmessage (retrieve-room-messages-join! {:roomuuid roomuuid}))))
                              user-room-rows)]
    user-room-rows'))



;Events
;====================================

(defn create-user-request [rr data]
  (create-response rr (fn [] (io-utils/blocking-io-loop dynamo-db/create-user data))))

(defn create-room-request [rr {:keys [^String useruuid ^String roomname]}]
  (create-response rr (fn []
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
  (create-response rr (fn []
                        (io-utils/blocking-io-loop dynamo-db/add-user-to-chat-room data)
                        ;return
                        {})))

(defn remove-user-from-chat-room-request [rr data]
  (create-response rr (fn []
                        (io-utils/blocking-io-loop dynamo-db/remove-user-from-chat-room data)
                        ;return
                        {})))

(defn send-message-request [rr {:keys [^APersistentMap message ^String roomuuid] :as data}]
  ;destructure the message
  (let [{:keys [^String messageuuid ^String content]} message]
    (create-response rr (fn []
                          ;save message to dynamodb
                          (io-utils/blocking-io-loop dynamo-db/add-message {:messageuuid messageuuid
                                                                            :roomuuid    roomuuid
                                                                            :content     content})
                          ;notify other people of message
                          (let [useruuids-in-room (get-useruuids-in-room! roomuuid)]
                            ;publish data to rabbitmq
                            (doseq [useruuid useruuids-in-room]
                              (rabbit-mq/publish-update
                                (p/clj-data->proto->byte-array {:eventname :receive-message
                                                                :data      data})
                                useruuid)))
                          ;return
                          {}))))

(defn retrieve-room-messages-request [rr {:keys [^String roomuuid]}]
  (create-response rr (fn []
                        ;return
                        {:message (retrieve-room-messages-join! {:roomuuid roomuuid})})))

(defn set-typing-status-request [rr {:keys [^String roomuuid ^String useruuid ^Boolean typingstatus] :as data}]
  (create-response rr (fn []
                        (let [^APersistentVector useruuids-in-room (get-useruuids-in-room! roomuuid)]
                          ;publish data to RabbitMQ
                          (doseq [^String useruuid useruuids-in-room]
                            (rabbit-mq/publish-update
                              (p/clj-data->proto->byte-array {:eventname :receive-typing-status
                                                              :data      data})
                              useruuid))
                          ;return
                          {}))))

(defn send-read-confirmation-request [rr {:keys [^String messageuuid ^String useruuid ^String roomuuid] :as data}]
  (create-response rr (fn []
                        (let [^APersistentVector useruuids-in-room (get-useruuids-in-room! roomuuid)]
                          ;public data to RabbitMQ
                          (doseq [^String useruuid useruuids-in-room]
                            (rabbit-mq/publish-update
                              (p/clj-data->proto->byte-array {:eventname :receive-read-confirmation
                                                              :data      data})
                              useruuid))
                          ;write to dynamo
                          (io-utils/blocking-io-loop dynamo-db/add-message-read data)
                          ;return
                          {}))))


(defn login-request [rr {:keys [^String useruuid] :as data}]
  (create-response rr (fn []
                        (let [rooms (login-join! {:useruuid useruuid})
                              useruuids (->> rooms
                                             (mapcat (fn [room]
                                                       (map :useruuid (get room :user))))
                                             (vec))]
                          ;retrieve all users in those rooms
                          (doseq [useruuid useruuids]
                            ;send data to RabbitMQ
                            (rabbit-mq/publish-update
                              (p/clj-data->proto->byte-array {:eventname :receive-online-status
                                                              :data      {:useruuid     useruuid
                                                                          :onlinestatus true}})
                              useruuid))
                          ;return
                          {:room rooms}))))

(defn logout-request [rr {:keys [^String useruuid]}]
  (create-response rr
                   ;grab all of user's rooms
                   (fn []
                     (let [roomuuids (get-roomuuids-for-user! useruuid)
                           ;get all users in all relevant rooms
                           all-useruuids (flatten (for [roomuuid roomuuids] (get-useruuids-in-room! roomuuid)))]
                       ;notify all relevant users that useruuid went offline
                       (doseq [u all-useruuids]
                         (rabbit-mq/publish-update
                           (p/clj-data->proto->byte-array {:eventname :receive-online-status
                                                           :data      {:useruuid     useruuid
                                                                       :onlinestatus false}})
                           u))
                       ;return
                       {}))))

(defn set-user-details-request [rr {:keys [^String useruuid ^APersistentMap userdetails]}]
  (create-response rr
                   (fn []
                     (println "going to update userdetails::" userdetails)
                     (io-utils/blocking-io-loop dynamo-db/update-user-details
                                                {:useruuid    useruuid
                                                 :userdetails userdetails})
                     ;return
                     {})))


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
      :set-user-details-request (set-user-details-request rr data)
      ;else, just return a byte array as OK
      (throw (Exception. "Add eventname to events/process-request-response")))))


