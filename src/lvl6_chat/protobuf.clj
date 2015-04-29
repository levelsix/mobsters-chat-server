(ns lvl6-chat.protobuf
  (:require [flatland.protobuf.core :as proto :refer [protobuf protobuf-dump protobuf-load protodef]]
            [lvl6-chat.util :as util])
  (:import
    ;chat.proto
    com.lvl6.chatserver.Chat$TranslateLanguage
    com.lvl6.chatserver.Chat$ChatRoomTag
    com.lvl6.chatserver.Chat$ChatRoomProto
    com.lvl6.chatserver.Chat$ChatUserProto
    com.lvl6.chatserver.Chat$ChatMessageProto
    com.lvl6.chatserver.Chat$ChatUserDetailsProto
    com.lvl6.chatserver.Chat$TranslatedTextProto

    ;chat_event.proto
    com.lvl6.chatserver.ChatEvent$EventResponseStatus
    com.lvl6.chatserver.ChatEvent$ChatEventType
    com.lvl6.chatserver.ChatEvent$ChatEventProto

    ;create user
    com.lvl6.chatserver.ChatEvent$CreateUserRequestProto
    com.lvl6.chatserver.ChatEvent$CreateUserResponseProto

    com.lvl6.chatserver.ChatEvent$SendMessageRequestProto
    com.lvl6.chatserver.ChatEvent$SendMessageResponseProto
    com.lvl6.chatserver.ChatEvent$CreateUserRequestProto
    (com.google.protobuf ByteString)))


;ProtoDefs
;event type protodef
(def ChatEventProtoDef (protodef ChatEvent$ChatEventProto))

;create user
(def CreateUserRequestProtoDef (protodef ChatEvent$CreateUserRequestProto))
(def CreateUserResponseProtoDef (protodef ChatEvent$CreateUserResponseProto))

(def ChatRoomProtoDef (protodef Chat$ChatRoomProto))

(defn event-name-dispatch [eventname data protobuf-fn]
  (println "eventname:::" eventname)
  (condp = eventname
    :create-user-request (protobuf-fn CreateUserRequestProtoDef data)
    (throw (Exception. "Unsupported protobuf type in event-name-dispatch"))))

;ProtoDef constructor function
(defn chat-event-proto [{:keys [eventname data uuid]}]
  (protobuf ChatEventProtoDef {:eventname eventname
                               :data      (util/copy-to-byte-string (protobuf-dump data))
                               ;if uuid is not supplied, generate one
                               :uuid      (if uuid
                                            uuid
                                            (util/random-uuid-str))}))

;create user
(defn create-user-request-proto [{:keys [uuid] :as m}]
  (protobuf CreateUserRequestProtoDef m))

(defn create-user-response-proto [{:keys [status] :as m}]
  (protobuf CreateUserResponseProtoDef m))

;proto conversions


(defn proto->byte-array [proto]
  (protobuf-dump proto))

(defn byte-array->proto->clj-data [^bytes b-a]
  (let [{:keys [eventname data uuid] :as chat-event-proto} (protobuf-load ChatEventProtoDef b-a)
        _ (println "chat-event-proto" chat-event-proto)
        _ (println "eventname 1:::" eventname)
        data-byte-array (util/byte-string-to-byte-array data)
        data-deserialized (into {} (event-name-dispatch eventname data-byte-array protobuf-load))]
    {:eventname eventname
     :data data-deserialized
     :uuid uuid}))

