(ns lvl6-chat.println-m)


(defn println-enabled? [] true)

(defmacro println-m [& params]
  `(if (println-enabled?)
     (println ~@params)))