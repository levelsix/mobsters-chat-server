(ns lvl6-chat.consistent-hashing
  (:require [clojurewerkz.chash.ring :as ch]))

(def ring (atom nil))

(defn new-ring []
  (reset! ring
          (ch/fresh 3 "node-1")))

(defn update-partition [idx new-node-name]
  (reset! ring
          (ch/update @ring idx new-node-name)))


(defn get-node-for-val [value]
  (ch/successors @ring (ch/key-of value) 3))