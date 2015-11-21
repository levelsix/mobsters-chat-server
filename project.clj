(defproject lvl6-chat "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [

                 [org.clojure/clojure "1.7.0"]
                 [com.raspasov/protobuf "0.8.2"]
                 [com.taoensso/faraday "1.6.0"]
                 [aleph "0.4.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 ;logging/profiling
                 [com.taoensso/timbre "3.4.0"]
                 ;env variables
                 [environ "1.0.0"]
                 ;zeromq
                 [org.zeromq/jzmq "3.1.0"]
                 [org.zeromq/cljzmq "0.1.4"]
                 [com.keminglabs/zmq-async "0.1.0"]

                 ;amazonica
                 [amazonica "0.3.22"]

                 [org.clojure/core.incubator "0.1.3"]

                 ;consistent hashing
                 [clojurewerkz/chash "1.1.0"]

                 ;rabbit mq client
                 [com.novemberain/langohr "3.1.0"]

                 ;hashing
                 [digest "1.4.4"]

                 ;better pprint
                 [aprint "0.1.3"]
                 
                 ]
  :javac-options ["-target" "1.6" "-source" "1.6"]


  :plugins [[lein-environ "1.0.0"]
            [lein-rpm "0.0.5"]
            [lein-protobuf "0.4.2"]]

  :repl-options {
                 :host "0.0.0.0"
                 }
  :main ^:skip-aot lvl6-chat.core
  :jvm-opts ^:replace ["-XX:+AggressiveOpts"
                       "-XX:+UseFastAccessorMethods"
                       "-XX:+UseG1GC"
                       ;"-Xms2g"
                       ;"-Xmx2g"
                       "-XX:+PerfDisableSharedMem"
                       ;enable remote debugger
                       ;"-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
                       ;YourKit
                       ;"-agentpath:/Users/raspasov/Downloads/YourKit_Java_Profiler_2015_build_15054.app/Contents/Resources/bin/mac/libyjpagent.jnilib"
                       ]
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
