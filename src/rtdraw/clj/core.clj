(ns rtdraw.clj.core
  (:require [ring.adapter.jetty9 :refer [run-jetty send!]]
            [compojure.core :refer [defroutes GET POST]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.session :refer [wrap-session]]
            [compojure.route :as route])
  (:gen-class))



(defroutes routes
  (route/resources "/")
  (route/not-found "Where are you going?"))

(def ws-handler {:on-connect (fn [_] (println "connect"))
                 :on-error (fn [_ e] (println "error: " e))
                 :on-close (fn [_ _ reason] (println "close: " reason))
                 :on-text (fn [ws text-message] 
                            (send! ws "yo")
                            )
                 })

(def websocket-routes {"/ws" ws-handler})

(def app
  (-> #'routes
      wrap-keyword-params
      wrap-params
      wrap-session
      ))

(defn -main [& args]
  (run-jetty app {:port 3000 :websockets websocket-routes}))
