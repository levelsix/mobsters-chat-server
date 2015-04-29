(ns lvl6-chat.util
  (:import (java.util UUID)
           (com.google.protobuf ByteString)))


(def byte-array-class (class (byte-array 0)))


(defn timestamp[]
  (quot (System/currentTimeMillis) 1000))

(defn timestamp-ms []
  (System/currentTimeMillis))



(defn random-uuid-str [] (str (UUID/randomUUID)))

(defn random-uuid [] (UUID/randomUUID))

(defn copy-to-byte-string ^ByteString [^bytes b]
  (ByteString/copyFrom b))

(defn byte-string-to-byte-array ^bytes [^ByteString bs]
  (.toByteArray bs))