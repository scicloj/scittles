(ns scicloj.scittles.v1.server
  (:require [org.httpkit.server :as httpkit]
            [cognitect.transit :as transit]
            [clojure.java.browse :as browse]
            [scicloj.scittles.v1.page :as page]
            [scicloj.scittles.v1.widget :as widget]
            [scicloj.scittles.v1.table :as table]))

(def default-port 1971)

(defonce *clients (atom #{}))

(defn broadcast! [msg]
  (doseq [ch @*clients]
    (httpkit/send! ch msg)))

(def *state
  (atom {:port default-port
         :widgets []
         :fns {}}))

(defn routes [{:keys [:body :request-method :uri]
               :as req}]
  (if (:websocket? req)
    (httpkit/as-channel req {:on-open (fn [ch] (swap! *clients conj ch))
                             :on-close (fn [ch _reason] (swap! *clients disj ch))
                             :on-receive (fn [_ch msg])})
    (case [request-method uri]
      [:get "/"] {:body (page/page @*state)
                  :status 200}
      [:post "/compute"] (let [{:keys [fname args]} (-> body
                                                        (transit/reader :json)
                                                        transit/read
                                                        read-string)
                               f (-> @*state :fns (get fname))]
                           {:body (pr-str (when (and f args)
                                            (apply f args)))
                            :status 200}))))

(defonce *stop-server! (atom nil))

(defn core-http-server []
  (httpkit/run-server #'routes {:port (:port @*state)}))

(defn open! []
  (let [url (str "http://localhost:" (:port @*state) "/")]
    (reset! *stop-server! (core-http-server))
    (println "serving scittle at " url)
    (browse/browse-url url)))

(defn close! []
  (when-let [s @*stop-server!]
    (s))
  (reset! *stop-server! nil))

(defn write-html! [path]
  (->> @*state
       page/page
       (spit path))
  [:ok])

(defn show-widgets!
  ([widgets]
   (show-widgets! widgets nil))
  ([widgets options]
   (swap! *state
          (fn [state]
            (-> state
                (assoc :widgets widgets)
                (merge options))))
   (broadcast! "refresh")
   [:ok]))

(comment
  (close!)
  (open!)

  (show-widgets!

   [[:h1 "hi"]

    (widget/code "(+ 1 2)")

    (widget/clojure "(+ 1 2)")

    (widget/naive {:x 9})

    '[cytoscape
      {:elements {:nodes [{:data {:id "a" :parent "b"} :position {:x 215 :y 85}}
                          {:data {:id "b"}}
                          {:data {:id "c" :parent "b"} :position {:x 300 :y 85}}
                          {:data {:id "d"} :position {:x 215 :y 175}}
                          {:data {:id "e"}}
                          {:data {:id "f" :parent "e"} :position {:x 300 :y 175}}]
                  :edges [{:data {:id "ad" :source "a" :target "d"}}
                          {:data {:id "eb" :source "e" :target "b"}}]}
       :style [{:selector "node"
                :css {:content "data(id)"
                      :text-valign "center"
                      :text-halign "center"}}
               {:selector "parent"
                :css {:text-valign "top"
                      :text-halign "center"}}
               {:selector "edge"
                :css {:curve-style "bezier"
                      :target-arrow-shape "triangle"}}]
       :layout {:name "preset"
                :padding 5}}]

    ['vega
     {:data {:values
             (->> (repeatedly 99 #(- (rand) 0.5))
                  (reductions +)
                  (map-indexed (fn [x y]
                                 {:w (rand-int 9)
                                  :z (rand-int 9)
                                  :x x
                                  :y y}))
                  vec)},
      :mark "point"
      :encoding
      {:size {:field "w" :type "quantitative"}
       :x {:field "x", :type "quantitative"},
       :y {:field "y", :type "quantitative"},
       :fill {:field "z", :type "nominal"}}}]

    '[echarts
      {:xAxis {:data ["Mon" "Tue" "Wed" "Thu" "Fri" "Sat" "Sun"]}
       :yAxis {}
       :series [{:type "bar"
                 :color ["#7F5F3F"]
                 :data [23 24 18 25 27 28 25]}]}]
    ['datatables
     (-> {:column-names [:x :y]
          :row-vectors [[1 2]
                        [3 4]
                        [5 6]]}
         table/->table-hiccup)]]))
