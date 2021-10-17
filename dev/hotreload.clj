(ns hotreload
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.reload :refer [wrap-reload]]
            [cljc.rtdraw.core :refer [handler]])
  (:gen-class))

(def dev-handler
  (wrap-reload #'handler))

(defn -main [& args]
  (run-jetty dev-handler {:port 3000}))
