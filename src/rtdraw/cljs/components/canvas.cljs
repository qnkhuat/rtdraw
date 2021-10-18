(ns rtdraw.cljs.components.canvas
  (:require [reagent.core :as r]
            [reagent.dom :as rd]
            [goog.events :as events]
            [cljs.core.match :refer [match]]
            [cljs.core.async :as async
             :refer [<! >! put! chan close! go go-loop dropping-buffer]]))

;(defn canvas
;  []
;  [:canvas {
;            :on-click (fn [e]
;                        (let [el (.getElementById js/document "canvas")
;                              ctx (.getContext el "2d")]
;                          (js/console.log "canvas" el)
;                          (js/console.log "context" ctx)
;                          ;(set! (.-fillStyle ctx) "red")
;                          (.fillRect ctx 0 0 100 100)
;                          )
;                        )
;            :id "canvas" :class "w-screen h-screen"}]
;  )

(defn canvas
  []
  (let [ch (chan (dropping-buffer 1024))
        this (atom nil)
        drawing (r/atom false)

        handle-mouse-down 
        (fn [e] (put! ch {:type :mouse-down, :x (.-clientX e), :y (.-clientY e)}))

        handle-mouse-up 
        (fn [e] (put! ch {:type :mouse-up, :x (.-clientX e), :y (.-clientY e)}))

        handle-mouse-move 
        (fn [e] (put! ch {:type :mouse-move, :x (.-clientX e), :y (.-clientY e)}))

        ]
    (r/create-class
      {
       :component-did-mount
       (fn []
         (set! (.. (.getContext @this "2d") -canvas -width) (.-innerWidth js/window))
         (set! (.. (.getContext @this "2d") -canvas -height) (.-innerHeight js/window))
         (go-loop [msg (<! ch)]
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
                             (.beginPath (.getContext @this "2d"))
                             )
                           )

                         [{:type :mouse-down, :x x, :y y}]
                         (do 
                           (reset! drawing true)
                           (>! ch {:type :mouse-move, :x x, :y y})
                           )

                         :else
                         (js/console.log "what????????????"))
                  (recur (<! ch))
                  ))

       :reagent-render 
       (fn [] 
         [:canvas
          {:class "w-screen h-screen"
           :on-mouse-down handle-mouse-down
           :on-mouse-up handle-mouse-up
           :on-mouse-move handle-mouse-move
           ;:on-click handle-on-click 
           :ref (fn [el] (reset! this el))
           }]
         )}
      )))
