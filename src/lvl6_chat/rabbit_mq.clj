(ns lvl6-chat.rabbit-mq
  (:require [lvl6-chat.state :as state]
            [langohr.core :as rmq]
            [langohr.channel :as lch]
            [langohr.queue :as lq]
            [langohr.exchange :as le]
            [langohr.consumers :as lc]
            [langohr.basic :as lb]
            [clojure.core.async :refer [chan close! go >! <! <!! >!! go-loop put! thread alts! alts!! timeout pipeline pipeline-blocking pipeline-async]]))


(defn connect-to-rabbit-mq []
  (let [result-ch (chan 1)]
    (send-off state/rabbit-mq-connection (fn [rmq-conn]
                                           (if (or (= nil rmq-conn)
                                                   (not (rmq/open? rmq-conn)))
                                             ;connect
                                             (let [new-conn (rmq/connect)]
                                               (println "new connection")
                                               (>!! result-ch new-conn)
                                               ;set the agent to new state
                                               new-conn)
                                             ;else, return existing connection
                                             (do
                                               (println "return existing connection")
                                               (>!! result-ch rmq-conn)
                                               rmq-conn))))
    (<!! result-ch)))

(def ^{:const true} exchange-name "lvl6.chat")
(def ^{:const true} default-exchange-name "amq.direct")

(defn rmq-subscribe [rmq-ch ^String uuid]
  (let [queue-name (:queue (lq/declare rmq-ch))
        handler (fn [ch meta ^bytes payload]
                  (println "got payload for user" uuid "::" payload))]
    (lq/bind rmq-ch queue-name default-exchange-name {:routing-key uuid})
    (lc/subscribe rmq-ch queue-name handler {:auto-ack true})
    ;add to atom
    (swap! state/rmq-user-queues assoc uuid queue-name)
    true))

(defn start-consumer
  "Starts a consumer bound to the given topic exchange in a separate thread"
  [rmq-ch topic-name queue-name]
  ;create a new queue
  (let [queue-name' (:queue (lq/declare rmq-ch queue-name {:exclusive true :auto-delete true}))
        handler (fn [ch {:keys [routing-key] :as meta} ^bytes payload]
                  (println
                    (format "[consumer] from %s, routing key: %s" queue-name' routing-key))
                  (clojure.pprint/pprint payload))]
    ;subscribe to a topic name
    (lq/bind rmq-ch queue-name' exchange-name {:routing-key topic-name})
    ;add default consumer to a queue
    (lc/subscribe rmq-ch queue-name' handler {:auto-ack true})))

(defn publish-update
  "Publishes an update "
  [rmq-ch payload routing-key]
  (lb/publish rmq-ch exchange-name routing-key payload))

(defn init-user-rmq-ch
  "Inits RabbitMQ subscription for messages directed to a user only"
  [user-uuid]
  (let [conn (connect-to-rabbit-mq)
        rmq-ch (lch/open conn)]
    (rmq-subscribe rmq-ch user-uuid)))

(defn init []
  (let [conn (connect-to-rabbit-mq)
        rmq-ch (lch/open conn)]
    ;declare exchange
    (le/declare rmq-ch exchange-name "topic" {:durable true})
    ;(rmq/close rmq-ch)
    ;(rmq/close conn)
    ))


(defn close-rabbit-mq-connection []
  (send-off state/rabbit-mq-connection (fn [rmq-conn]
                                         (rmq/close rmq-conn)
                                         rmq-conn)))
