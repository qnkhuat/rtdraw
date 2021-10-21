(ns rtdraw.cljs.main
  (:require [reagent.dom :as rd]
            [rtdraw.cljs.components.canvas :refer [Canvas]]))

(defn Application []
  [:div [Canvas]]
  )

(defn init []
  (rd/render 
    [Application] 
    (js/document.getElementById "root")))
