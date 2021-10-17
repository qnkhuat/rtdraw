(ns rtdraw.cljc.server
  (:require [ring.adapter.jetty :as ring-jetty]
            [ring.util.servlet :as servlet])
  (:import javax.servlet.AsyncContext
           [javax.servlet AsyncContext DispatcherType AsyncEvent AsyncListener]
           [javax.servlet.http HttpServletRequest HttpServletResponse]
           [org.eclipse.jetty.server Request Server]
           org.eclipse.jetty.server.handler.AbstractHandler))

(defonce ^:private instance*
  (atom nil))

(defn instance []
  "Deref the instance*"
  @instance*)

; From ring/adapter/jetty.clj
(defn- async-jetty-raise [^AsyncContext context ^HttpServletResponse response]
  (fn [^Throwable exception]
    (.sendError response 500 (.getMessage exception))
    (.complete context)))

(defn- async-jetty-respond 
  [context response]
  (fn [response-map]
    (servlet/update-servlet-response response context response-map)))

(defn- async-timeout-listener 
  [request context response handler]
  (proxy [AsyncListener] []
    (onTimeout [^AsyncEvent _]
      (handler (servlet/build-request-map request)
               (async-jetty-respond context response)
               (async-jetty-raise context response)))
    (onComplete [^AsyncEvent _])
    (onError [^AsyncEvent _])
    (onStartAsync [^AsyncEvent _])))

(defn- ^AbstractHandler async-proxy-handler 
  [handler timeout timeout-handler]
  (proxy [AbstractHandler] []
    (handle [_ ^Request base-request ^HttpServletRequest request ^HttpServletResponse response]
      (let [^AsyncContext context (.startAsync request)]
        (.setTimeout context timeout)
        (when timeout-handler
          (.addListener
            context
            (async-timeout-listener request context response timeout-handler)))
        (handler
          (servlet/build-request-map request)
          (async-jetty-respond context response)
          (async-jetty-raise context response))
        (.setHandled base-request true)))))

; from metabase/server
(defn create-server
  "Create a new async Jetty server with `handler` and `options`. Handy for creating the real web server, and
  creating one-off web servers for tests and REPL usage."
  ^Server [handler options]
  ;; if any API endpoint functions aren't at the very least returning a channel to fetch the results later after 10
  ;; minutes we're in serious trouble. (Almost everything 'slow' should be returning a channel before then, but
  ;; some things like CSV downloads don't currently return channels at this time)
  ;;
  (let [timeout (* 10 60 1000)]
    (doto ^Server (#'ring-jetty/create-server (assoc options :async? true))
      (.setHandler (async-proxy-handler handler timeout nil)))))

(defn start-web-server!
  "Start the embedded Jetty web server. Returns `:started` if a new server was started; `nil` if there was already a
  running server.
  "
  ([handler config]
   (when-not (instance)
     ;; NOTE: we always start jetty w/ join=false so we can start the server first then do init in the background
     (let [new-server (create-server handler config)]
       ;; Only start the server if the newly created server becomes the official new server
       ;; Don't JOIN yet -- we're doing other init in the background; we can join later
       (when (compare-and-set! instance* nil new-server)
         (.start new-server)
         :started)))))

(defn stop-web-server!
  "Stop the embedded Jetty web server. Returns `:stopped` if a server was stopped, `nil` if there was nothing to stop."
  []
  (let [[^Server old-server] (reset-vals! instance* nil)]
    (when old-server
      (println "Shutting Down Embedded Jetty Webserver")
      (.stop old-server)
      :stopped)))

