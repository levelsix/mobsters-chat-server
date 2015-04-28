(ns lvl6-chat.dynamo-db
  (:require [taoensso.faraday :as far]))


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
  (far/create-table client-opts :chat-users
                    [:uuid :s]  ; Primary key named "id", (:n => number type)
                    {:throughput {:read 1 :write 1} ; Read & write capacity (units/sec)
                     :block? true ; Block thread during table creation
                     }))

(defn delete-tables []
  (far/delete-table client-opts :chat-users))

(defn list-tables []
  (far/list-tables client-opts))

(defn create-user [{:keys [uuid]}]
  (far/put-item client-opts
                :chat-users
                {:uuid uuid}))
