; https://exceptionnotfound.net/drawing-with-fabricjs-and-typescript-part-2-straight-lines/
(ns rtdraw.cljs.components.canvas
  (:require [reagent.core :as r]
            [rtdraw.cljs.components.mui :refer [Button FormControl MenuItem Select InputLabel Slider]]
            [rtdraw.cljs.env :refer [API_URL]]
            [cljs.core.match :refer [match]]
            [clojure.edn :as edn]
            [lambdaisland.uri :refer [uri]]
            [cljs.core.async :as a :refer [put! >! <! go go-loop dropping-buffer chan]]
            [clojure.string :as s]
            ["fabric" :as fabric]))

(defn s-uuid []
  (str (random-uuid)))


(defn calc-dimension 
  [points]
  (let [xs (map #(or (:x %) (get % "x")) points)
        ys (map #(or (:y %) (get % "y")) points)
        minX (apply min xs)
        maxX (apply max xs)
        minY (apply min ys)
        maxY (apply max ys)]
    {:left minX
     :top minY
     :width (- maxX minX)
     :height (- maxY minY)}))
  
(defn get-abs-left
  "Return the absolute left of an object whether it's alone or it's in a group"
  [o]
  (if (.-group o)
    (+ (.-left o) (.. o -group -left) (/ (.. o -group -width ) 2))
    (.-left o)))

(defn get-abs-top
  "Return the absolute left of an object whether it's alone or it's in a group"
  [o]
  (if (.-group o)
    (+ (.-top o) (.. o -group -top) (/ (.. o -group -height ) 2)) 
    (.-top o)))

(defn get-abs-scaleX
  "Return the absolute scale of an object whether it's alone or it's in a group"
  [o]
  (if (.-group o)
    (* (.-scaleX o) (.. o -group -scaleX))
    (.-scaleX o)))


(defn get-abs-scaleY
  "Return the absolute scale of an object whether it's alone or it's in a group"
  [o]
  (if (.-group o)
    (* (.-scaleY o) (.. o -group -scaleY))
    (.-scaleY o)))

(defn get-mouse-pos
  "Get position to draw using event and the element"
  [e el]
  (let [rect (.getBoundingClientRect el)]
    {:x (- (.-clientX e) (.-left rect))
     :y (- (.-clientY e) (.-top rect))}))

(defn get-mouse-pointer
  [e]
  (let [pointer (get (js->clj e) "pointer")]
    {:x (get pointer "x")
     :y (get pointer "y")}))

(defn get-object-by-id
  [objects id]
  (first (filter #(= id (.-id %)) objects)))

(defn make-object
  ([mode pointer] make-object mode pointer {:color "black"})

  ([mode pointer opts]
  (let [pointer-x (get pointer "x")
        pointer-y (get pointer "y")]
    (case mode
      :circle
      {:mode mode
       :options {
                 :id (s-uuid)
                 :left pointer-x
                 :top pointer-y
                 :stroke (:color opts)
                 :radius 20
                 }}

      :rectangle
      {:mode mode
       :options {
                 :id (s-uuid)
                 :left pointer-x
                 :top pointer-y
                 :width 20
                 :height 20
                 :stroke (:color opts)
                 }}

      :line 
      {:mode mode
       :coords [pointer-x pointer-y
                (+ pointer-x 20) pointer-y ]
       :options {
                 :id (s-uuid)
                 :stroke (:color opts)
                 :left pointer-x
                 :top pointer-y
                 :strokeWidth 5
                 }}

      :polyline
      {:mode mode
       :points [{:x pointer-x :y pointer-y}]
       :options {
                 :id (s-uuid)
                 :stroke (:color opts)
                 :objectCaching false
                 :fill "transparent"
                 :dirty true
                 }
       }
      ))))

(defn Canvas
  []
  (let [ch (chan (dropping-buffer 1024))
        ws-url (str (assoc (uri "")
                           :scheme (if (= "https" (:scheme (uri API_URL))) "wss" "ws") 
                           :host (:host (uri API_URL))
                           :port (:port (uri API_URL))
                           :path "/ws/"))
        conn (js/WebSocket.  ws-url)

        state (r/atom {
                       :mouse :up ; mouse state, up or down?
                       :color "black"
                       :stroke-size 5
                       :mode :polyline
                       :down-pointer {:x nil, :y nil}
                       :copied-object nil ; store object to duplicate
                       :current-mouse-pointer nil
                       :creating-object nil
                       })

        this (atom nil) ; store canvas ref

        canvas (atom nil) ; fabric canvas instance

        handle-mouse-up
        (fn [_e] (swap! state merge {:mouse :up, :creating-object nil}))

        handle-mouse-move
        (fn [e]
          ; save current mouse pointer as state
          (swap! state assoc :current-mouse-pointer (get-mouse-pointer e))

          ; TODO: move this to a function
          (when (and (#{:down} (:mouse @state)) (:creating-object @state))
            (let 
              [id (:creating-object @state)
               object (get-object-by-id (.getObjects @canvas) id)
               object-type (.get object "type")
               pointer (get-mouse-pointer e)
               x (:x pointer)
               y (:y pointer)]
              (put! ch
                    {:type  :action-object-modify
                     :payload {:id id
                               :options 
                               (case (keyword object-type)

                                 :line {:x2 x
                                        :y2 y}

                                 ; the points conj might be come expensive as drawing more and more, 
                                 ; should send the new point only
                                 :polyline (let [_ (.push (.-points object) {"x" x "y" y})
                                                 points (js->clj (.-points object))
                                                 dim (calc-dimension points)]
                                             {:points points 
                                              :left (:left dim)
                                              :top (:top dim)
                                              :width (:width dim)
                                              :height (:height dim)
                                              :pathOffset {:x (+ (:left dim) (/ (:width dim) 2)) 
                                                           :y (+ (:top dim) (/ (:height dim) 2))}})
                                             
                                 :rect {:originX (if (< (.-left object) x) "left" "right")
                                        :originY (if (< (.-top object) y) "top" "bottom")
                                        :width (Math/abs (- (.-left object) x))
                                        :height (Math/abs (- (.-top object) y))}
                                        

                                 :circle {:originX (if (< (.-left object) x) "left" "right")
                                          :originY (if (< (.-top object) y) "top" "bottom")
                                          :radius (min (/ (Math/abs (- (.-left object) x)) 2)
                                                       (/ (Math/abs (- (.-top object) y)) 2))}
                                 )}})
                            
            )
          
          ))

        handle-mouse-down
        (fn [e] 
          (swap! state assoc :mouse :down)
          ; create a new object with fixed size under the cursor
          ; TODO : move this logic to a function
          (when (and (= 0 (count (.getActiveObjects @canvas)))
                     (not= :select (-> @state :mode keyword)))
            (let [pointer (get (js->clj e) "pointer")
                  payload (make-object (:mode @state) pointer @state)
                  msg {:type :action-object-add 
                       :payload payload}]
              (put! ch msg)
              ; this will be reset in mouse-up
              (swap! state assoc :creating-object (->> payload :options :id)))))
              

        
        handle-object-modified
        (fn [e]
          (js/console.log "object:modified: " (clj->js e)))

        handle-object-added
        (fn [e]
          (js/console.log "object:added (" (.. e -target -type) "): " (clj->js e)))

        handle-object-modify
        (fn [_e]
          (let [active-objects (.getActiveObjects @canvas)]
            (doall (for [object active-objects]
                     ; this call back doesn't need to draw bc it's already drawed
                     ; so send directly to the remote
                     ;(let [real-object (first (filter #(= id (.-id object)) (.getObjects @canvas)))]
                     (.send conn {:type :action-object-modify, 
                                  :payload {:id (.-id object), 
                                            :options {
                                                      ; TODO: group scaling is still not working right, the top, left of object also need to be scaled
                                                      :left (get-abs-left object)
                                                      :top (get-abs-top object)
                                                      :scaleX (get-abs-scaleX object)
                                                      :scaleY (get-abs-scaleY object)
                                                      :width (.-width object)
                                                      :angle (.-angle object)
                                                      :height (.-height object)
                                                      }}})))))

        handle-change-pen
        (fn [e]
          (swap! state assoc :mode (keyword (.. e -target -value))))

        handle-change-stroke-size
        (fn [e]
          (swap! state assoc :stroke-size (.. e -target -value)))

        handle-change-color
        (fn [e]
          (swap! state assoc :color (.. e -target -value)))

        handle-clear
        (fn [_e]
          (put! ch {:type :action-clear}))

        handle-draw
        (fn 
          ;;; Main handler that will directly interact with the canvas, 
          ;;; All of manipulation to the canvas should go through this function.

          ;;; Msg from remote will go through this handler as well, so this function should carefully
          ;;; access the states so that the render will be in-sync
          [msg]
          ;(js/console.log "draw msg: " (clj->js msg))
          (match msg
                 {:type :action-object-add, :payload {:mode mode, :options options}}
                 (when @canvas
                   (when-let [object (case mode
                                       :rectangle (fabric/fabric.Rect. (clj->js options))
                                       :circle (fabric/fabric.Circle. (clj->js options))
                                       :polyline (fabric/fabric.Polyline. (->> msg :payload :points clj->js) 
                                                                          (clj->js options))
                                       :line (fabric/fabric.Line. (->> msg :payload :coords clj->js) 
                                                                  (clj->js options)))]
                     (.add @canvas object)

                     ; auto switch batch to select mode after create an object
                     (swap! state assoc :mode :select)
                     ))

                 ; TODO: this will not work for remote drawer
                 {:type :action-object-add-with-object, :payload {:object object}}
                 (do 
                   (.add @canvas (js->clj object))
                   (swap! state assoc :mode :select))

                 {:type :action-object-remove :payload {:id id}}
                 (doall (map #(.remove @canvas %) 
                             (filter #(= id (.-id %)) (.getObjects @canvas))))

                 {:type :action-object-modify :payload {:id id, :options options}}
                 (do
                   (doall (map #(do (.set % (clj->js options)) (.setCoords %)) 
                               (filter #(= id (.-id %)) (.getObjects @canvas))))
                   (.renderAll @canvas))

                 {:type :action-clear}
                 (.clear @canvas)

                 :else
                 (js/console.error "Unrecognized msg: " (clj->js msg))))

        handle-key-down
        (fn [e]
          (cond
            ; Ctrl/Cmd C
            (and (= (.-keyCode e) 67 ) (or (.-ctrlKey e) (.-metaKey e)))
            (when-let [active-object (.getActiveObject @canvas)]
              (.clone active-object #(swap! state assoc :copied-object %)))

            ; Ctrl/Cmd V
            (and (= (.-keyCode e) 86 ) (or (.-ctrlKey e) (.-metaKey e)))
            (when-let [cloned-object (:copied-object @state)]
              ; create new object under the cursor
              (.set cloned-object "id" (s-uuid))
              (.set cloned-object "left" (-> @state :current-mouse-pointer :x))
              (.set cloned-object "top" (-> @state :current-mouse-pointer :y))
              (put! ch {:type :action-object-add-with-object :payload {:object (clj->js cloned-object)}}))

            ; Delete
            (= (.-keyCode e) 8)
            (doall (map #(put! ch {:type :action-object-remove :payload {:id (.-id %)}}) 
                        (.getActiveObjects @canvas)))))

        handle-selection-created
        (fn [e]
          (set! (.-target e) "hasRotatingPoint" false)
          (swap! state assoc :activeObjects (.getActiveObjects @canvas)))

        handle-selection-updated
        (fn [e]
          (set! (.-target e) "hasRotatingPoint" false)
          (swap! state assoc :activeObjects (.getActiveObjects @canvas)))

        handle-selection-cleared
        (fn [_e]
          (swap! state assoc :activeObjects nil))

        testing-handler
        (fn [_e]
          (js/console.log "s-uuid: "))

        ]
        (r/create-class
          {
          :component-did-mount
          (fn []
            (let [width (.-offsetWidth @this)
                  height (.-offsetHeight @this)]
              (reset! canvas (fabric/fabric.Canvas. "canvas" (clj->js {:width width :height height})))
              (set! (.-isDrawingMode @canvas) false)
              (.addEventListener js/document "keydown" handle-key-down)

              (.on @canvas "mouse:up" handle-mouse-up)
              (.on @canvas "mouse:down" handle-mouse-down)
              (.on @canvas "mouse:move" handle-mouse-move)
              (.on @canvas "object:added" handle-object-added)
              (.on @canvas "object:moving" handle-object-modify); TODO :figure out how rotate group?
              (.on @canvas "object:scaling" handle-object-modify)
              (.on @canvas "object:rotating" handle-object-modify) 
              (.on @canvas "object:skewing" handle-object-modify)
              (.on @canvas "object:modified" handle-object-modified)
              (.on @canvas "selection:created" handle-selection-created)
              (.on @canvas "selection:updated" handle-selection-updated)
              (.on @canvas "selection:cleared" handle-selection-cleared)

              (set! (.-onmessage conn) (fn [msg] 
                                          (js/console.log "received a message: " msg)
                                          (handle-draw (->> msg .-data edn/read-string))))

              (set! (.-onopen conn) (fn [_event] 
                                 (js/console.log "Connected to : " ws-url)))


              ; loop to draw
              (go-loop [msg (<! ch)]
                        (handle-draw msg)
                        (when (= 1 (.-readyState conn))
                          (.send conn msg))
                        (recur (<! ch)))))

          :reagent-render 
          (fn [] 
            [:div {:class "mt-24 mx-12"}
              [Button {:onClick handle-clear} "Clear"]
              [Button {:onClick testing-handler} "Test"]
              [FormControl
              [InputLabel {:id "select-pen"} "Pen"]
              [Select {:labelId "select-pen"
                        :id "select"
                        :value (:mode @state)
                        :onChange handle-change-pen
                        }
                [MenuItem {:value :select} "Select"]
                [MenuItem {:value :rectangle} "Rect"]
                [MenuItem {:value :line} "Line"]
                [MenuItem {:value :circle} "Circle"]
                [MenuItem {:value :polyline} "Pen"]
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
                :id "canvas"
                :ref (fn [el] (reset! this el))
                }]]
            )}
          )))
