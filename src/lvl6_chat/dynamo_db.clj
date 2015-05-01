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
  (far/create-table client-opts :chatusers
                    [:uuid :s]  ; Primary key named "id", (:n => number type)
                    {:throughput {:read 1 :write 1} ; Read & write capacity (units/sec)
                     :block? true ; Block thread during table creation
                     }))

(defn delete-tables []
  (far/delete-table client-opts :chatusers))

(defn list-tables []
  (far/list-tables client-opts))

(defn get-user
  ([{:keys [uuid]} confirm-ch]
   (try (do
          (let [user (far/get-item client-opts
                                   :chatusers
                                   {:uuid uuid})]
            (>!! confirm-ch (if (= nil user) false user))))
        (catch Exception e (>!! confirm-ch e))))
  ([data]
   (let [confirm-ch (chan 1)]
     (get-user data confirm-ch)
     (<!! confirm-ch))))

(defn create-user [{:keys [uuid] :as data} confirm-ch]
  (try (do
         (let [user-exists? (get-user data)]
           (if (= false user-exists?)
             ;create user only when it doesn't exist already
             (let [auth-token (util/random-uuid-str)
                   auth-token-hashed (digest/sha-256 auth-token)
                   new-data (assoc data :authtoken auth-token-hashed)]
               (far/put-item client-opts
                             :chatusers
                             new-data)
               (>!! confirm-ch (get-user data)))
             (>!! confirm-ch user-exists?))))
       (catch Exception e (>!! confirm-ch e))))



