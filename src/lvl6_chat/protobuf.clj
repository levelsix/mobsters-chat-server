(ns lvl6-chat.protobuf
  (:require [flatland.protobuf.core :as proto :refer [protobuf protodef]])
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
    com.lvl6.chatserver.ChatEvent$CreateUserRequestProto

    com.lvl6.chatserver.ChatEvent$SendMessageRequestProto
    com.lvl6.chatserver.ChatEvent$SendMessageResponseProto
    com.lvl6.chatserver.ChatEvent$CreateUserRequestProto))


(def ChatRoomProtoDef (protodef Chat$ChatRoomProto))


(protobuf ChatRoomProtoDef
          :uuid "asd"
          :tag "GLOBAL")

