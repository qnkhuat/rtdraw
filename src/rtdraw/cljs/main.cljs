(ns rtdraw.cljs.main
  (:require [reagent.dom :as rd]
            [rtdraw.cljs.components.canvas :refer [canvas]]
            ))


(defn Application []
  [:div [canvas]])

(defn init []
  (rd/render 
    [Application] 
    (js/document.getElementById "root")))
