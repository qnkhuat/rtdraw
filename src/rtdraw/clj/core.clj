(ns rtdraw.clj.core
  (:require [ring.adapter.jetty9 :refer [run-jetty send!]]
            [compojure.core :refer [defroutes]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.session :refer [wrap-session]]
            [compojure.route :as route])
  (:gen-class))

(defonce connections (atom #{}))

(defroutes routes
  (route/not-found "Where are you going?"))

(def ws-handler {:on-connect (fn [ws] 
                               (swap! connections conj ws))

                 :on-error (fn [ws _e] 
                             (swap! connections disj ws))

                 :on-close (fn [ws _status-code _reason] 
                             (swap! connections disj ws))

                 :on-text (fn [ws text-message] 
                            ; broadcast this message to everyone except itself
                            (doall (map #(send! % text-message) (filter #(not= ws %) @connections))))
                 })

(def websocket-routes {"/ws" ws-handler})

(def app
  (-> #'routes
      wrap-keyword-params
      wrap-params
      wrap-session))

(defn -main [& _args]
  (run-jetty app {:port 3000 :websockets websocket-routes}))
