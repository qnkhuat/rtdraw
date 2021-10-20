(ns rtdraw.cljs.components.canvas
  (:require [reagent.core :as r]
            [rtdraw.cljs.components.mui :refer [Button]]
            [cljs.core.match :refer [match]]
            [clojure.edn :as edn]
            [cljs.core.async :as a :refer [put! >! <! go go-loop dropping-buffer chan]]
            ))

(defn get-mouse-pos
  "Get position to draw using event and the element"
  [e el]
  (let [rect (.getBoundingClientRect el)]
    {:x (- (.-clientX e) (.-left rect))
     :y (- (.-clientY e) (.-top rect))}))

(defn canvas
  []
  (let [ch (chan (dropping-buffer 1024))
        conn (js/WebSocket.  "ws://localhost:3000/ws/")

        state (r/atom {:pen-type :pencil
                       :color "black"
                       :stroke-size 5
                       })

        this (atom nil)
        drawing (r/atom false)

        handle-mouse-down 
        (fn [e] 
          (put! ch (merge {:type :mouse-down} (get-mouse-pos e @this))))

        handle-mouse-up 
        (fn [e]  
          (put! ch (merge {:type :mouse-up} (get-mouse-pos e @this))))

        handle-mouse-move 
        (fn [e] 
          (put! ch (merge {:type :mouse-move} (get-mouse-pos e @this))))


        handle-msg
        (fn [msg]
          (match [msg]
                 [{:type :mouse-move, :x x, :y y}]
                 (when (and @this @drawing) 
                   (let [ctx (.getContext @this "2d")]
                     (.lineTo ctx x y)
                     (.stroke ctx)
                     (.beginPath ctx)
                     (.moveTo ctx x y)
                     (.closePath ctx)
                     ))

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
          )]
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
                  (when (= 1 (.-readyState conn))
                    (do
                      (.send conn msg)
                      (handle-msg msg)
                      )
                    (recur (<! ch)))
                  ))

       :reagent-render 
       (fn [] 
         [:div
          [Button "click me bitch"]
          [:canvas
           {:class "mt-24 ml-24 border-2 border-black"
            :on-mouse-down handle-mouse-down
            :on-mouse-up handle-mouse-up
            :on-mouse-move handle-mouse-move
            :ref (fn [el] (reset! this el))
            }]]
         )}
      )))
