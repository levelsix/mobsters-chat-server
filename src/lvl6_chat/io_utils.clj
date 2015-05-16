(ns lvl6-chat.io-utils
  (:require [lvl6-chat.constants :as constants]
            [clojure.core.async :refer [chan go >! <! <!! >!! go-loop put! thread alts! alts!! timeout pipeline pipeline-blocking pipeline-async]]
            [lvl6-chat.exception-log :as exception-log]
            [lvl6-chat.println-m :refer [println-m]])
  (:import (clojure.lang IFn)))


(defn blocking-io-loop
  "Blocking loop. Blocks a real thread until successfully completed.
   f must be a function that takes params plus a confirm channel to put the result on when done.
   If the result on the channel is an Exception, the exception will be logged. In that case, or in a case of timeout
   the operation is re-tried. Handles timeouts via alts!!"
  [^IFn f & params]
  (let [params (into [] params)]
    (loop [num-of-tries 0
           result nil]
      (println "blocking-io-loop, try number " num-of-tries)
      ;if we reached the max number of retried, throw an exception
      (if (= num-of-tries constants/max-io-retries)
        (throw result))
      (let [confirm-ch (chan 1)
            _ (thread (apply f (conj params confirm-ch)))
            [result _] (alts!! [confirm-ch (timeout constants/io-timeout)])]
        (println "got result::" result)
        (cond
          (and (not (instance? Exception result)) (not (nil? result)))
          ;write ok
          (do
            ;out of the loop
            result)
          (instance? Exception result)
          (do (>!! exception-log/incoming-exceptions result)
              ;wait with expontential backoff
              (<!! (timeout (* constants/io-timeout (inc num-of-tries))))
              (recur (inc num-of-tries) result))
          (= nil result)
          (let [timeout-exception (Exception. "Timeout exception")]
            (do (>!! exception-log/incoming-exceptions timeout-exception)
                ;wait with expontential backoff
                (<!! (timeout (* constants/io-timeout (inc num-of-tries))))
                (recur (inc num-of-tries) timeout-exception)))
          :else
          ;not ok, retry
          (do
            (println-m "going to retry blocking loop")
            ;wait with expontential backoff
            (<!! (timeout (* constants/io-timeout (inc num-of-tries))))
            (recur (inc num-of-tries) (Exception. "Unknown error in blocking-io-loop"))))))))

;(defn try-io-once
;  "Tries to execute an IO function exactly once.
;   Returns either the desired result, or some sort of an exception.
;   Suitable for read operations"
;  [^IFn f & params]
;  (let [confirm-ch (chan 1)
;        _ (apply f (conj params confirm-ch))
;        [result _] (alts!! [confirm-ch (timeout constants/io-timeout)])]
;    (println "got result::" result)
;    (cond
;      (and (not (instance? Exception result)) (not (nil? result)))
;      ;write ok
;      (do
;        result)
;      ;exception, log and return
;      (instance? Exception result)
;      (do (>!! exception-log/incoming-exceptions result)
;          result)
;      (= nil result)
;      (do
;        (let [timeout-exception (Exception. "Timeout exception")]
;          (>!! exception-log/incoming-exceptions timeout-exception)
;          timeout-exception))
;      :else
;      ;not ok, retry
;      (do
;        (let [unknown-exception (Exception. "Unknown exception")]
;          (>!! exception-log/incoming-exceptions unknown-exception)
;          unknown-exception)))))