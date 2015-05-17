(ns lvl6-chat.state)



(def ws-server (atom nil))

(def rabbit-mq-connection (agent nil))

(def rabbit-mq-agent (agent nil))

;example data:
;{:ws-uuid   {:queue-name "rabbit-mq-queue-auto-generated-name"
;             :rmq-ch rmq-channel-instance (not core.async channel)}
;each user WebSocket has its own queue (one user can have multiple WebSockets at once if using from different devices)
(def rmq-ws-queues (atom {}))

