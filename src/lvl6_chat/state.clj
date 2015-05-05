(ns lvl6-chat.state
  (:import (clojure.lang Keyword)))



(def ws-server (atom nil))

(def rabbit-mq-connection (agent nil))

;example data:
;{:user-uuid "rabbit-mq-queue-auto-generated-name", etc}
(def rmq-user-queues (atom {}))

;(defn add-rmq-user-queue [^String uuid ^String queue-name]
;  (swap! rmq-user-queues assoc uuid queue-name))
;
;(defn remove-rmq-user-queue [^String uuid]
;  (swap! rmq-user-queues dissoc uuid))