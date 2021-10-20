(ns rtdraw.cljs.components.canvas
  (:require [reagent.core :as r]
            [rtdraw.cljs.components.mui :refer [Button FormControl MenuItem Select InputLabel Slider]]
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


(defn get-ctx
  "Get context from canvas ref"
  [ref]
  (when @ref
    (.getContext @ref "2d")))

(defn is-drawing
  [state]
  (:drawing @state))

(defn canvas
  []
  (let [ch (chan (dropping-buffer 1024))
        conn (js/WebSocket.  "ws://localhost:3000/ws/")

        state (r/atom {:pen-type :pen
                       :color "black"
                       :stroke-size 5
                       :drawing false ; keeping track of whether or not is drawing mode
                       })

        this (atom nil) 

        handle-mouse-down 
        (fn [e] 
          (put! ch (merge {:type :mouse-down} (get-mouse-pos e @this) @state )))

        handle-mouse-up 
        (fn [e]  
          (put! ch (merge {:type :mouse-up} (get-mouse-pos e @this) @state)))

        handle-mouse-move 
        (fn [e] 
          (put! ch (merge {:type :mouse-move} (get-mouse-pos e @this) @state)))

        handle-change-pen
        (fn [e]
          (swap! state assoc :pen-type (keyword (.. e -target -value))))

        handle-change-stroke-size
        (fn [e]
          (swap! state assoc :stroke-size (.. e -target -value))
          )

        handle-change-color
        (fn [e]
          (swap! state assoc :color (.. e -target -value)))

        handle-clear
        (fn [e]
          (put! ch {:type :action-clear}))


        handle-draw
        (fn [msg]
          (match [msg]
                 [{:type :mouse-move, :x x, :y y, }]
                 (when (and @this (is-drawing state)) 
                   (let [ctx (get-ctx this)]
                     (set! (.. ctx -lineWidth) (:stroke-size msg))
                     (set! (.. ctx -strokeStyle) (:color msg))
                     (set! (.. ctx -lineJoin) "round")
                     (set! (.. ctx -lineCap) "round")
                     (.lineTo ctx x y)
                     (.stroke ctx)
                     (.moveTo ctx x y)
                     ))

                 [{:type :mouse-up, :x _, :y _}]
                 (do 
                   (swap! state assoc :drawing false)
                   (when @this 
                     (.beginPath (.getContext @this "2d"))))

                 [{:type :mouse-down, :x x, :y y}]
                 (do 
                   (swap! state assoc :drawing true)
                   (put! ch {:type :mouse-move, :x x, :y y}))


                 [{:type :action-clear}]
                 (let [ctx (get-ctx this)]
                   (.clearRect ctx 0 0 (.-width ctx) (.-height ctx)))

                 :else
                 (js/console.error "????????????"))

          )
        ]
    (r/create-class
      {
       :component-did-mount
       (fn []
         ; resize
         ; TODO : recheck this logic. do we need to set all this?
         (set! (.. (get-ctx this) -width) (.-offsetWidth @this))
         (set! (.. (get-ctx this) -height) (.-offsetHeight @this))
         (set! (.. @this -width) (.-offsetWidth @this))
         (set! (.. @this -height) (.-offsetHeight @this))

         (set! (.-onmessage conn) (fn [msg] 
                                    (handle-draw (->> msg .-data edn/read-string))
                                    ))

         ; loop to draw
         (go-loop [msg (<! ch)]
                  (handle-draw msg)
                  (when (= 1 (.-readyState conn))
                    (.send conn msg))
                  (recur (<! ch))
                  ))

       :reagent-render 
       (fn [] 
         [:div {:class "mt-24 mx-12"}
          [Button {:onClick handle-clear } "Clear"]
          [FormControl
           [InputLabel {:id "select-pen"} "Pen"]
           [Select {:labelId "select-pen"
                    :id "select"
                    :value (:pen-type @state)
                    :onChange handle-change-pen
                    }
            [MenuItem {:value :rectangle} "Rect"]
            [MenuItem {:value :circle} "Circle"]
            [MenuItem {:value :pen} "Pen"]
            ]
           ]

          [FormControl
           [InputLabel {:id "select-color"} "color"]
           [Select {:labelId "select-color"
                    :id "select"
                    :value (:color @state)
                    :onChange handle-change-color
                    }
            [MenuItem {:value "black"} "Black"]
            [MenuItem {:value "red"} "Red"]
            [MenuItem {:value "blue"} "Blue"]
            ]
           ]
          [Slider {:aria-label "Stroke size" 
                   :min 1
                   :max 20
                   :value (:stroke-size @state)
                   :onChange handle-change-stroke-size}]
          [:canvas
           {:class "mt-24 ml-24 border-2 border-black w-screen h-screen"
            :on-mouse-down handle-mouse-down
            :on-mouse-up handle-mouse-up
            :on-mouse-move handle-mouse-move
            :ref (fn [el] (reset! this el))
            }]]
         )}
      )))
