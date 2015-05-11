(ns lvl6-chat.protobuf
  (:require [flatland.protobuf.core :as proto :refer [protobuf protobuf-dump protobuf-load protodef]]
            [lvl6-chat.util :as util]))


;ProtoDefs
(def ChatRoomProtoDef (protodef com.lvl6.chatserver.Chat$ChatRoomProto))
;event type protodef
(def ChatEventProtoDef (protodef com.lvl6.chatserver.ChatEvent$ChatEventProto))

;create user
(def CreateUserRequestProtoDef (protodef com.lvl6.chatserver.ChatEvent$CreateUserRequestProto))
(def CreateUserResponseProtoDef (protodef com.lvl6.chatserver.ChatEvent$CreateUserResponseProto))
;create chat room
(def CreateChatRoomRequestProtoDef (protodef com.lvl6.chatserver.ChatEvent$CreateChatRoomRequestProto))
(def CreateChatRoomResponseProtoDef (protodef com.lvl6.chatserver.ChatEvent$CreateChatRoomResponseProto))
;add user to chat room
(def AddUserToChatRoomRequestProtoDef (protodef com.lvl6.chatserver.ChatEvent$AddUserToChatRoomRequestProto))
(def AddUserToChatRoomResponseProtoDef (protodef com.lvl6.chatserver.ChatEvent$AddUserToChatRoomResponseProto))
;remove user from chat room
(def RemoveUserFromChatRoomRequestProtoDef (protodef com.lvl6.chatserver.ChatEvent$RemoveUserFromChatRoomRequestProto))
(def RemoveUserFromChatRoomResponseProtoDef (protodef com.lvl6.chatserver.ChatEvent$RemoveUserFromChatRoomResponseProto))
;send message
(def SendMessageRequestProtoDef (protodef com.lvl6.chatserver.ChatEvent$SendMessageRequestProto))
(def SendMessageResponseProtoDef (protodef com.lvl6.chatserver.ChatEvent$SendMessageResponseProto))
;receive message
(def ReceiveMessageProtoDef (protodef com.lvl6.chatserver.ChatEvent$ReceiveMessageProto))
;retrieve room messages
(def RetrieveRoomMessagesRequestProtoDef (protodef com.lvl6.chatserver.ChatEvent$RetrieveRoomMessagesRequestProto))
(def RetrieveRoomMessagesResponseProtoDef (protodef com.lvl6.chatserver.ChatEvent$RetrieveRoomMessagesResponseProto))
;set typing status
(def SetTypingStatusRequestProtoDef (protodef com.lvl6.chatserver.ChatEvent$SetTypingStatusRequestProto))
(def SetTypingStatusResponseProtoDef (protodef com.lvl6.chatserver.ChatEvent$SetTypingStatusResponseProto))
;receive typing status
(def ReceiveTypingStatusProtoDef (protodef com.lvl6.chatserver.ChatEvent$ReceiveTypingStatusProto))
;send read confirmation
(def SendReadConfirmationRequestProtoDef (protodef com.lvl6.chatserver.ChatEvent$SendReadConfirmationRequestProto))
(def SendReadConfirmationResponseProtoDef (protodef com.lvl6.chatserver.ChatEvent$SendReadConfirmationResponseProto))
;receive read confirmation
(def ReceiveReadConfirmationProtoDef (protodef com.lvl6.chatserver.ChatEvent$ReceiveReadConfirmationProto))
;login
(def LoginRequestProtoDef (protodef com.lvl6.chatserver.ChatEvent$LoginRequestProto))
(def LoginResponseProtoDef (protodef com.lvl6.chatserver.ChatEvent$LoginResponseProto))
;logout
(def LogoutRequestProtoDef (protodef com.lvl6.chatserver.ChatEvent$LogoutRequestProto))
(def LogoutResponseProtoDef (protodef com.lvl6.chatserver.ChatEvent$LogoutResponseProto))
;receive online status
(def ReceiveOnlineStatusProtoDef (protodef com.lvl6.chatserver.ChatEvent$ReceiveOnlineStatusProto))
;set user details
(def SetUserDetailsRequestProtoDef (protodef com.lvl6.chatserver.ChatEvent$SetUserDetailsRequestProto))
(def SetUserDetailsResponseProtoDef (protodef com.lvl6.chatserver.ChatEvent$SetUserDetailsResponseProto))
;set room translation settings
(def SetRoomTranslationSettingsRequestProtoDef (protodef com.lvl6.chatserver.ChatEvent$SetRoomTranslationSettingsRequestProto))
(def SetRoomTranslationSettingsResponseProtoDef (protodef com.lvl6.chatserver.ChatEvent$SetRoomTranslationSettingsResponseProto))
;receive room notification
(def ReceiveRoomNotificationProtoDef (protodef com.lvl6.chatserver.ChatEvent$ReceiveRoomNotificationProto))




(defn event-name-dispatch [eventname data protobuf-fn]
  (condp = eventname
    ;create user
    :create-user-request (protobuf-fn CreateUserRequestProtoDef data)
    :create-user-response (protobuf-fn CreateUserResponseProtoDef data)
    ;create room
    :create-chat-room-request (protobuf-fn CreateChatRoomRequestProtoDef data)
    :create-chat-room-response (protobuf-fn CreateChatRoomResponseProtoDef data)
    ;add user to chat room
    :add-user-to-chat-room-request (protobuf-fn AddUserToChatRoomRequestProtoDef data)
    :add-user-to-chat-room-response (protobuf-fn AddUserToChatRoomResponseProtoDef data)
    ;remove user from chat room
    :remove-user-from-chat-room-request (protobuf-fn RemoveUserFromChatRoomRequestProtoDef data)
    :remove-user-from-chat-room-response (protobuf-fn RemoveUserFromChatRoomResponseProtoDef data)
    ;send message
    :send-message-request (protobuf-fn SendMessageRequestProtoDef data)
    :send-message-response (protobuf-fn SendMessageResponseProtoDef data)
    ;receive message
    :receive-message (protobuf-fn ReceiveMessageProtoDef data)
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
        data-deserialized (event-name-dispatch eventname data-byte-array protobuf-load)
        ;eliminate flatland map representations from returned data
        pure-clj-data (clojure.walk/prewalk (fn [x]
                                                   (if (instance? flatland.protobuf.PersistentProtocolBufferMap x)
                                                     (into {} x)
                                                     x)) data-deserialized)]
    {:eventname eventname
     :data pure-clj-data
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


