(ns lvl6-chat.dynamo-db
  (:require [taoensso.faraday :as far]
            [clojure.core.async :refer [chan go >! <! <!! >!! go-loop put! thread alts! alts!! timeout pipeline pipeline-blocking pipeline-async]]
            [lvl6-chat.util :as util]
            [amazonica.aws.dynamodbv2 :as dynamodbv2]
            [digest :as digest])
  (:import (clojure.lang Keyword)))


(def client-opts
  {;;; For DDB Local just use some random strings here, otherwise include your
   ;;; production IAM keys:
   :access-key "AKIAIPMBU57QNKCO3RJQ"
   :secret-key "VUJ6psXzXgacpecC718B3UUEm8AYwj6uqWykKGY8"
   :endpoint   "http://dynamodb.us-west-2.amazonaws.com"
   ;;; You may optionally override the default endpoint if you'd like to use DDB
   ;;; Local or a different AWS Region (Ref. http://goo.gl/YmV80o), etc.:
   ;; :endpoint "http://localhost:8000"                   ; For DDB Local
   ;; :endpoint "http://dynamodb.eu-west-1.amazonaws.com" ; For EU West 1 AWS region
   })


(defn create-tables []
  ;chat-users
  (far/create-table client-opts :chat-users
                    [:useruuid :s]                          ; Primary key named "id", (:n => number type)
                    {:throughput {:read 1 :write 1}         ; Read & write capacity (units/sec)
                     :block?     true                       ; Block thread during table creation
                     })

  ;chat-rooms
  (far/create-table client-opts :chat-rooms
                    [:roomuuid :s]                          ; Primary key named "id", (:n => number type)
                    {:throughput {:read 1 :write 1}         ; Read & write capacity (units/sec)
                     :block?     true                       ; Block thread during table creation
                     })
  ;:gsindexes    - [{:name _ :hash-keydef _ :range-keydef _
  ;                  :projection #{:all :keys-only [<attr> ...]}
  ;                  :throughput _}].
  ;chat-room-users
  (far/create-table client-opts :chat-room-users
                    [:autogenuuid :s]                       ; Primary key named "id", (:n => number type)
                    {:throughput {:read 1 :write 1}         ; Read & write capacity (units/sec)
                     :block?     true                       ; Block thread during table creation
                     :gsindexes  [{:name         "roomuuid_index"
                                   :hash-keydef  [:roomuuid :s]
                                   :range-keydef [:useruuid :s]
                                   :projection   #{:all}
                                   :throughput   {:read 1 :write 1}}
                                  {:name         "useruuid_index"
                                   :hash-keydef  [:useruuid :s]
                                   :range-keydef [:roomuuid :s]
                                   :projection   #{:all}
                                   :throughput   {:read 1 :write 1}}]})

  ;messages
  (far/create-table client-opts :chat-messages
                    [:messageuuid :s]                       ; Primary key named "id", (:n => number type)
                    {:throughput {:read 1 :write 1}         ; Read & write capacity (units/sec)
                     :block?     true                       ; Block thread during table creation
                     :gsindexes  [{:name         "room_messages_index"
                                   :hash-keydef  [:roomuuid :s]
                                   :range-keydef [:timestamp :n]
                                   :projection   #{:all}
                                   :throughput   {:read 1 :write 1}}]})

  ;chat-message-read
  (far/create-table client-opts :chat-message-read
                    [:autogenuuid :s]                       ; Primary key named "id", (:n => number type)
                    {:throughput {:read 1 :write 1}         ; Read & write capacity (units/sec)
                     :block?     true                       ; Block thread during table creation
                     :gsindexes  [{:name        "messages_read_index"
                                   :hash-keydef [:messageuuid :s]
                                   :projection  #{:all}
                                   :throughput  {:read 1 :write 1}}]})

  ;chat-user-online
  (far/create-table client-opts :chat-user-online
                    [:useruuid :s]
                    {:range-keydef [:last-seen-timestamp :n]
                     :throughput   {:read 1 :write 1}
                     :block?       true}))

(defn delete-tables []
  (try
    (do
      (far/delete-table client-opts :chat-users)
      (far/delete-table client-opts :chat-rooms)
      (far/delete-table client-opts :chat-room-users)
      (far/delete-table client-opts :chat-messages)
      (far/delete-table client-opts :chat-message-read)
      (far/delete-table client-opts :chat-user-online))
    (catch Exception e e)))

(defn list-tables []
  (far/list-tables client-opts))


(defn describe-table [table-name]
  (far/describe-table client-opts table-name))

;chat-users
;==============================
(defn get-user
  ([{:keys [useruuid return-authtoken?] :or {return-authtoken? false}} confirm-ch]
   (try (let [user (far/get-item client-opts
                                 :chat-users
                                 {:useruuid useruuid})
              user' (if return-authtoken?
                      ;leave as-is
                      user
                      ;else, remove authtoken before returning
                      (dissoc user :authtoken))]
          (>!! confirm-ch (if (= nil user') false user')))
        (catch Exception e (>!! confirm-ch e))))
  ([data]
   (let [confirm-ch (chan 1)]
     (get-user data confirm-ch)
     (<!! confirm-ch))))

(defn create-user
  "Creates a chat user"
  [{:keys [useruuid userdetails]
    :or {userdetails {}}
    :as data} confirm-ch]
  (try (let [user-exists? (get-user data)]
         (if (= false user-exists?)
           ;create user only when it doesn't exist already
           (let [auth-token (digest/sha-256 (util/random-uuid-str))
                 user-data {:useruuid          useruuid
                            :authtoken         auth-token
                            :userdetails (far/freeze userdetails)
                            :lastseentimestamp (util/timestamp-ms)}]
             (far/put-item client-opts
                           :chat-users
                           user-data)
             (>!! confirm-ch user-data))
           (>!! confirm-ch user-exists?)))
       (catch Exception e (>!! confirm-ch e))))

(defn update-user-last-seen-timestamp [{:keys [useruuid]}]
  (far/update-item client-opts
                   :chat-users
                   {:useruuid useruuid}
                   {:lastseentimestamp [:put (util/timestamp-ms)]}))

(defn update-user-details [{:keys [useruuid userdetails]
                            :or {userdetails {}}}
                           confirm-ch]
  (try (let []
         (far/update-item client-opts
                          :chat-users
                          {:useruuid useruuid}
                          {:userdetails [:put (far/freeze userdetails)]})
         (>!! confirm-ch true))
       (catch Exception e (>!! confirm-ch e))))


;chat-rooms
;==============================
(defn get-room
  ([{:keys [roomuuid useruuid return-authtoken?] :or {return-authtoken? false}} confirm-ch]
   (try (let [room (far/get-item client-opts
                                 :chat-rooms
                                 {:roomuuid roomuuid})
              ;if it's an owner, return the authtoken
              return-authtoken? (if (and (not (nil? useruuid))
                                         (= useruuid (get room :useruuid)))
                                  true
                                  false)
              ;remove authtoken by default
              room' (if return-authtoken? room (dissoc room :authtoken))]
          (>!! confirm-ch (if (= nil room') false room')))
        (catch Exception e (>!! confirm-ch e))))
  ([data]
   (let [confirm-ch (chan 1)]
     (get-room data confirm-ch)
     (<!! confirm-ch))))

(defn create-room
  "Create a chat room"
  [{:keys [useruuid roomname] :or {roomname "N/A"} :as data} confirm-ch]
  (try (let [room-uuid (util/random-uuid-str)
             auth-token (digest/sha-256 (util/random-uuid-str))
             new-data (assoc data :authtoken auth-token
                                  :roomuuid room-uuid
                                  :roomname roomname
                                  :useruuid useruuid)]
         (println "new-data" new-data)
         (doseq [[k v] new-data]
           (println (type v)))
         (far/put-item client-opts
                       :chat-rooms
                       new-data)
         (>!! confirm-ch new-data))
       (catch Exception e (>!! confirm-ch e))))

;chat-room-users
;============================
(defn add-user-to-chat-room
  "Add user to a chat room"
  [{:keys [useruuid roomuuid] :as data} confirm-ch]
  (println "add-user-to-chat-room data::" data)
  (try (let [autogenuuid (util/random-uuid-str)
             new-data (assoc data :autogenuuid autogenuuid)]
         (println "auto gen uuid:" autogenuuid)
         (far/put-item client-opts
                       :chat-room-users
                       new-data)
         (>!! confirm-ch {}))
       (catch Exception e (>!! confirm-ch e))))

(defn get-room-users
  "Retrieves all users per room"
  ([{:keys [roomuuid]} confirm-ch]
   (try (let [users (far/query client-opts
                               :chat-room-users
                               {:roomuuid [:eq roomuuid]}
                               {:index  "roomuuid_index"
                                :return :all-projected-attributes})]
          ;:return :all-attributes
          (println "users:")
          (println users)
          (>!! confirm-ch users))
        (catch Exception e (>!! confirm-ch e))))
  ([data]
   (let [confirm-ch (chan 1)]
     (get-room-users data confirm-ch)
     (<!! confirm-ch))))

(defn get-user-rooms
  "Retrieves all rooms for a user"
  ([{:keys [useruuid]} confirm-ch]
   (try (let [rooms (far/query client-opts
                               :chat-room-users
                               {:useruuid [:eq useruuid]}
                               {:index  "useruuid_index"
                                :return :all-attributes})]
          (>!! confirm-ch rooms))))
  ([data]
   (let [confirm-ch (chan 1)]
     (get-user-rooms data confirm-ch)
     (<!! confirm-ch))))

(defn remove-user-from-chat-room
  "Removes a user from a chat room"
  ([{:keys [useruuid roomuuid]} confirm-ch]
   (try (let [users (get-room-users {:roomuuid roomuuid})
              users-to-remove (filter #(= (get % :useruuid) useruuid) users)]
          ;remove users from room
          (println "inside remove-user-from-chat-room")
          (println "users:")
          (println users)
          (println "users-to-remove:")
          (println users-to-remove)
          (doseq [{:keys [autogenuuid]} users-to-remove]
            (far/delete-item client-opts :chat-room-users {:autogenuuid autogenuuid}))
          (>!! confirm-ch {}))
        (catch Exception e (>!! confirm-ch e))))
  ([data]
   (let [confirm-ch (chan 1)]
     (remove-user-from-chat-room data confirm-ch)
     (<!! confirm-ch))))


;chat-messages
;==============================

(defn add-message
  "Adds a message"
  [{:keys [messageuuid roomuuid content userdetails translatedcontent language] :as data} confirm-ch]
  (try
    (let [timestamp (util/timestamp-ms)]
      (far/put-item client-opts
                    :chat-messages
                    {:messageuuid       messageuuid
                     :userdetails       (far/freeze userdetails)
                     :language          language
                     :translatedcontent (far/freeze translatedcontent)
                     :roomuuid          roomuuid
                     :content           content
                     :timestamp         timestamp})
      (>!! confirm-ch true))
    (catch Exception e (>!! confirm-ch e))))

(defn get-message [{:keys [messageuuid] :as data}]
  (far/get-item client-opts
                :chat-messages
                data))


;Amazonica working examples
;(defn add-message-2
;  [{:keys [messageuuid roomuuid content] :as data} confirm-ch]
;  (try
;    (let [timestamp (util/timestamp-ms)
;          data' (assoc data :timestamp timestamp)]
;      (dynamodbv2/put-item client-opts
;                    :table-name "chat-messages"
;                    :item data')
;      (>!! confirm-ch true))
;    (catch Exception e (>!! confirm-ch e))))
;
;(defn get-message-2
;  [{:keys [messageuuid] :as data}]
;  (dynamodbv2/get-item client-opts
;                       :table-name "chat-messages"
;                       :key {:messageuuid {:s messageuuid}}))



(defn get-room-messages
  [{:keys [roomuuid timestamp limit] :or {timestamp (util/timestamp-ms)
                                          limit     5}}
   confirm-ch]
  (try (let [messages (far/query client-opts
                                 :chat-messages
                                 {:roomuuid  [:eq roomuuid]
                                  :timestamp [:le timestamp]}
                                 {:index  "room_messages_index"
                                  :limit  limit
                                  :return :all-attributes})]
         (>!! confirm-ch messages))
       (catch Exception e (>!! confirm-ch e))))

;chat-message-read
;========================
(defn add-message-read [{:keys [messageuuid useruuid] :as data} confirm-ch]
  (try (let [autogenuuid (util/random-uuid-str)
             new-data (assoc data :autogenuuid autogenuuid)]
         (far/put-item client-opts
                       :chat-message-read
                       new-data)
         (>!! confirm-ch true))
       (catch Exception e (>!! confirm-ch e))))

(defn get-message-read-users [{:keys [messageuuid]} confirm-ch]
  (try (let [result (far/query client-opts :chat-message-read
                               {:messageuuid [:eq messageuuid]}
                               {:index  "messages_read_index"
                                :return :all-attributes})]
         (>!! confirm-ch result))
       (catch Exception e (>!! confirm-ch e))))

;chat-user-online
;==========================
(defn add-user-last-seen
  "Adds an entry for an user being online"
  [{:keys [useruuid last-seen-timestamp] :or {last-seen-timestamp (util/timestamp-ms)} :as data}]
  (far/put-item client-opts
                :chat-user-online
                {:useruuid            useruuid
                 :last-seen-timestamp last-seen-timestamp}))

(defn get-user-last-seen
  "Returns all the times the user has been seen between start and end timestamps"
  [{:keys [useruuid last-seen-timestamp-start last-seen-timestamp-end]}]
  (far/query client-opts
             :chat-user-online
             {:useruuid            [:eq useruuid]
              :last-seen-timestamp [:between [last-seen-timestamp-start last-seen-timestamp-end]]}))



