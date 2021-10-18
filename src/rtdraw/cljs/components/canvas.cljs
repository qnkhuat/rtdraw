(ns rtdraw.cljs.components.canvas
  (:require [reagent.core :as r]
            [reagent.dom :as rd]
            [goog.events :as events]
            [cljs.core.async :as async
             :refer [>! <! chan close! go go-loop dropping-buffer]]))

(defn canvas
  []
  (let [ch (chan (dropping-buffer 1024))
        drawing (r/atom false)
        handle-mousedown (fn [_]
                           (reset! drawing true))
        handle-mouseup (fn [_]
                           (reset! drawing false))
        handle-draw (fn [_]
                      (js/console.log (str "drawing baby:" @drawing)))]
    
  [:canvas
   {:class "w-screen h-screen"
    :on-mouse-down handle-mousedown
    :on-mouse-up handle-mouseup
    :on-mouse-move handle-draw
    }]
  ))
