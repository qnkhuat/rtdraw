(ns rtdraw.cljs.components.canvas
  (:require [reagent.core :as r]
            [cljs.core.match :refer [match]]
            [clojure.edn :as edn]
            [cljs.core.async :as a :refer [put! >! <! go go-loop dropping-buffer chan]]
            ))

(defn canvas
  []
  (let [ch (chan (dropping-buffer 1024))
        conn (js/WebSocket.  "ws://localhost:3000/ws/")

        this (atom nil)
        drawing (r/atom false)

        handle-mouse-down 
        (fn [e] (if ch 
                  (put! ch {:type :mouse-down, :x (.-clientX e), :y (.-clientY e)})
                  (js/console.log "Channel is closed")))

        handle-mouse-up 
        (fn [e] (if ch 
                  (put! ch {:type :mouse-up, :x (.-clientX e), :y (.-clientY e)})
                  (js/console.log "Channel is closed")))

        handle-mouse-move 
        (fn [e] (if ch 
                  (put! ch {:type :mouse-move, :x (.-clientX e), :y (.-clientY e)})
                  (js/console.log "Channel is closed")))

        handle-msg
        (fn [msg]
          (match [msg]
                 [{:type :mouse-move, :x x, :y y}]
                 (when (and @this @drawing) 
                   (let [ctx (.getContext @this "2d")]
                     (.lineTo ctx x y)
                     (.stroke ctx)
                     (.beginPath ctx)
                     (.moveTo ctx x y)))

                 [{:type :mouse-up, :x _, :y _}]
                 (do 
                   (reset! drawing false)
                   (when @this 
                     (.beginPath (.getContext @this "2d"))))

                 [{:type :mouse-down, :x x, :y y}]
                 (do 
                   (reset! drawing true)
                   (put! ch {:type :mouse-move, :x x, :y y}))

                 :else
                 (js/console.error "????????????"))
          )
        ]
    (r/create-class
      {
       :component-did-mount
       (fn []
         ; resize
         (set! (.. @this -width) (.-innerWidth js/window))
         (set! (.. @this -height) (.-innerHeight js/window))

         (set! (.-onmessage conn) (fn [msg] 
                                    (handle-msg (->> msg .-data edn/read-string))
                                    ))

         ; loop to draw
         (go-loop [msg (<! ch)]
                  (if (= 1 (.-readyState conn))
                    (do
                     (.send conn msg)
                     (handle-msg msg)
                     (recur (<! ch)))
                    (recur msg)
                    )
                  ))

       :reagent-render 
       (fn [] 
         [:canvas
          {:class ""
           :on-mouse-down handle-mouse-down
           :on-mouse-up handle-mouse-up
           :on-mouse-move handle-mouse-move
           :ref (fn [el] (reset! this el))
           }]
         )}
      )))
