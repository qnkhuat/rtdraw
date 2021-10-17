(ns dev
  (:require [ring.middleware.reload :refer [wrap-reload]]
            [rtdraw.cljc.server :as server]
            [rtdraw.cljc.core :refer [routes]]))

(def dev-handler
  (wrap-reload #'routes))

(defn start! []
  (server/start-web-server! dev-handler {:port 3000}))

(comment
  (require 'dev)
  (dev/start!))
