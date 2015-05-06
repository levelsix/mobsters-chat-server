(ns lvl6-chat.state
  (:import (clojure.lang Keyword)))



(def ws-server (atom nil))

(def rabbit-mq-connection (agent nil))

(def rabbit-mq-agent (agent nil))

;example data:
;{:ws-uuid   {:queue-name "rabbit-mq-queue-auto-generated-name"
;             :rmq-ch rmq-channel-instance (not core.async channel)}
;each user WebSocket has its own queue (one user can have multiple WebSockets at once if using from different devices)
(def rmq-ws-queues (atom {}))

;example data:
;{:room-uuid {:queue-name "rabbit-mq-queue-auto-generated-name"
;             :rmq-ch rmq-channel-instance (not core.async channel)}
;update this when:
; - user connects to a ws and a room that he's part of is not in the atom (assoc)
; - no users interested in a room are currently connected (dissoc)
;(def rmq-room-queues (atom {}))

;example data:
;{:room-uuid #{user-uuid-1 user-uuid-2}}
;update this when:
; - users connects to a ws
; - users disconnects from a ws
; - notification comes on a rmq-user-queue that user got added/removed to/from a room
;get info from here when:
; - message comes on rmq-room-queue to determine which user websockets should receive that notification
;(def room-users (atom {}))

;example data
;{:user-uuid #{room-uuid-1 room-uuid-2}
;update this when:
; - users connect to websockets
; - notification comes on a rmq-user-queue that user got added/removed to/from a room
; -
;(def user-rooms (atom {}))