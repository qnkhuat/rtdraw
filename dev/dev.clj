(ns dev
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.reload :refer [wrap-reload]]
            [rtdraw.cljc.server :as server]
            [rtdraw.cljc.core :refer [routes]])
  (:gen-class))

(def dev-handler
  (wrap-reload #'routes))

(defn start! []
  (server/start-web-server! dev-handler {:port 3000}))

(defn -main [& args]
  (start!))

(start!)
