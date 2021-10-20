(ns rtdraw.cljs.components.mui
  (:require [reagent.core :as r]
            ["@mui/material/Button" :as MuiButton]
            ))

(defn -adapt
  [component]
  (r/adapt-react-class (.-default component)))

(def Button (-adapt MuiButton))
