(ns rtdraw.cljs.main
  (:require [reagent.dom :as rd]
            [cljs.core.async :as a :refer [put! >! <! go]]
            [haslett.client :as ws]
            [haslett.format :as fmt]
            [rtdraw.cljs.components.canvas :refer [canvas]]))

;(defn interactive
;  []
;  [:div 
;   [:input {:type "button", :value "connect"
;            :on-click (fn [_] (go (let 
;                                    (js/console.log "websocket " stream)
;                                    (>! (:sink stream) "ngockq ne")
;                                    )))}]
;   " "
;   [:input {:type "button", :value "send"}]
;   ])


(defn Application []
  [:div [canvas]
   ])

(defn init []
  (rd/render 
    [Application] 
    (js/document.getElementById "root")))
