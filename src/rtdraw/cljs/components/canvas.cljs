(ns rtdraw.cljs.components.canvas
  (:require [reagent.core :as r]
            [cljs.core.match :refer [match]]
            [cljs.core.async :as a :refer [put! >! <! go go-loop dropping-buffer chan]]
            ))

(defn canvas
  []
  (let [ch (chan (dropping-buffer 1024))
        conn (js/WebSocket.  "ws://localhost:3000/ws/")

        this (atom nil)
        drawing (r/atom false)

        handle-mouse-down 
        (fn [e] (put! ch {:type :mouse-down, :x (.-clientX e), :y (.-clientY e)}))

        handle-mouse-up 
        (fn [e] (put! ch {:type :mouse-up, :x (.-clientX e), :y (.-clientY e)}))

        handle-mouse-move 
        (fn [e] (put! ch {:type :mouse-move, :x (.-clientX e), :y (.-clientY e)}))

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
                 (js/console.error "????????????")))
        ]
    (r/create-class
      {
       :component-did-mount
       (fn []
         ; resize
         (set! (.. (.getContext @this "2d") -canvas -width) (.-innerWidth js/window))
         (set! (.. (.getContext @this "2d") -canvas -height) (.-innerHeight js/window))
         (set! (.-onmessage conn) (fn [msg] (js/console.log "got a message: " msg)))

         ; loop to draw
         (go-loop [msg (<! ch)]
                  (js/console.log "got a message")
                  (.send conn msg)
                  (handle-msg msg)
                  (recur (<! ch))
                  ))

       :reagent-render 
       (fn [] 
         [:canvas
          {:class "w-screen h-screen"
           :on-mouse-down handle-mouse-down
           :on-mouse-up handle-mouse-up
           :on-mouse-move handle-mouse-move
           :ref (fn [el] (reset! this el))
           }]
         )}
      )))
