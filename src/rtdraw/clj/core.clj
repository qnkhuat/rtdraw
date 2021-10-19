(ns rtdraw.clj.core
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [compojure.core :refer [defroutes GET]]
            [compojure.route :as route])
  (:gen-class))

(defroutes routes
  (GET "/foo" [] "Hello Foo")
  (GET "/bar" [] "Hello Bar")
  (route/not-found "Where are you going??????"))

(defn -main [& args]
  (run-jetty routes {:port 3000}))
