(defproject lvl6-chat "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0-beta2"]
                 [org.flatland/protobuf "0.8.1"]
                 [com.taoensso/faraday "1.6.0"]]
  :javac-options ["-target" "1.6" "-source" "1.6"]

  :plugins [[lein-environ "1.0.0"]
            [lein-rpm "0.0.5"]

            [lein-protobuf "0.4.2-LOCAL"]]

  :main ^:skip-aot lvl6-chat.core
  :jvm-opts ^:replace ["-XX:+AggressiveOpts"
                       "-XX:+UseFastAccessorMethods"
                       "-XX:+UseG1GC"
                       "-Xms1g"
                       "-Xmx1g"
                       "-XX:+PerfDisableSharedMem"
                       ;enable remote debugger
                       ;"-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
                       ;YourKit
                       "-agentpath:/Users/raspasov/Downloads/YourKit_Java_Profiler_2015_EAP_build_15040.app/Contents/Resources/bin/mac/libyjpagent.jnilib"
                       ]
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
