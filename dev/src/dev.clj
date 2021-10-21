(ns dev
  (:require [ring.middleware.reload :refer [wrap-reload]]
            [ring.adapter.jetty9 :refer [run-jetty]]
            [rtdraw.clj.core :refer [app websocket-routes]]))

(println "Welcome to rtdraw dev")

(defonce ^:private instance* (atom nil))

(defn instance []
  @instance*)

(def dev-handler
  (wrap-reload #'app))

(defn start! []
  (reset! instance* (run-jetty dev-handler 
                               {:port 3000 :join? false 
                                :websockets websocket-routes})))

(defn stop! []
  (when (instance)
    (.stop (instance))))

(comment
  (require 'dev)
  (dev/start!)
  (dev/stop!)
  (use '[clojure.tools.namespace.repl :only (refresh)])
  (refresh)
  )
