(ns lvl6-chat.dynamo-db
  (:require [taoensso.faraday :as far]
            [clojure.core.async :refer [chan go >! <! <!! >!! go-loop put! thread alts! alts!! timeout pipeline pipeline-blocking pipeline-async]]
            [lvl6-chat.util :as util]
            [digest :as digest]))


(def client-opts
  {;;; For DDB Local just use some random strings here, otherwise include your
   ;;; production IAM keys:
   :access-key "<AWS_DYNAMODB_ACCESS_KEY>"
   :secret-key "<AWS_DYNAMODB_SECRET_KEY>"
   :endpoint "http://localhost:8000"
   ;;; You may optionally override the default endpoint if you'd like to use DDB
   ;;; Local or a different AWS Region (Ref. http://goo.gl/YmV80o), etc.:
   ;; :endpoint "http://localhost:8000"                   ; For DDB Local
   ;; :endpoint "http://dynamodb.eu-west-1.amazonaws.com" ; For EU West 1 AWS region
   })


(defn create-tables []
  ;chatusers
  (far/create-table client-opts :chatusers
                    [:useruuid :s]                          ; Primary key named "id", (:n => number type)
                    {:throughput {:read 1 :write 1}         ; Read & write capacity (units/sec)
                     :block?     true                       ; Block thread during table creation
                     })

  ;chatrooms
  (far/create-table client-opts :chatrooms
                    [:roomuuid :s]  ; Primary key named "id", (:n => number type)
                    {:throughput {:read 1 :write 1} ; Read & write capacity (units/sec)
                     :block? true ; Block thread during table creation

                     })
  ;:gsindexes    - [{:name _ :hash-keydef _ :range-keydef _
  ;                  :projection #{:all :keys-only [<attr> ...]}
  ;                  :throughput _}].
  ;chatroomusers
  (far/create-table client-opts :chatroomusers
                    [:autogenuuid :s]  ; Primary key named "id", (:n => number type)
                    {:throughput {:read 1 :write 1} ; Read & write capacity (units/sec)
                     :block?     true ; Block thread during table creation
                     :gsindexes  [{:name        "roomuuid_index"
                                   :hash-keydef [:roomuuid :s]
                                   :projection  #{:all}
                                   :throughput  {:read 1 :write 1}}]}))

(defn delete-tables []
  (far/delete-table client-opts :chatusers)
  (far/delete-table client-opts :chatrooms)
  (far/delete-table client-opts :chatroomusers))

(defn list-tables []
  (far/list-tables client-opts))

(defn get-user
  ([{:keys [useruuid]} confirm-ch]
   (try (let [user (far/get-item client-opts
                                 :chatusers
                                 {:useruuid useruuid})]
          (>!! confirm-ch (if (= nil user) false user)))
        (catch Exception e (>!! confirm-ch e))))
  ([data]
   (let [confirm-ch (chan 1)]
     (get-user data confirm-ch)
     (<!! confirm-ch))))

(defn create-user
  "Creates a chat user"
  [{:keys [useruuid] :as data} confirm-ch]
  (try (let [user-exists? (get-user data)]
         (if (= false user-exists?)
           ;create user only when it doesn't exist already
           (let [auth-token (digest/sha-256 (util/random-uuid-str))
                 new-data (assoc data :authtoken auth-token)]
             (far/put-item client-opts
                           :chatusers
                           new-data)
             (>!! confirm-ch (get-user new-data)))
           (>!! confirm-ch user-exists?)))
       (catch Exception e (>!! confirm-ch e))))

(defn get-room
  ([{:keys [roomuuid]} confirm-ch]
   (try (let [room (far/get-item client-opts
                                 :chatrooms
                                 {:roomuuid roomuuid})]
          (>!! confirm-ch (if (= nil room) false room)))
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
                                  :roomname roomname)]
         (println "new-data" new-data)
         (doseq [[k v] new-data]
           (println (type v)))
         (far/put-item client-opts
                         :chatrooms
                         new-data)
         (>!! confirm-ch {:room new-data}))
       (catch Exception e (>!! confirm-ch e))))

(defn add-user-to-chat-room
  "Add user to a chat room"
  [{:keys [useruuid roomuuid] :as data} confirm-ch]
  (try (let [autogenuuid (util/random-uuid-str)
             new-data (assoc data :autogenuuid autogenuuid)]
         (println "auto gen uuid:" autogenuuid)
         (far/put-item client-opts
                       :chatroomusers
                       new-data)
         (>!! confirm-ch {}))
       (catch Exception e (>!! confirm-ch e))))

(defn get-room-users
  ([{:keys [roomuuid]} confirm-ch]
   (try (let [users (far/query client-opts :chatroomusers {:roomuuid [:eq roomuuid]} {:index "roomuuid_index"
                                                                                      :return :all-attributes})]
          (>!! confirm-ch users))
        (catch Exception e (>!! confirm-ch e))))
  ([data]
   (let [confirm-ch (chan 1)]
     (get-room-users data confirm-ch)
     (<!! confirm-ch))))

(defn remove-user-from-chat-room
  "Removes a user from a chat room"
  ([{:keys [useruuid roomuuid]} confirm-ch]
   (try (let [users (get-room-users {:roomuuid roomuuid})
              users-to-remove (filter #(= (get % :useruuid) useruuid) users)]
          ;remove users from room
          (doseq [{:keys [autogenuuid]} users-to-remove]
            (far/delete-item client-opts :chatroomusers {:autogenuuid autogenuuid}))
          (>!! confirm-ch {}))
        (catch Exception e (>!! confirm-ch e))))
  ([data]
    (let [confirm-ch (chan 1)]
      (remove-user-from-chat-room data confirm-ch)
      (<!! confirm-ch))))








