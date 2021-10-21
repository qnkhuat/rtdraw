(ns rtdraw.cljs.components.canvas
  (:require [reagent.core :as r]
            [rtdraw.cljs.components.mui :refer [Button FormControl MenuItem Select InputLabel Slider]]
            [cljs.core.match :refer [match]]
            [clojure.edn :as edn]
            [cljs.core.async :as a :refer [put! >! <! go go-loop dropping-buffer chan]]
            ["fabric" :as fabric]
            ))

(defn s-uuid []
  (str (random-uuid)))

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

(defn Canvas
  []
  (let [ch (chan (dropping-buffer 1024))
        conn (js/WebSocket.  "ws://localhost:3000/ws/")

        state (r/atom {
                       :color "black"
                       :stroke-size 5
                       :mode :rectangle
                       :down-pointer {:x nil, :y nil}
                       :copied-object nil ; store object to duplicate
                       :current-mouse-pointer nil
                       })

        this (atom nil) ; store canvas ref

        canvas (atom nil) ; fabric canvas instance

        handle-mouse-up
        (fn [_e]
          (swap! state assoc :mode :selecting))


        handle-mouse-move
        (fn [e]
          (swap! state assoc :current-mouse-pointer (get-mouse-pointer e)))

        handle-mouse-down
        (fn [e] 
          (when (not= (:mode @state) :selecting)
            (let [pointer (get (js->clj e) "pointer")
                  payload {:mode (:mode @state)
                           :options {
                                     :left (get pointer "x")
                                     :top (get pointer "y")
                                     :width 50
                                     :height 50
                                     :stroke (:color @state)
                                     :stroke-size (:stroke-size @state)
                                     :id (s-uuid)
                                     :radius 25
                                     }
                           }
                  msg (merge {:type :action-object-add :payload payload})]
              (swap! state assoc :last-created-payload payload)
              (put! ch msg)
              )))

        handle-object-moving
        (fn [e]
          (js/console.log "object: moving:" (clj->js e)))

        handle-object-modified
        (fn [e]
          (js/console.log "object: modified: " (clj->js e)))

        handle-object-modify
        (fn [e]
          (let [active-object (.getActiveObject @canvas)]
            ; this call back doesn't need to draw bc it's already drawed
            ; so send directly to the remote
            (.send conn {:type :action-object-modify, :payload {:id (.-id (.getActiveObject @canvas)), 
                                                                :options {
                                                                          :left (.-left active-object)
                                                                          :top (.-top active-object)
                                                                          :width (.-width active-object)
                                                                          :height (.-height active-object)
                                                                          :zoomX (.-zoomX active-object)
                                                                          :zoomY (.-zoomY active-object)
                                                                          :scaleX (.-scaleX active-object)
                                                                          :scaleY (.-scaleY active-object)
                                                                          }}}))
          )

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
        (fn [msg]
          ; This shouldn't access state
          (println "draw msg: " msg)
          (println "state: " @state)
          (match msg
                 {:type :action-object-add, :payload {:mode mode, :options options}}
                 (when @canvas
                   (when-let [object (case mode
                                       :rectangle (fabric/fabric.Rect. (clj->js options))
                                       :circle (fabric/fabric.Circle. (clj->js options))
                                       nil)]
                     (.add @canvas object)
                     ))

                 ; TODO: this will not work for remote drawer
                 {:type :action-object-with-object, :payload {:object object}}
                 (.add @canvas (js->clj object))

                 {:type :action-object-remove :payload {:id id}}
                 (doall (map #(.remove @canvas %) 
                             (filter #(= id (.-id %)) (.getObjects @canvas))))

                 {:type :action-object-modify :payload {:id id, :options options}}
                 (do
                   ;(doall (map #(.set % (clj->js new-options)) (filter #(= id (.-id %)) (.getObjects @canvas)))))
                   ;(.set (.getActiveObject @canvas) (clj->js {:angle 45})))
                   (doall (map #(.set % (clj->js options)) 
                               (filter #(= id (.-id %)) (.getObjects @canvas))))
                   (.renderAll @canvas)
                   )
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
              ; update the copied object, or else the next paste will replace the last one
              (.clone cloned-object #(swap! state assoc :copied-object %))
              ; create new object under the cursor
              (.set cloned-object "id" (s-uuid))
              (.set cloned-object "left" (-> @state :current-mouse-pointer :x))
              (.set cloned-object "top" (-> @state :current-mouse-pointer :y))
              (put! ch {:type :action-object-with-object :payload {:object (clj->js cloned-object)}})
              )

            ; Delete
            (= (.-keyCode e) 8)
            (doall (map #(put! ch {:type :action-object-remove :payload {:id (.-id %)}}) 
                        (.getActiveObjects @canvas)))
            ))

        handle-selection-created
        (fn [_e]
          (let [active-object (.item @canvas 0)]
            (swap! state assoc :selecting true)))

        handle-selection-cleared
        (fn [_e]
          (println "selection:created")
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
              (set! (.-isDrawingMode @canvas) false)
              (.addEventListener js/document "keydown" handle-key-down)

              (.on @canvas "mouse:up" handle-mouse-up)
              (.on @canvas "mouse:down" handle-mouse-down)
              (.on @canvas "mouse:move" handle-mouse-move)
              (.on @canvas "object:created" #(js/console.log "object:created: " (clj->js %)))
              (.on @canvas "object:moving" handle-object-modify)
              (.on @canvas "object:scaling" handle-object-modify)
              (.on @canvas "object:rotating" handle-object-modify)
              (.on @canvas "object:skewing" handle-object-modify)
              (.on @canvas "object:modified" handle-object-modified)
              (.on @canvas "selection:created" #(js/console.log "selection:created: " (clj->js %)))

              (set! (.-onmessage conn) (fn [msg] 
                                          (js/console.log "received a message: " msg)
                                          (handle-draw (->> msg .-data edn/read-string))
                                          ))

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
