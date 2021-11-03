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
                 :width 50
                 :height 50
                 :stroke (:color opts)
                 }}

      :rectangle
      {:mode mode
       :options {
                 :id (s-uuid)
                 :left pointer-x
                 :top pointer-y
                 :width 50
                 :height 50
                 :stroke (:color opts)
                 :radius 25 ; this is for circle only
                 }}

      :line 
      {:mode mode
       :coords [pointer-x pointer-y
                (+ pointer-x 50) pointer-y ]
       :options {
                 :id (s-uuid)
                 :stroke (:color opts)
                 :left pointer-x
                 :top pointer-y
                 :strokeWidth 5
                 }}))))

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
                       :color "black"
                       :stroke-size 5
                       :mode :pen
                       :down-pointer {:x nil, :y nil}
                       :copied-object nil ; store object to duplicate
                       :current-mouse-pointer nil
                       })

        this (atom nil) ; store canvas ref

        canvas (atom nil) ; fabric canvas instance

        handle-mouse-up
        (fn [_e] )

        handle-mouse-move
        (fn [e]
          (swap! state assoc :current-mouse-pointer (get-mouse-pointer e)))

                      

        handle-mouse-down
        (fn [e] 
          (when (not (#{:selecting :pen} (:mode @state) ))
            ; create a new object with fixed size under the cursor
            ; only works for shapes
            ; TODO : move this logic to a function
            (let [pointer (get (js->clj e) "pointer")
                  payload (make-object (:mode @state) pointer @state)
                  msg {:type :action-object-add 
                       :payload payload}]
              (put! ch msg))))

        
        handle-object-modified
        (fn [e]
          (js/console.log "object: modified: " (clj->js e)))

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
          (let [selected-pen (keyword (.. e -target -value))]
            (if (= selected-pen :pen)
              (set! (.-isDrawingMode @canvas) true)
              (set! (.-isDrawingMode @canvas) false))
            (swap! state assoc :mode selected-pen)))

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
          (println "draw msg: " (clj->js msg))
          (match msg
                 {:type :action-object-add, :payload {:mode mode, :options options}}
                 (when @canvas
                   (when-let [object (case mode
                                       :rectangle (fabric/fabric.Rect. (clj->js options))
                                       :circle (fabric/fabric.Circle. (clj->js options))
                                       :line (fabric/fabric.Line. (->> msg :payload :coords clj->js) 
                                                                  (clj->js options)))]
                     (.add @canvas object)
                     (swap! state assoc :mode :selecting)))


                 ; TODO: this will not work for remote drawer
                 {:type :action-object-add-with-object, :payload {:object object}}
                 (do 
                   (.add @canvas (js->clj object))
                   (swap! state assoc :mode :selecting))

                 {:type :action-object-remove :payload {:id id}}
                 (doall (map #(.remove @canvas %) 
                             (filter #(= id (.-id %)) (.getObjects @canvas))))

                 {:type :action-object-modify :payload {:id id, :options options}}
                 (do
                   (doall (map #(.set % (clj->js options)) 
                               (filter #(= id (.-id %)) (.getObjects @canvas))))
                   (.renderAll @canvas))

                 ;(doall (map #(fabric/fabric.util.qrDecompose mt)))

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
            (swap! state assoc :selecting true))

        handle-selection-updated
        (fn [e]
            (set! (.-target e) "hasRotatingPoint" false)
            (swap! state assoc :selecting true))

        handle-selection-cleared
        (fn [_e]
          (swap! state assoc :selecting false))

        testing-handler
        (fn [e]
          (js/console.log "s-uuid: "))

        ]
        (r/create-class
          {
          :component-did-mount
          (fn []
            (let [width (.-offsetWidth @this)
                  height (.-offsetHeight @this)]
              (reset! canvas (fabric/fabric.Canvas. "canvas" (clj->js {:width width :height height})))
              (set! (.-isDrawingMode @canvas) true)
              (.addEventListener js/document "keydown" handle-key-down)

              (.on @canvas "mouse:up" handle-mouse-up)
              (.on @canvas "mouse:down" handle-mouse-down)
              (.on @canvas "mouse:move" handle-mouse-move)
              (.on @canvas "object:created" #(js/console.log "object:created: " (clj->js %)))
              (.on @canvas "object:moving" handle-object-modify); TODO :figure out how rotate group?
              (.on @canvas "object:scaling" handle-object-modify)
              (.on @canvas "object:rotating" handle-object-modify) 
              (.on @canvas "object:skewing" handle-object-modify)
              (.on @canvas "object:modified" handle-object-modified)
              (.on @canvas "selection:created" handle-selection-created)
              (.on @canvas "selection:updated" handle-selection-updated)

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
                [MenuItem {:value :selecting} "Selecting"]
                [MenuItem {:value :rectangle} "Rect"]
                [MenuItem {:value :line} "Line"]
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
                :id "canvas"
                :ref (fn [el] (reset! this el))
                }]]
            )}
          )))
