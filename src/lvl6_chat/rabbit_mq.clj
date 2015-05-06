(ns lvl6-chat.rabbit-mq
  (:require [lvl6-chat.state :as state]
            [langohr.core :as rmq]
            [langohr.channel :as lch]
            [langohr.queue :as lq]
            [langohr.exchange :as le]
            [langohr.consumers :as lc]
            [langohr.basic :as lb]
            [clojure.core.async :refer [chan close! go >! <! <!! >!! go-loop put! thread alts! alts!! timeout pipeline pipeline-blocking pipeline-async]]))


(defn connect-to-rabbit-mq
  "Returns an open RabbitMQ connection"
  []
  (let [result-ch (chan 1)]
    (send-off state/rabbit-mq-connection (fn [current-conn]
                                           (if (or (= nil current-conn)
                                                   (not (rmq/open? current-conn)))
                                             ;connect
                                             (let [new-conn (rmq/connect)]
                                               (println "new connection")
                                               (>!! result-ch new-conn)
                                               ;set the agent to new state
                                               new-conn)
                                             ;else, return existing connection
                                             (do
                                               (println "return existing connection")
                                               (>!! result-ch current-conn)
                                               current-conn))))
    (<!! result-ch)))

(def ^{:const true} exchange-name "lvl6.chat")

(defn rmq-subscribe [rmq-ch ^String web-socket-unique-key]
  (let [queue-name (:queue (lq/declare rmq-ch "" {:exclusive true}))
        handler (fn [ch meta ^bytes payload]
                  (println "got payload for user" web-socket-unique-key "::" payload))]
    (lq/bind rmq-ch queue-name exchange-name {:routing-key web-socket-unique-key})
    (lc/subscribe rmq-ch queue-name handler {:auto-ack true})
    queue-name))

(defn publish-update
  "Publishes an update "
  [payload routing-key]
  (let [conn (connect-to-rabbit-mq)
        rmq-ch (lch/open conn)]
    (lb/publish rmq-ch exchange-name routing-key payload)
    (rmq/close rmq-ch)))

(defn delete-queue
  "Ad hoc delete of queue by name, convenient for use from REPL"
  [queue-name]
  (let [conn (connect-to-rabbit-mq)
        rmq-ch (lch/open conn)]
    (lq/delete rmq-ch queue-name)
    (rmq/close rmq-ch)))

(defn close-rabbit-mq-connection
  "Disconnects RabbitMQ TCP connection"
  []
  (send-off state/rabbit-mq-connection (fn [rmq-conn]
                                         (rmq/close rmq-conn)
                                         rmq-conn)))



;Public Methods
;==================================================================
(defn start-subscription!
  "Inits RabbitMQ subscription for messages directed to a user"
  [web-socket-unique-key]
  (let [conn (connect-to-rabbit-mq)
        rmq-ch (lch/open conn)
        queue-name (rmq-subscribe rmq-ch web-socket-unique-key)]
    ;add to atom
    (swap! state/rmq-ws-queues assoc web-socket-unique-key {:queue-name queue-name
                                                            :rmq-ch     rmq-ch})))


(defn- safe-restart-agent-if-error [a]
  (try (if-not (= nil (agent-error state/rabbit-mq-agent))
         (restart-agent state/rabbit-mq-agent nil))
       (catch Exception e e)))

(defn stop-subscription!
  "Called when user disconnects from a WebSocket"
  [web-socket-unique-key]
  (swap! state/rmq-ws-queues (fn [x]
                               ;ensure agent doesn't have errors
                               (safe-restart-agent-if-error state/rabbit-mq-agent)
                               (send-off state/rabbit-mq-agent (fn [_]
                                                                 (let [{:keys [rmq-ch queue-name]} (get x web-socket-unique-key)]
                                                                   ;delete if rmq-ch, and the queue exist, and channel is stil open
                                                                   (when-not (and (nil? rmq-ch)
                                                                                  (nil? queue-name)
                                                                                  (rmq/open? rmq-ch))
                                                                     (lq/delete rmq-ch queue-name)
                                                                     (rmq/close rmq-ch)))
                                                                 (println "closed rmq-ch")
                                                                 ;agent for side effects, keep state as nil
                                                                 nil))
                               ;clean up memory
                               (dissoc x web-socket-unique-key))))


(defn init
  "Called at server start"
  []
  (let [conn (connect-to-rabbit-mq)
        rmq-ch (lch/open conn)]
    ;declare exchange
    (le/declare rmq-ch exchange-name "topic" {:durable true})
    ;close the rmq channel
    (rmq/close rmq-ch)))