(ns scicloj.scittles.v1.resource
  (:require [scicloj.tempfiles.api :as tempfiles]))

(def cached-resource
  (memoize
   (fn [url]
     (let [path (-> ".cache"
                    tempfiles/tempfile!
                    :path)]
       (->> url
            slurp
            (spit path))
       path))))

(defn get-resource [url]
  (-> url
      cached-resource
      slurp))
