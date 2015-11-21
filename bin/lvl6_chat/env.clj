(ns lvl6-chat.env
  (:require [environ.core :as environ]))


(defonce env environ/env)

;merges root binding of env with a-map
(defn merge-with-env! [a-map]
  (alter-var-root #'env (fn [x] (merge x a-map))))
