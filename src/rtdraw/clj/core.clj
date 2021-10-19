(ns rtdraw.clj.core
  (:require [ring.adapter.jetty9 :refer [run-jetty]]
            [compojure.core :refer [defroutes GET POST]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.jetty9 :refer [get-sch-adapter]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.session :refer [wrap-session]]
            [compojure.route :as route])
  (:gen-class))



(let [{:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket! (get-sch-adapter) {})]

  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv) ; ChannelSocket's receive channel
  (def chsk-send!                    send-fn) ; ChannelSocket's send API fn
  (def connected-uids                connected-uids) ; Watchable, read-only atom
  )


(defroutes routes
  (GET "/foo" [] "Hello Foo")
  (GET "/bar" [] "Hello Bar")
  (GET  "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post                req))
  (route/resources "/")
  (route/not-found "Where are you going?"))

(def ws-handler {:on-connect (fn [ws] (println "connect"))
                 :on-error (fn [ws e] (println "error: " e))
                 :on-close (fn [ws status-code reason] (println "close: " reason))
                 :on-text (fn [ws text-message] (println "text: " text-message))
                 :on-bytes (fn [ws bytes offset len] (println "bytes: " len))
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
