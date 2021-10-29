(ns build
  (:require [clojure.tools.build.api :as b]
            [clojure.pprint :refer [pprint]]))

(def class-dir "target/classes")
(def src-dir "src/rtdraw/clj/")
(def basis (b/create-basis {:project "deps.edn"}))
(def uber-file "target/rtdraw.jar")

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber [_]
  (clean nil)

  (b/copy-dir {:src-dirs [src-dir "resources"]
               :target-dir class-dir})

  (b/compile-clj {:basis basis
                  :src-dirs [src-dir]
                  :class-dir class-dir})

  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis basis
           :main 'rtdraw.clj.core}))
