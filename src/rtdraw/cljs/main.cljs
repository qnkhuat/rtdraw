(ns rtdraw.cljs.main
  (:require [reagent.dom :as rd]
            [rtdraw.cljs.env :refer [API_URL]]
            [lambdaisland.uri :refer [uri]]
            [rtdraw.cljs.components.drawer :refer [Drawer]]))

(defn Application []
  [:div [Drawer]])

(defn init []
  (rd/render 
    [Application] 
    (js/document.getElementById "root")))
