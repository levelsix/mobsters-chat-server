(ns lvl6-chat.events
  (:require [lvl6-chat.dynamo-db :as dynamo-db]
            [lvl6-chat.io-utils :as io-utils]
            [lvl6-chat.protobuf :as p]))

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

;Events
;====================================

(defn create-user-request [rr data]
  (write-and-read-response rr (io-utils/blocking-io-loop dynamo-db/create-user data)))

(defn create-room-request [rr data]
  (write-and-read-response rr (io-utils/blocking-io-loop dynamo-db/create-room data)))

(defn add-user-to-chat-room-request [rr data]
  (write-and-read-response rr (io-utils/blocking-io-loop dynamo-db/add-user-to-chat-room data)))

(defn remove-user-from-chat-room-request [rr data]
  (write-and-read-response rr (io-utils/blocking-io-loop dynamo-db/remove-user-from-chat-room data)))

(defn process-request-response
  "Main request/response router via (condp = eventname)"
  [{:keys [request] :as rr}]
  (let [{:keys [eventname data]} request]
    (println "processing eventname::" eventname)
    (condp = eventname
      ;return
      :create-user-request (create-user-request rr data)
      :create-chat-room-request (create-room-request rr data)

      :add-user-to-chat-room-request (add-user-to-chat-room-request rr data)
      :remove-user-from-chat-room-request (remove-user-from-chat-room-request rr data)
      ;:login-request (write-and-read-response rr )
      ;else, just return a byte array as OK
      (throw (Exception. "Add eventname to events/process-request-response")))))


