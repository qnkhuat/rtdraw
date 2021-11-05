(ns rtdraw.cljs.components.drawer
  (:require
    [reagent.core :as r]
    [rtdraw.cljs.env :refer [API_URL]]
    [lambdaisland.uri :refer [uri]]
    [rtdraw.cljs.components.canvas :refer [Canvas]]
    [rtdraw.cljs.components.toolbar :refer [Toolbar]]
    [rtdraw.cljs.components.mui :refer [Button]]
    ))

(defn Drawer
  []
  (let [state (r/atom {:color "black"
                       :stroke-size 5
                       :mode :rectangle})
        api-uri (uri API_URL)
        ws-url (str (assoc (uri "")
                           :scheme (if (#{"https"} (:scheme api-uri)) "wss" "ws") 
                           :host (:host api-uri)
                           :port (:port api-uri)
                           :path "/ws/"))]
    (fn [] 
      (js/console.log "new state I supposed: " (clj->js @state))
      [:div
       [Toolbar 
        :mode (:mode @state)
        :stroke-size (:stroke-size @state)
        :color (:color @state)
        :onChange (fn [k v] (js/console.log "handle: " k v)
                    (js/console.log "state: " (clj->js @state))
                    (swap! state assoc k v))]
       [Canvas 
        {
         :mode (:mode @state)
         :stroke-size (:stroke-size @state)
         :color (:color @state)
         :id "canvas"
         :ws-url ws-url}]
       ])))

