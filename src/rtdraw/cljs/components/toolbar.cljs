(ns rtdraw.cljs.components.toolbar
  (:require
    [reagent.core :as r]
    [rtdraw.cljs.components.canvas :refer [Canvas]]
    [rtdraw.cljs.components.mui :refer [Button FormControl MenuItem Select InputLabel Slider]]
    ))
  

(defn Toolbar
  [& {:keys [mode onChange stroke-size color] :as args}]
  []
  (let [handle-change-mode (fn [e] (onChange :mode (keyword (.. e -target -value))))]
    (fn 
      [& {:keys [mode onChange stroke-size color] :as args}]
      (js/console.log "render" (clj->js args))
      [:div 
       {:class "m-8"}
       [FormControl
        [InputLabel {:id "select-pen"} "Pen"]
        [Select {:labelId "select-pen"
                 :id "select"
                 :value mode
                 :onChange handle-change-mode
                 }
         [MenuItem {:value :select} "Select"]
         [MenuItem {:value :rectangle} "Rect"]
         [MenuItem {:value :line} "Line"]
         [MenuItem {:value :circle} "Circle"]
         [MenuItem {:value :polyline} "Pen"]
         ]
        ]

       ])))
    
