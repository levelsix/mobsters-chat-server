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

    ;login
    com.lvl6.chatserver.ChatEvent$LoginRequestProto
    com.lvl6.chatserver.ChatEvent$LoginResponseProto


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
;login
(def LoginRequestProtoDef (protodef ChatEvent$LoginRequestProto))
(def LoginResponseProtoDef (protodef ChatEvent$LoginResponseProto))

(def ChatRoomProtoDef (protodef Chat$ChatRoomProto))

(defn event-name-dispatch [eventname data protobuf-fn]
  (condp = eventname
    ;create user
    :create-user-request (protobuf-fn CreateUserRequestProtoDef data)
    :create-user-response (protobuf-fn CreateUserResponseProtoDef data)
    ;login
    :login-request (protobuf-fn LoginRequestProtoDef data)
    :login-response (protobuf-fn LoginResponseProtoDef data)
    (throw (Exception. "Unsupported protobuf type in event-name-dispatch"))))

;ProtoDef constructor function
(defn chat-event-proto [{:keys [eventname data uuid]}]
  (protobuf ChatEventProtoDef {:eventname eventname
                               :data      (util/copy-to-byte-string (protobuf-dump data))
                               ;if uuid is not supplied, generate one
                               :uuid      (if uuid
                                            uuid
                                            (util/random-uuid-str))}))

;Public methods
(defn byte-array->proto->clj-data
  "Takes a byte array, turns it into a protobuf, and then into Clojure data;
   Typically used when receiving data from a WebSocket"
  [^bytes b-a]
  (let [{:keys [eventname data uuid]} (protobuf-load ChatEventProtoDef b-a)
        data-byte-array (util/byte-string-to-byte-array data)
        ;put data into a clojure map (avoid protobuf/map mixup)
        data-deserialized (into {} (event-name-dispatch eventname data-byte-array protobuf-load))]
    {:eventname eventname
     :data data-deserialized
     :uuid uuid}))

(defn clj-data->proto->byte-array
  "Creates a protobuf event given some Clojure data; converts to byte array before returning;
   Typically used before sending data onto a WebSocket"
  [{:keys [eventname data uuid] :as m}]
  ;transform clojure data to protobuf and then to byte-array
  (let [event (chat-event-proto
                {:eventname eventname
                 :data      (event-name-dispatch eventname data protobuf)
                 :uuid      uuid})]
    (println "proto event::" event)
    (protobuf-dump event)))