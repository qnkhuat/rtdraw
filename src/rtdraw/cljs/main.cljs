(ns rtdraw.cljs.main
  (:require [reagent.dom :as rd]
            [cljs.core.async :as a :refer [put! >! <! go]]
            [rtdraw.cljs.components.canvas :refer [canvas]]))

(defn Application []
  [:div [canvas]])

(defn init []
  (rd/render 
    [Application] 
    (js/document.getElementById "root")))
