(ns lvl6-chat.util
  (:require [clojure.core.async :refer [chan go >! <! <!! >!! go-loop put! thread alts! alts!! timeout pipeline pipeline-blocking pipeline-async]])
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


(defmacro chan-and-print
  "Helper macro for the repl
  Takes chan-name, and optional buff-n and xf, creates a chan and starts printing out of it"
  [chan-name & more]
  (let [[buff-n xf] more]
    `(do (def ~chan-name (chan (if ~buff-n ~buff-n 1) ~xf))
         (go (while true (println (str '~chan-name) "::" (<! ~chan-name)))))))

