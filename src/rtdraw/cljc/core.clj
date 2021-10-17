(ns rtdraw.cljc.core
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [compojure.core :refer [defroutes GET]]
            [ring.middleware.reload :refer [wrap-reload]]
            [compojure.route :as route]
            [rtdraw.cljc.server :as server])
  (:gen-class))

(defroutes routes
  (GET "/foo" [] "Hello Foo")
  (GET "/bar" [] "Hello Bar")
  (route/not-found "Where are you going??"))

(defn -main [& args]
  (server/start-web-server! routes {:port 3000}))
