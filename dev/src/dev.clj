(ns dev
  (:require [ring.middleware.reload :refer [wrap-reload]]
            [ring.adapter.jetty :refer [run-jetty]]
            [rtdraw.clj.core :refer [routes]]))

(def dev-handler
  (wrap-reload #'routes))

(defn start! []
  (run-jetty dev-handler {:port 3000}))

(comment
  (require 'dev)
  (dev/start!))
