(ns lvl6-chat.core
  (:require [lvl6-chat.aleph-netty :as aleph-netty]
            [lvl6-chat.protobuf]
            [lvl6-chat.dynamo-db]
            [taoensso.timbre :as timbre]
            [lvl6-chat.env :as env]
            [lvl6-chat.exception-log :as exception-log])
  (:gen-class))


;logging config
(defn configure-timbre-logging []
  (timbre/set-level! :info)
  (timbre/set-config! [:appenders :spit :enabled?] true)
  (timbre/set-config! [:shared-appender-config :spit-filename] (get-in env/env [:config :error-log])))
;end logging config




(defn -main
  "LVL6 Chat"
  [& args]
  (env/merge-with-env! (read-string (slurp "resources/etc/lvl6chat/config-original.clj")))
  (configure-timbre-logging)
  (exception-log/start-exception-logger)
  (aleph-netty/start-server))

(-main)

