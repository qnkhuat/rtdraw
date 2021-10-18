(ns rtdraw.cljs.main
  (:require [reagent.core :as r]
            [reagent.dom :as rd]))

(defn init []
  (println "Hello World!!!!!!!")
  (println "yay")
  )

(def current-count (r/atom 0))

(defn Application []
  [:h2 {:class "text-red-400 mt-6 text-center text-3xl font-extrabold"} "Sign in to your account"]
  )

(rd/render [Application] (js/document.getElementById "root"))
