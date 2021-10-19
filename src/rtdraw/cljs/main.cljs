(ns rtdraw.cljs.main
  (:require [reagent.dom :as rd]
            [cljs.core.async :as a :refer [put! >! <! go]]
            [haslett.client :as ws]
            [haslett.format :as fmt]
            [rtdraw.cljs.components.canvas :refer [canvas]]))

(defn Application []
  [:div [canvas]])

(defn init []
  (rd/render 
    [Application] 
    (js/document.getElementById "root")))
